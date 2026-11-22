"""Voice Translate voice copying engine.

Turns speech a Piper voice said into the voice of the player whose words it
is, with OpenVoice V2's tone colour converter, and marks the result with
WavMark, the inaudible watermark OpenVoice itself puts on the voices it
copies, so a copied voice can always be told apart from a real recording.

A separate process with its own Python environment: it needs PyTorch, which
the light speech engine does without. Runs on the processor, faster than
real time.

Endpoints:
  GET  /status   what the engine is doing
  POST /convert  JSON {"audio", "rate", "speaker", "reference"}: audio is
                 base64 mono PCM16 at rate, reference base64 16 kHz mono
                 PCM16 of the speaker, prepared once per speaker id.
                 Answers 48 kHz mono PCM16.
"""

import argparse
import base64
import collections
import json
import os
import sys
import threading
import time
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

OUTPUT_RATE = 48000
REFERENCE_RATE = 16000
MAX_BODY_BYTES = 8 * 1024 * 1024
MAX_CACHED_VOICES = 32
CONVERTER_REPO = "myshell-ai/OpenVoiceV2"
CONVERTER_FILES = ("converter/config.json", "converter/checkpoint.pth")
WATERMARK_REPO = "M4869/WavMark"
WATERMARK_FILE = "step59000_snr39.99_pesq4.35_BERP_none0.30_mean1.81_std1.81.model.pkl"
DOWNLOAD_MEGABYTES = 140
# OpenVoice marks its copies with this message; so do we.
WATERMARK_MESSAGE = "default"
WATERMARK_RATE = 16000
WATERMARK_CHUNK = 16000
TAU = 0.3


class VoiceEngine:
    def __init__(self, models_dir):
        self.models_dir = models_dir
        self.lock = threading.Lock()
        self.state_lock = threading.Lock()
        self.converter = None
        self.config = None
        self.watermarker = None
        self.voices = collections.OrderedDict()
        self.state = "idle"
        self.error = ""

    def status(self):
        with self.state_lock:
            result = {"state": self.state, "device": "cpu", "error": self.error, "progress": -1}
        if result["state"] == "downloading":
            done = folder_megabytes(self.models_dir)
            result["progress"] = max(0, min(99, int(done * 100 / DOWNLOAD_MEGABYTES)))
        return result

    def set_state(self, state, error=""):
        with self.state_lock:
            self.state = state
            self.error = error

    def load_async(self):
        with self.state_lock:
            if self.state in ("loading", "downloading", "ready"):
                return
            self.state = "loading"
        threading.Thread(target=self._load, name="voice-loader", daemon=True).start()

    def _load(self):
        try:
            import torch
            from huggingface_hub import hf_hub_download
            from openvoice.models import SynthesizerTrn
            import wavmark.models.my_model as wavmark_model

            torch.set_num_threads(cpu_threads())
            paths = self._download(hf_hub_download)
            with open(paths[CONVERTER_FILES[0]], encoding="utf-8") as handle:
                config = json.load(handle)
            converter = SynthesizerTrn(0, config["data"]["filter_length"] // 2 + 1, n_speakers=0, **config["model"])
            # Only weights are read from the checkpoints, never code.
            state = torch.load(paths[CONVERTER_FILES[1]], map_location="cpu", weights_only=True)
            converter.load_state_dict(state["model"], strict=False)
            converter.eval()
            # The same model wavmark.load_model() builds, read from the file fetched above.
            watermarker = wavmark_model.Model(16000, num_bit=32, n_fft=1000, hop_length=400, num_layers=8)
            watermarker.load_state_dict(torch.load(paths[WATERMARK_FILE], map_location="cpu", weights_only=True),
                                        strict=True)
            watermarker.eval()
            with self.lock:
                self.converter = converter
                self.config = config
                self.watermarker = watermarker
                silence = torch.zeros(config["data"]["sampling_rate"])
                self._convert(silence, self._embedding(silence), self._embedding(silence))
            self.set_state("ready")
            log("voice copying ready")
        except Exception as exception:
            traceback.print_exc()
            self.set_state("error", str(exception))

    def _download(self, hf_hub_download):
        """Every file's local path, downloading only what is not on disk yet."""
        wanted = [(CONVERTER_REPO, name) for name in CONVERTER_FILES] + [(WATERMARK_REPO, WATERMARK_FILE)]
        paths = {}
        for repo, name in wanted:
            try:
                paths[name] = hf_hub_download(repo, name, local_files_only=True)
            except Exception:
                self.set_state("downloading")
                paths[name] = hf_hub_download(repo, name)
        self.set_state("loading")
        return paths

    def convert(self, pcm, rate, speaker, reference):
        import numpy
        import torch

        audio = resample(torch.from_numpy(to_float(pcm)), rate, self.sampling_rate())
        with self.lock:
            if self.converter is None:
                raise NotReady()
            target = self._voice_of(speaker, reference)
            if target is None:
                raise ValueError("no reference for this speaker yet")
            copied = self._convert(audio, self._embedding(audio), target)
            peak = float(copied.abs().max()) if copied.numel() else 0.0
            if peak > 0:
                copied = copied * (0.9 / peak)
            # WavMark works on 16 kHz audio; marked at that rate, it survives
            # the way to 48 kHz and the voice mod's Opus.
            marked = self._watermark(resample(copied, self.sampling_rate(), WATERMARK_RATE))
        samples = resample(marked, WATERMARK_RATE, OUTPUT_RATE).numpy()
        return (numpy.clip(samples, -1.0, 1.0) * 32767).astype("<i2").tobytes()

    def sampling_rate(self):
        return self.config["data"]["sampling_rate"] if self.config else 22050

    def _voice_of(self, speaker, reference):
        """A player's tone colour, worked out once from their reference and kept by id."""
        import torch
        if speaker in self.voices:
            self.voices.move_to_end(speaker)
            return self.voices[speaker]
        if not reference:
            return None
        audio = resample(torch.from_numpy(to_float(reference)), REFERENCE_RATE, self.sampling_rate())
        embedding = self._embedding(audio)
        self.voices[speaker] = embedding
        while len(self.voices) > MAX_CACHED_VOICES:
            self.voices.popitem(last=False)
        return embedding

    def _embedding(self, audio):
        import torch
        with torch.no_grad():
            return self.converter.ref_enc(self._spectrogram(audio).transpose(1, 2)).unsqueeze(-1)

    def _convert(self, audio, source, target):
        import torch
        with torch.no_grad():
            spectrogram = self._spectrogram(audio)
            lengths = torch.LongTensor([spectrogram.size(-1)])
            out = self.converter.voice_conversion(spectrogram, lengths, sid_src=source, sid_tgt=target, tau=TAU)[0]
        return out[0, 0].float()

    def _spectrogram(self, audio):
        """The linear spectrogram OpenVoice was trained on, as its mel_processing makes it."""
        import torch
        data = self.config["data"]
        n_fft, hop, window = data["filter_length"], data["hop_length"], data["win_length"]
        y = torch.nn.functional.pad(audio.view(1, 1, -1), ((n_fft - hop) // 2, (n_fft - hop) // 2), mode="reflect")
        spectrum = torch.stft(y.view(1, -1), n_fft, hop_length=hop, win_length=window,
                              window=torch.hann_window(window), center=False, return_complex=True)
        return torch.sqrt(spectrum.real.pow(2) + spectrum.imag.pow(2) + 1e-6)

    def _watermark(self, audio16k):
        """WavMark laid out as OpenVoice's ToneColorConverter.add_watermark
        does it: 32 bits of the message in every other second."""
        import torch
        bits = message_bits(WATERMARK_MESSAGE)
        audio = audio16k.clone()
        for n in range(len(bits) // 32):
            start = 2 * n * WATERMARK_CHUNK
            chunk = audio[start:start + WATERMARK_CHUNK]
            if chunk.numel() != WATERMARK_CHUNK:
                break
            with torch.no_grad():
                marked = self.watermarker.encode(chunk[None], torch.FloatTensor(bits[n * 32:(n + 1) * 32])[None])
            audio[start:start + WATERMARK_CHUNK] = marked.squeeze()
        return audio


class NotReady(Exception):
    pass


def message_bits(message, pad_len=8):
    """OpenVoice's utils.string_to_bits, flattened: pad_len bytes of ASCII,
    cut or padded with spaces, 8 bits each."""
    rows = [[int(bit) for bit in bin(ord(character))[2:].zfill(8)] for character in message[:pad_len]]
    rows.extend([[0, 0, 1, 0, 0, 0, 0, 0]] * (pad_len - len(rows)))
    return [bit for row in rows for bit in row]


def cpu_threads():
    """Leaves most of the processor to the game."""
    return max(1, min(4, (os.cpu_count() or 4) // 3))


def to_float(pcm):
    import numpy
    return numpy.frombuffer(pcm, dtype="<i2").astype(numpy.float32) / 32768.0


def resample(samples, source_rate, target_rate):
    """A band-limited resample, so the watermark is not smeared on the way."""
    import torchaudio.functional
    if source_rate == target_rate or samples.numel() == 0:
        return samples
    return torchaudio.functional.resample(samples, source_rate, target_rate)


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
            if urlparse(self.path).path != "/convert":
                self.reply_json(404, {"error": "not found"})
                return
            try:
                request = json.loads(body or b"{}")
                reference = request.get("reference") or ""
                pcm = engine.convert(base64.b64decode(request.get("audio", "")), int(request.get("rate", 22050)),
                                     str(request.get("speaker", "")),
                                     base64.b64decode(reference) if reference else b"")
                self.reply(200, pcm, "application/octet-stream")
            except NotReady:
                self.reply_json(503, engine.status())
            except ValueError as exception:
                self.reply_json(400, {"error": str(exception)})
            except Exception as exception:
                traceback.print_exc()
                self.reply_json(500, {"error": str(exception)})

        def reply_json(self, code, payload):
            self.reply(code, json.dumps(payload, ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8")

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
    arguments = parser.parse_args()

    os.makedirs(arguments.models, exist_ok=True)
    os.environ["HF_HOME"] = arguments.models
    os.environ["HF_HUB_DISABLE_XET"] = "1"
    os.environ["HF_HUB_DISABLE_TELEMETRY"] = "1"
    os.environ["HF_HUB_DISABLE_PROGRESS_BARS"] = "1"
    os.environ["HF_HUB_DISABLE_IMPLICIT_TOKEN"] = "1"
    watch_parent(arguments.parent_pid)

    engine = VoiceEngine(arguments.models)
    engine.load_async()
    server = ThreadingHTTPServer(("127.0.0.1", arguments.port), make_handler(engine))
    server.daemon_threads = True
    log("listening on 127.0.0.1:%d" % arguments.port)
    server.serve_forever()


if __name__ == "__main__":
    main()
