"""Voice Translate speech engine.

A small local HTTP server around faster-whisper, started and stopped by the
Voice Translate mod. It only listens on 127.0.0.1 and exits on its own when
the game that started it goes away, so a crash cannot leave it holding the GPU.

Endpoints:
  GET  /status      what the engine is doing and which model is loaded
  POST /load        {"model": "large-v3-turbo", "device": "auto"}; loads in the background
  POST /transcribe  body: 16 kHz mono PCM16 little-endian; query: language, prompt
"""

import argparse
import json
import os
import sys
import threading
import time
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

SAMPLE_RATE = 16000
MAX_AUDIO_SECONDS = 60
# Rough download sizes, used to show progress while a model downloads.
MODEL_MEGABYTES = {
    "tiny": 75,
    "base": 145,
    "small": 484,
    "medium": 1530,
    "large-v3-turbo": 1620,
    "large-v3": 3090,
}
# Where each model lives on the Hugging Face Hub, as faster-whisper 1.2 names them.
# The mod reads the same names to show which models are already downloaded.
MODEL_REPOS = {
    "tiny": "Systran/faster-whisper-tiny",
    "base": "Systran/faster-whisper-base",
    "small": "Systran/faster-whisper-small",
    "medium": "Systran/faster-whisper-medium",
    "large-v3-turbo": "mobiuslabsgmbh/faster-whisper-large-v3-turbo",
    "large-v3": "Systran/faster-whisper-large-v3",
}


def add_nvidia_library_paths():
    """Lets CTranslate2 find the cuBLAS and cuDNN that pip installed next to it.

    The NVIDIA wheels put their DLLs and shared objects in nvidia/<lib>/bin or
    lib inside site-packages, where neither Windows nor the dynamic linker look.
    """
    try:
        import nvidia  # noqa: F401  (namespace package from the NVIDIA wheels)
    except ImportError:
        return
    for base in getattr(sys.modules["nvidia"], "__path__", []):
        for library in ("cublas", "cudnn"):
            for folder in ("bin", "lib"):
                path = os.path.join(base, library, folder)
                if not os.path.isdir(path):
                    continue
                if hasattr(os, "add_dll_directory"):
                    os.add_dll_directory(path)
                os.environ["PATH"] = path + os.pathsep + os.environ.get("PATH", "")
                os.environ["LD_LIBRARY_PATH"] = path + os.pathsep + os.environ.get("LD_LIBRARY_PATH", "")


class Engine:
    """Owns the loaded model. Whisper runs one clip at a time on one device,
    so every transcription goes through a single lock."""

    def __init__(self, models_dir):
        self.models_dir = models_dir
        self.lock = threading.Lock()
        self.state_lock = threading.Lock()
        self.model = None
        self.model_name = ""
        self.device = ""
        self.compute_type = ""
        self.state = "idle"
        self.error = ""
        # What was asked for last, whether or not it worked: the mod compares
        # this with what it wants, so a request is never silently lost.
        self.target_model = ""
        self.target_device = ""
        self.download_megabytes = 0
        self.loading_thread = None

    # --- status ---------------------------------------------------------------

    def status(self):
        with self.state_lock:
            result = {
                "state": self.state,
                "model": self.model_name,
                "device": self.device,
                "compute_type": self.compute_type,
                "target": self.target_model,
                "target_device": self.target_device,
                "error": self.error,
                "progress": -1,
            }
        if result["state"] == "downloading" and self.download_megabytes > 0:
            done = folder_megabytes(repo_folder(self.models_dir, self.target_model))
            result["progress"] = max(0, min(99, int(done * 100 / self.download_megabytes)))
        return result

    def set_state(self, state, error=""):
        with self.state_lock:
            self.state = state
            self.error = error

    # --- loading --------------------------------------------------------------

    def load_async(self, model_name, device):
        """Starts loading a model; False while another load is still running.

        A download cannot be stopped halfway from inside the process, so a
        busy engine refuses instead of queueing, and the mod restarts it when
        it wants something else. Whatever was downloaded so far stays and is
        resumed the next time that model is asked for.
        """
        with self.state_lock:
            if self.state in ("loading", "downloading"):
                return False
            self.state = "loading"
            self.error = ""
            self.target_model = model_name
            self.target_device = device
        self.loading_thread = threading.Thread(
            target=self._load, args=(model_name, device), name="model-loader", daemon=True)
        self.loading_thread.start()
        return True

    def _load(self, model_name, device):
        try:
            from faster_whisper import WhisperModel
            from faster_whisper.utils import download_model

            path = self._download(model_name, download_model)
            self.set_state("loading")
            candidates = device_candidates(device)
            last_error = None
            for candidate_device, compute_type in candidates:
                try:
                    model = WhisperModel(path, device=candidate_device, compute_type=compute_type)
                    self._warm_up(model)
                except Exception as exception:  # a GPU without the right libraries fails here
                    last_error = exception
                    log("could not start on %s/%s: %s" % (candidate_device, compute_type, exception))
                    continue
                with self.lock:
                    self.model = model
                with self.state_lock:
                    self.model_name = model_name
                    self.device = candidate_device
                    self.compute_type = compute_type
                    self.state = "ready"
                    self.error = ""
                log("model %s ready on %s (%s)" % (model_name, candidate_device, compute_type))
                return
            raise RuntimeError("no device could run the model: %s" % last_error)
        except Exception as exception:
            traceback.print_exc()
            self.set_state("error", str(exception))

    def _download(self, model_name, download_model):
        """The model's folder, downloading it first only if it is not complete
        on disk; a model that is there loads without touching the network."""
        try:
            return download_model(model_name, cache_dir=self.models_dir, local_files_only=True)
        except Exception:
            pass
        self.download_megabytes = MODEL_MEGABYTES.get(model_name, 0)
        self.set_state("downloading")
        try:
            return download_model(model_name, cache_dir=self.models_dir)
        finally:
            self.download_megabytes = 0

    @staticmethod
    def _warm_up(model):
        """The first call on a GPU compiles kernels and takes seconds; doing it
        here keeps that delay out of the first real sentence."""
        import numpy
        silence = numpy.zeros(SAMPLE_RATE, dtype=numpy.float32)
        segments, _ = model.transcribe(silence, language="en", beam_size=1, without_timestamps=True)
        list(segments)

    # --- transcription --------------------------------------------------------

    def transcribe(self, pcm, language, prompt, beam_size):
        import numpy
        audio = numpy.frombuffer(pcm, dtype="<i2").astype(numpy.float32) / 32768.0
        started = time.perf_counter()
        with self.lock:
            if self.model is None:
                raise EngineNotReady()
            segments, info = self.model.transcribe(
                audio,
                language=language or None,
                beam_size=beam_size,
                initial_prompt=prompt or None,
                condition_on_previous_text=False,
                without_timestamps=True,
                vad_filter=True,
                vad_parameters={"min_silence_duration_ms": 400, "speech_pad_ms": 200},
            )
            parts = []
            for segment in segments:
                parts.append({
                    "text": segment.text.strip(),
                    "avg_logprob": segment.avg_logprob,
                    "no_speech_prob": segment.no_speech_prob,
                    "compression_ratio": segment.compression_ratio,
                })
        return {
            "text": " ".join(part["text"] for part in parts if part["text"]).strip(),
            "language": info.language,
            "language_probability": info.language_probability,
            "duration": len(audio) / SAMPLE_RATE,
            "processing_seconds": time.perf_counter() - started,
            "segments": parts,
        }


class EngineNotReady(Exception):
    pass


def device_candidates(requested):
    """The device and precision to try, best first."""
    cuda = []
    try:
        import ctranslate2
        if ctranslate2.get_cuda_device_count() > 0:
            cuda = [("cuda", "float16"), ("cuda", "int8_float16")]
    except Exception as exception:
        log("CUDA check failed: %s" % exception)
    cpu = [("cpu", "int8")]
    if requested == "cpu":
        return cpu
    if requested == "cuda":
        return cuda or cpu
    return cuda + cpu


def repo_folder(models_dir, model_name):
    """Where the Hugging Face cache keeps one model: models--<owner>--<name>."""
    repo = MODEL_REPOS.get(model_name, model_name)
    return os.path.join(models_dir, "models--" + repo.replace("/", "--"))


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

        def log_message(self, fmt, *args):  # keep the log for things that matter
            pass

        def do_GET(self):
            if urlparse(self.path).path == "/status":
                self.reply(200, engine.status())
            else:
                self.reply(404, {"error": "not found"})

        def do_POST(self):
            url = urlparse(self.path)
            length = int(self.headers.get("Content-Length", "0"))
            if length < 0 or length > MAX_AUDIO_SECONDS * SAMPLE_RATE * 2:
                self.reply(413, {"error": "body too large"})
                return
            body = self.rfile.read(length)
            if url.path == "/load":
                request = json.loads(body or b"{}")
                started = engine.load_async(str(request.get("model", "small")), str(request.get("device", "auto")))
                self.reply(202 if started else 409, engine.status())
            elif url.path == "/transcribe":
                self.transcribe(body, parse_qs(url.query))
            else:
                self.reply(404, {"error": "not found"})

        def transcribe(self, body, query):
            language = query.get("language", [""])[0]
            prompt = query.get("prompt", [""])[0]
            beam_size = int(query.get("beam", ["5"])[0])
            try:
                self.reply(200, engine.transcribe(body, language, prompt, max(1, min(beam_size, 8))))
            except EngineNotReady:
                self.reply(503, engine.status())
            except Exception as exception:
                traceback.print_exc()
                self.reply(500, {"error": str(exception)})

        def reply(self, code, payload):
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

    return Handler


def watch_parent(pid):
    """Exits when the game process is gone."""
    if pid <= 0:
        return

    def alive():
        if os.name == "nt":
            import ctypes
            kernel32 = ctypes.windll.kernel32
            handle = kernel32.OpenProcess(0x1000, False, pid)  # PROCESS_QUERY_LIMITED_INFORMATION
            if not handle:
                return False
            code = ctypes.c_ulong()
            kernel32.GetExitCodeProcess(handle, ctypes.byref(code))
            kernel32.CloseHandle(handle)
            return code.value == 259  # STILL_ACTIVE
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
    parser.add_argument("--model", default="")
    parser.add_argument("--device", default="auto")
    arguments = parser.parse_args()

    os.makedirs(arguments.models, exist_ok=True)
    # Everything the download touches stays inside the models folder, so the
    # progress shown is the folder growing and deleting it removes it all.
    # Xet would stage chunks in a cache elsewhere and keep a second copy; a
    # plain download goes straight to its final place and resumes if cut.
    os.environ["HF_HOME"] = os.path.join(arguments.models, ".hf")
    os.environ["HF_HUB_DISABLE_XET"] = "1"
    os.environ["HF_HUB_DISABLE_TELEMETRY"] = "1"
    os.environ["HF_HUB_DISABLE_PROGRESS_BARS"] = "1"
    os.environ["HF_HUB_DISABLE_IMPLICIT_TOKEN"] = "1"
    add_nvidia_library_paths()
    watch_parent(arguments.parent_pid)

    engine = Engine(arguments.models)
    if arguments.model:
        engine.load_async(arguments.model, arguments.device)

    server = ThreadingHTTPServer(("127.0.0.1", arguments.port), make_handler(engine))
    server.daemon_threads = True
    log("listening on 127.0.0.1:%d" % arguments.port)
    server.serve_forever()


if __name__ == "__main__":
    main()
