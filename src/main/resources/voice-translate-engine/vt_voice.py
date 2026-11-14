"""Voice Translate voice engine.

Speaks translated text in the voice of the person who said it, with
Chatterbox Multilingual. A separate process with its own Python environment:
Chatterbox needs one exact version of PyTorch, whose CUDA libraries clash with
the ones the speech engine uses.

Endpoints:
  GET  /status  what the engine is doing
  POST /speak   JSON {"text", "language", "speaker", "reference"}; reference is
                base64 16 kHz mono PCM16 of the speaker, sent once per new
                version of the speaker id. Answers 48 kHz mono PCM16.
"""

import argparse
import base64
import collections
import json
import os
import sys
import tempfile
import threading
import time
import traceback
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

OUTPUT_RATE = 48000
REFERENCE_RATE = 16000
MAX_BODY_BYTES = 8 * 1024 * 1024
MAX_TEXT_CHARACTERS = 600
MAX_CACHED_VOICES = 24
MODEL_MEGABYTES = 3200


class VoiceEngine:
    def __init__(self, models_dir):
        self.models_dir = models_dir
        self.lock = threading.Lock()
        self.state_lock = threading.Lock()
        self.model = None
        self.default_conditionals = None
        self.voices = collections.OrderedDict()
        self.state = "idle"
        self.device = ""
        self.error = ""
        self.downloading = False

    def status(self):
        with self.state_lock:
            result = {"state": self.state, "device": self.device, "error": self.error, "progress": -1}
        if result["state"] == "downloading":
            done = folder_megabytes(self.models_dir)
            result["progress"] = max(0, min(99, int(done * 100 / MODEL_MEGABYTES)))
        return result

    def set_state(self, state, error=""):
        with self.state_lock:
            self.state = state
            self.error = error

    def load_async(self, device):
        with self.state_lock:
            if self.state in ("loading", "downloading", "ready"):
                return
            self.state = "loading"
        threading.Thread(target=self._load, args=(device,), name="voice-loader", daemon=True).start()

    def _load(self, requested):
        try:
            import torch
            from chatterbox.mtl_tts import ChatterboxMultilingualTTS

            device = "cuda" if requested in ("auto", "cuda") and torch.cuda.is_available() else "cpu"
            if not self._is_downloaded():
                self.set_state("downloading")
            model = ChatterboxMultilingualTTS.from_pretrained(device=device)
            self.set_state("loading")
            with self.lock:
                self.model = model
                self.default_conditionals = model.conds
                # The first generation compiles kernels; do it before a player waits for it.
                model.generate("Hello.", language_id="en")
            with self.state_lock:
                self.device = device
                self.state = "ready"
                self.error = ""
            log("voice model ready on %s" % device)
        except Exception as exception:
            traceback.print_exc()
            self.set_state("error", str(exception))

    def _is_downloaded(self):
        return folder_megabytes(self.models_dir) > MODEL_MEGABYTES * 0.9

    def speak(self, text, language, speaker, reference):
        import torch
        import torchaudio.functional as audio_functional

        text = text.strip()[:MAX_TEXT_CHARACTERS]
        with self.lock:
            if self.model is None:
                raise NotReady()
            conditionals = self._conditionals_for(speaker, reference)
            self.model.conds = conditionals
            # Guidance lower than usual: the reference is often in another
            # language, and full guidance carries its accent into this one.
            wav = self.model.generate(text, language_id=language, cfg_weight=0.3, exaggeration=0.5)
            sample_rate = self.model.sr
        wav = wav.detach().float().cpu()
        if wav.dim() > 1:
            wav = wav.mean(dim=0)
        wav = audio_functional.resample(wav, sample_rate, OUTPUT_RATE)
        peak = float(wav.abs().max()) if wav.numel() > 0 else 0.0
        if peak > 0.99:
            wav = wav * (0.99 / peak)
        return (wav.clamp(-1.0, 1.0) * 32767.0).to(torch.int16).numpy().tobytes()

    def _conditionals_for(self, speaker, reference):
        """A speaker's voice, prepared once from their reference and kept by id."""
        if speaker and speaker in self.voices:
            self.voices.move_to_end(speaker)
            return self.voices[speaker]
        if not speaker or not reference:
            return self.default_conditionals
        path = write_wav(reference, REFERENCE_RATE)
        try:
            self.model.prepare_conditionals(path, exaggeration=0.5)
        finally:
            os.remove(path)
        conditionals = self.model.conds
        self.voices[speaker] = conditionals
        while len(self.voices) > MAX_CACHED_VOICES:
            self.voices.popitem(last=False)
        return conditionals


class NotReady(Exception):
    pass


def write_wav(pcm, sample_rate):
    handle, path = tempfile.mkstemp(suffix=".wav")
    os.close(handle)
    with wave.open(path, "wb") as target:
        target.setnchannels(1)
        target.setsampwidth(2)
        target.setframerate(sample_rate)
        target.writeframes(pcm)
    return path


def folder_megabytes(path):
    total = 0
    for root, _, files in os.walk(path):
        for name in files:
            try:
                total += os.path.getsize(os.path.join(root, name))
            except OSError:
                pass
    return total // (1024 * 1024)


def log(message):
    print(time.strftime("%H:%M:%S"), message, flush=True)


def make_handler(engine):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt, *args):
            pass

        def do_GET(self):
            if urlparse(self.path).path == "/status":
                self.reply_json(200, engine.status())
            else:
                self.reply_json(404, {"error": "not found"})

        def do_POST(self):
            length = int(self.headers.get("Content-Length", "0"))
            if length < 0 or length > MAX_BODY_BYTES:
                self.reply_json(413, {"error": "body too large"})
                return
            body = self.rfile.read(length)
            if urlparse(self.path).path != "/speak":
                self.reply_json(404, {"error": "not found"})
                return
            try:
                request = json.loads(body or b"{}")
                reference = request.get("reference") or ""
                pcm = engine.speak(str(request.get("text", "")), str(request.get("language", "en")),
                                   str(request.get("speaker", "")),
                                   base64.b64decode(reference) if reference else b"")
                self.reply_bytes(200, pcm)
            except NotReady:
                self.reply_json(503, engine.status())
            except ValueError as exception:
                self.reply_json(400, {"error": str(exception)})
            except Exception as exception:
                traceback.print_exc()
                self.reply_json(500, {"error": str(exception)})

        def reply_json(self, code, payload):
            self.reply(code, json.dumps(payload, ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8")

        def reply_bytes(self, code, data):
            self.reply(code, data, "application/octet-stream")

        def reply(self, code, data, content_type):
            self.send_response(code)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

    return Handler


def watch_parent(pid):
    if pid <= 0:
        return

    def alive():
        if os.name == "nt":
            import ctypes
            kernel32 = ctypes.windll.kernel32
            handle = kernel32.OpenProcess(0x1000, False, pid)
            if not handle:
                return False
            code = ctypes.c_ulong()
            kernel32.GetExitCodeProcess(handle, ctypes.byref(code))
            kernel32.CloseHandle(handle)
            return code.value == 259
        try:
            os.kill(pid, 0)
            return True
        except OSError:
            return False

    def loop():
        while True:
            time.sleep(2)
            if not alive():
                log("the game is gone, exiting")
                os._exit(0)

    threading.Thread(target=loop, name="parent-watch", daemon=True).start()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--models", required=True)
    parser.add_argument("--parent-pid", type=int, default=0)
    parser.add_argument("--device", default="auto")
    arguments = parser.parse_args()

    os.makedirs(arguments.models, exist_ok=True)
    os.environ["HF_HOME"] = arguments.models
    os.environ["HF_HUB_DISABLE_XET"] = "1"
    os.environ["HF_HUB_DISABLE_TELEMETRY"] = "1"
    os.environ["HF_HUB_DISABLE_PROGRESS_BARS"] = "1"
    os.environ["HF_HUB_DISABLE_IMPLICIT_TOKEN"] = "1"
    watch_parent(arguments.parent_pid)

    engine = VoiceEngine(arguments.models)
    engine.load_async(arguments.device)
    server = ThreadingHTTPServer(("127.0.0.1", arguments.port), make_handler(engine))
    server.daemon_threads = True
    log("listening on 127.0.0.1:%d" % arguments.port)
    server.serve_forever()


if __name__ == "__main__":
    main()
