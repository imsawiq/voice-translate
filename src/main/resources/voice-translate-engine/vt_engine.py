"""Voice Translate speech engine.

A small local HTTP server, started and stopped by the Voice Translate mod.
It recognises speech (Parakeet through sherpa-onnx, or faster-whisper) and
speaks translations with Piper voices. It only listens on 127.0.0.1 and
exits on its own when the game that started it goes away, so a crash cannot
leave it holding memory or the GPU.

Endpoints:
  GET  /status      what the engine is doing and which model is loaded
  POST /load        {"model": "parakeet", "device": "auto"}; loads in the background
  POST /transcribe  body: 16 kHz mono PCM16 little-endian; query: language, prompt
  POST /prepare     {"languages": ["ru"]}; downloads those voices in the background
  POST /speak       {"text", "language", "voice": "male"|"female", "speed"}; 48 kHz mono PCM16
"""

import argparse
import json
import os
import sys
import threading
import time
import traceback
from collections import OrderedDict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

SAMPLE_RATE = 16000
OUTPUT_RATE = 48000
MAX_AUDIO_SECONDS = 60
# "none" runs the engine for its voices only, for players who recognise speech in the cloud.
NO_MODEL = "none"
PARAKEET = "parakeet"
# Whisper tiny only tells Parakeet's speech apart by language; it is not used to transcribe it.
LANGUAGE_MODEL = "tiny"
# Rough download sizes, used to show progress while a model downloads.
MODEL_MEGABYTES = {
    PARAKEET: 670 + 75,
    "tiny": 75,
    "base": 145,
    "small": 484,
    "medium": 1530,
    "large-v3-turbo": 1620,
    "large-v3": 3090,
}
# Where each model lives on the Hugging Face Hub. The mod reads the same
# names to show which models are already downloaded.
MODEL_REPOS = {
    PARAKEET: "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
    "tiny": "Systran/faster-whisper-tiny",
    "base": "Systran/faster-whisper-base",
    "small": "Systran/faster-whisper-small",
    "medium": "Systran/faster-whisper-medium",
    "large-v3-turbo": "mobiuslabsgmbh/faster-whisper-large-v3-turbo",
    "large-v3": "Systran/faster-whisper-large-v3",
}
# The languages Parakeet v3 transcribes. Speech in any other goes to Whisper tiny.
PARAKEET_LANGUAGES = {
    "bg", "cs", "da", "de", "el", "en", "es", "et", "fi", "fr", "hr", "hu", "it", "lt",
    "lv", "mt", "nl", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "uk",
}
# One male and one female Piper voice per language, from the sherpa-onnx
# mirrors on the Hub (csukuangfj/vits-piper-<name>). Where a language has
# only one good voice, both point at it. The mod keeps the same list of
# languages in PiperLanguages.java.
VOICES = {
    "ar": ("ar_JO-kareem-medium", "ar_JO-kareem-medium"),
    "ca": ("ca_ES-upc_pau-x_low", "ca_ES-upc_ona-medium"),
    "cs": ("cs_CZ-jirka-medium", "cs_CZ-jirka-medium"),
    "cy": ("cy_GB-gwryw_gogleddol-medium", "cy_GB-bu_tts-medium"),
    "da": ("da_DK-talesyntese-medium", "da_DK-talesyntese-medium"),
    "de": ("de_DE-thorsten-medium", "de_DE-kerstin-low"),
    "el": ("el_GR-rapunzelina-low", "el_GR-rapunzelina-low"),
    "en": ("en_US-ryan-medium", "en_US-amy-medium"),
    "es": ("es_ES-davefx-medium", "es_AR-daniela-high"),
    "fa": ("fa_IR-amir-medium", "fa_IR-gyro-medium"),
    "fi": ("fi_FI-harri-medium", "fi_FI-harri-medium"),
    "fr": ("fr_FR-tom-medium", "fr_FR-siwis-medium"),
    "hi": ("hi_IN-rohan-medium", "hi_IN-priyamvada-medium"),
    "hu": ("hu_HU-imre-medium", "hu_HU-anna-medium"),
    "id": ("id_ID-news_tts-medium", "id_ID-news_tts-medium"),
    "is": ("is_IS-bui-medium", "is_IS-salka-medium"),
    "it": ("it_IT-riccardo-x_low", "it_IT-paola-medium"),
    "ka": ("ka_GE-natia-medium", "ka_GE-natia-medium"),
    "kk": ("kk_KZ-issai-high", "kk_KZ-issai-high"),
    "lb": ("lb_LU-marylux-medium", "lb_LU-marylux-medium"),
    "lv": ("lv_LV-aivars-medium", "lv_LV-aivars-medium"),
    "ne": ("ne_NP-google-medium", "ne_NP-google-medium"),
    "nl": ("nl_BE-rdh-medium", "nl_BE-nathalie-medium"),
    "no": ("no_NO-talesyntese-medium", "no_NO-talesyntese-medium"),
    "pl": ("pl_PL-darkman-medium", "pl_PL-gosia-medium"),
    "pt": ("pt_BR-faber-medium", "pt_BR-faber-medium"),
    "ro": ("ro_RO-mihai-medium", "ro_RO-mihai-medium"),
    "ru": ("ru_RU-denis-medium", "ru_RU-irina-medium"),
    "sk": ("sk_SK-lili-medium", "sk_SK-lili-medium"),
    "sl": ("sl_SI-artur-medium", "sl_SI-artur-medium"),
    "sr": ("sr_RS-serbski_institut-medium", "sr_RS-serbski_institut-medium"),
    "sv": ("sv_SE-nst-medium", "sv_SE-lisa-medium"),
    "sw": ("sw_CD-lanfrica-medium", "sw_CD-lanfrica-medium"),
    "tr": ("tr_TR-fettah-medium", "tr_TR-dfki-medium"),
    "uk": ("uk_UA-lada-x_low", "uk_UA-lada-x_low"),
    "vi": ("vi_VN-vais1000-medium", "vi_VN-vais1000-medium"),
    "zh": ("zh_CN-huayan-medium", "zh_CN-huayan-medium"),
}
# espeak-ng's pronunciation data is the same in every voice; it is fetched once from this one.
PHONEME_DATA_VOICE = "en_US-ryan-medium"
MAX_LOADED_VOICES = 4
TTS_THREADS = 2


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


def cpu_threads():
    """Leaves most of the processor to the game."""
    return max(1, min(4, (os.cpu_count() or 4) // 3))


def to_float(pcm):
    import numpy
    return numpy.frombuffer(pcm, dtype="<i2").astype(numpy.float32) / 32768.0


class WhisperRecognizer:
    """faster-whisper: any of 99 languages, fastest on an NVIDIA card."""

    def __init__(self, model):
        self.model = model

    def transcribe(self, audio, language, prompt, beam_size):
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
            "segments": parts,
        }


class ParakeetRecognizer:
    """Parakeet TDT v3 through sherpa-onnx: 25 European languages, several
    times faster than Whisper on a processor because it does not pad every
    phrase to 30 seconds. It does not say which language it heard, so
    Whisper tiny tells that, and transcribes the languages Parakeet lacks."""

    def __init__(self, recognizer, language_model):
        self.recognizer = recognizer
        self.language_model = WhisperRecognizer(language_model)

    def transcribe(self, audio, language, prompt, beam_size):
        probability = 1.0
        if not language:
            language, probability, _ = self.language_model.model.detect_language(audio)
        if language not in PARAKEET_LANGUAGES:
            return self.language_model.transcribe(audio, language, prompt, 1)
        stream = self.recognizer.create_stream()
        stream.accept_waveform(SAMPLE_RATE, audio)
        self.recognizer.decode_stream(stream)
        return {
            "text": stream.result.text.strip(),
            "language": language,
            "language_probability": probability,
            "segments": [],
        }


class Engine:
    """Owns the loaded recognition model. It runs one clip at a time, so
    every transcription goes through a single lock."""

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
        self.downloading_repos = []
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
            done = sum(folder_megabytes(repo_folder(self.models_dir, repo)) for repo in self.downloading_repos)
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
            if model_name == NO_MODEL:
                self._ready(None, NO_MODEL, "", "")
            elif model_name == PARAKEET:
                self._load_parakeet()
            else:
                self._load_whisper(model_name, device)
        except Exception as exception:
            traceback.print_exc()
            self.set_state("error", str(exception))

    def _load_parakeet(self):
        import sherpa_onnx
        from faster_whisper import WhisperModel

        folders = self._download([PARAKEET, LANGUAGE_MODEL])
        self.set_state("loading")
        folder = folders[PARAKEET]
        recognizer = sherpa_onnx.OfflineRecognizer.from_transducer(
            encoder=native_path(os.path.join(folder, "encoder.int8.onnx")),
            decoder=native_path(os.path.join(folder, "decoder.int8.onnx")),
            joiner=native_path(os.path.join(folder, "joiner.int8.onnx")),
            tokens=native_path(os.path.join(folder, "tokens.txt")),
            num_threads=cpu_threads(),
            model_type="nemo_transducer",
        )
        language_model = WhisperModel(folders[LANGUAGE_MODEL], device="cpu", compute_type="int8",
                                      cpu_threads=cpu_threads())
        model = ParakeetRecognizer(recognizer, language_model)
        model.transcribe(silence(), "en", "", 1)
        self._ready(model, PARAKEET, "cpu", "int8")

    def _load_whisper(self, model_name, device):
        from faster_whisper import WhisperModel

        path = self._download([model_name])[model_name]
        self.set_state("loading")
        last_error = None
        for candidate_device, compute_type in device_candidates(device):
            try:
                model = WhisperRecognizer(WhisperModel(path, device=candidate_device, compute_type=compute_type,
                                                       cpu_threads=cpu_threads()))
                # The first call on a GPU compiles kernels and takes seconds;
                # doing it here keeps that delay out of the first real sentence.
                model.transcribe(silence(), "en", "", 1)
            except Exception as exception:  # a GPU without the right libraries fails here
                last_error = exception
                log("could not start on %s/%s: %s" % (candidate_device, compute_type, exception))
                continue
            self._ready(model, model_name, candidate_device, compute_type)
            return
        raise RuntimeError("no device could run the model: %s" % last_error)

    def _ready(self, model, model_name, device, compute_type):
        with self.lock:
            self.model = model
        with self.state_lock:
            self.model_name = model_name
            self.device = device
            self.compute_type = compute_type
            self.state = "ready"
            self.error = ""
        log("model %s ready on %s (%s)" % (model_name, device or "-", compute_type or "-"))

    def _download(self, model_names):
        """Each model's folder, downloading only what is not complete on
        disk; models that are there load without touching the network."""
        folders = {}
        missing = []
        for name in model_names:
            try:
                folders[name] = self._fetch(name, local_files_only=True)
            except Exception:
                missing.append(name)
        if not missing:
            return folders
        self.download_megabytes = sum(MODEL_MEGABYTES.get(name, 0) for name in missing)
        self.downloading_repos = missing
        self.set_state("downloading")
        try:
            for name in missing:
                folders[name] = self._fetch(name, local_files_only=False)
        finally:
            self.download_megabytes = 0
            self.downloading_repos = []
        return folders

    def _fetch(self, name, local_files_only):
        """Only the files a model needs: Whisper's as faster-whisper picks
        them, Parakeet's without the sample recordings in its repo."""
        if name == PARAKEET:
            from huggingface_hub import snapshot_download
            return snapshot_download(MODEL_REPOS[name], cache_dir=self.models_dir, local_files_only=local_files_only,
                                     allow_patterns=["*.onnx", "tokens.txt"])
        from faster_whisper.utils import download_model
        return download_model(name, cache_dir=self.models_dir, local_files_only=local_files_only)

    # --- transcription --------------------------------------------------------

    def transcribe(self, pcm, language, prompt, beam_size):
        audio = to_float(pcm)
        started = time.perf_counter()
        with self.lock:
            if self.model is None:
                raise EngineNotReady()
            result = self.model.transcribe(audio, language, prompt, beam_size)
        result["duration"] = len(audio) / SAMPLE_RATE
        result["processing_seconds"] = time.perf_counter() - started
        return result


class Voices:
    """Piper voices, downloaded the first time a language is wanted and
    kept loaded for the few languages in use. A sentence takes a fraction
    of a second on any processor."""

    def __init__(self, models_dir):
        self.models_dir = models_dir
        self.lock = threading.Lock()
        self.loaded = OrderedDict()
        self.downloads = set()
        self.downloads_lock = threading.Lock()
        # One download at a time: every voice needs the shared phoneme data
        # first, and sherpa-onnx aborts the whole process on a half-written copy.
        self.download_lock = threading.Lock()
        # A voice counts as on disk only once its download finished; the Hub
        # cache shows a folder as soon as the first file of it arrives.
        self.complete_dir = os.path.join(models_dir, ".voices-complete")

    def prepare(self, languages):
        for language in languages:
            if language in VOICES:
                for name in set(VOICES[language]):
                    self._download_in_background(name)

    def speak(self, text, language, voice, speed):
        """48 kHz PCM16 bytes; None while the voice is still downloading."""
        import numpy

        names = VOICES.get(language)
        if names is None:
            raise UnknownLanguage(language)
        name = names[0] if voice == "male" else names[1]
        tts = self._loaded(name)
        if tts is None:
            self._download_in_background(name)
            return None
        with self.lock:
            audio = tts.generate(text, sid=0, speed=speed)
        samples = numpy.asarray(audio.samples, dtype=numpy.float32)
        if samples.size == 0:
            return b""
        samples = resample(samples, audio.sample_rate, OUTPUT_RATE)
        peak = float(numpy.max(numpy.abs(samples)))
        if peak > 0:
            samples *= 0.9 / peak
        return (samples * 32767).astype("<i2").tobytes()

    def downloading(self):
        with self.downloads_lock:
            return sorted(self.downloads)

    def _loaded(self, name):
        with self.lock:
            if name in self.loaded:
                self.loaded.move_to_end(name)
                return self.loaded[name]
        folder = self._local_folder(name)
        phonemes = self._local_folder(PHONEME_DATA_VOICE, phonemes=True)
        if folder is None or phonemes is None:
            return None
        import sherpa_onnx
        config = sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                    model=native_path(os.path.join(folder, name + ".onnx")),
                    tokens=native_path(os.path.join(folder, "tokens.txt")),
                    data_dir=native_path(os.path.join(phonemes, "espeak-ng-data")),
                ),
                num_threads=TTS_THREADS,
                provider="cpu",
            ),
        )
        tts = sherpa_onnx.OfflineTts(config)
        with self.lock:
            self.loaded[name] = tts
            while len(self.loaded) > MAX_LOADED_VOICES:
                self.loaded.popitem(last=False)
        log("voice %s ready" % name)
        return tts

    def _local_folder(self, name, phonemes=False):
        from huggingface_hub import snapshot_download
        if not os.path.isfile(self._complete_marker(name, phonemes)):
            return None
        if phonemes:
            folder = self._phoneme_folder()
            return folder if os.path.isfile(os.path.join(folder, "espeak-ng-data", "phontab")) else None
        try:
            folder = snapshot_download(voice_repo(name), cache_dir=self.models_dir, local_files_only=True,
                                       allow_patterns=voice_files(name, phonemes))
        except Exception:
            return None
        if not all(os.path.isfile(os.path.join(folder, path)) for path in (name + ".onnx", "tokens.txt")):
            return None
        return folder

    def _phoneme_folder(self):
        return os.path.join(self.models_dir, "piper-phonemes")

    def _complete_marker(self, name, phonemes):
        return os.path.join(self.complete_dir, name + (".phonemes" if phonemes else ".voice"))

    def _fetch(self, name, phonemes):
        from huggingface_hub import snapshot_download
        if self._local_folder(name, phonemes) is not None:
            return
        log("downloading %s %s" % ("phoneme data from" if phonemes else "voice", name))
        folder = snapshot_download(voice_repo(name), cache_dir=self.models_dir, allow_patterns=voice_files(name, phonemes))
        if phonemes:
            # espeak-ng cannot read through the links the Hub cache is made
            # of on Windows, so it gets a plain copy of its own.
            import shutil
            target = self._phoneme_folder()
            shutil.rmtree(target, ignore_errors=True)
            shutil.copytree(os.path.join(folder, "espeak-ng-data"), os.path.join(target, "espeak-ng-data"))
        os.makedirs(self.complete_dir, exist_ok=True)
        with open(self._complete_marker(name, phonemes), "w") as marker:
            marker.write("ok")

    def _download_in_background(self, name):
        with self.downloads_lock:
            if name in self.downloads:
                return
            self.downloads.add(name)
        threading.Thread(target=self._download, args=(name,), name="voice-download", daemon=True).start()

    def _download(self, name):
        try:
            with self.download_lock:
                self._fetch(PHONEME_DATA_VOICE, phonemes=True)
                self._fetch(name, phonemes=False)
        except Exception as exception:
            log("could not download voice %s: %s" % (name, exception))
        finally:
            with self.downloads_lock:
                self.downloads.discard(name)


def voice_repo(name):
    return "csukuangfj/vits-piper-" + name


def voice_files(name, phonemes=False):
    if phonemes:
        return ["espeak-ng-data/*", "espeak-ng-data/**"]
    return [name + ".onnx", "tokens.txt"]


def native_path(path):
    """A path native code can open. espeak-ng and parts of sherpa-onnx open
    files with narrow strings, which on Windows break on a user folder named
    in Cyrillic or any other non-ASCII script; the short 8.3 form is ASCII."""
    if os.name != "nt" or path.isascii():
        return path
    import ctypes
    from ctypes import wintypes
    get_short = ctypes.windll.kernel32.GetShortPathNameW
    get_short.argtypes = [wintypes.LPCWSTR, wintypes.LPWSTR, wintypes.DWORD]
    get_short.restype = wintypes.DWORD
    size = get_short(path, None, 0)
    if size == 0:
        return path
    buffer = ctypes.create_unicode_buffer(size)
    get_short(path, buffer, size)
    return buffer.value


def resample(samples, source_rate, target_rate):
    """Linear interpolation; plenty for speech that the voice mod encodes with Opus anyway."""
    import numpy
    if source_rate == target_rate:
        return samples
    count = int(len(samples) * target_rate / source_rate)
    positions = numpy.linspace(0, len(samples) - 1, count)
    return numpy.interp(positions, numpy.arange(len(samples)), samples).astype(numpy.float32)


def silence():
    import numpy
    return numpy.zeros(SAMPLE_RATE, dtype=numpy.float32)


class EngineNotReady(Exception):
    pass


class UnknownLanguage(Exception):
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


def make_handler(engine, voices):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt, *args):  # keep the log for things that matter
            pass

        def do_GET(self):
            if urlparse(self.path).path == "/status":
                status = engine.status()
                status["voice_downloads"] = voices.downloading()
                self.reply(200, status)
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
                started = engine.load_async(str(request.get("model", PARAKEET)), str(request.get("device", "auto")))
                self.reply(202 if started else 409, engine.status())
            elif url.path == "/transcribe":
                self.transcribe(body, parse_qs(url.query))
            elif url.path == "/prepare":
                request = json.loads(body or b"{}")
                voices.prepare([str(language) for language in request.get("languages", [])])
                self.reply(202, {"voice_downloads": voices.downloading()})
            elif url.path == "/speak":
                self.speak(json.loads(body or b"{}"))
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

        def speak(self, request):
            speed = float(request.get("speed", 1.0))
            try:
                pcm = voices.speak(str(request.get("text", "")), str(request.get("language", "")),
                                   str(request.get("voice", "male")), max(0.5, min(speed, 2.0)))
            except UnknownLanguage as exception:
                self.reply(422, {"error": "no voice for " + str(exception)})
                return
            except Exception as exception:
                traceback.print_exc()
                self.reply(500, {"error": str(exception)})
                return
            if pcm is None:
                self.reply(503, {"error": "the voice is downloading", "voice_downloads": voices.downloading()})
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(len(pcm)))
            self.end_headers()
            self.wfile.write(pcm)

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
    voices = Voices(arguments.models)
    if arguments.model:
        engine.load_async(arguments.model, arguments.device)

    server = ThreadingHTTPServer(("127.0.0.1", arguments.port), make_handler(engine, voices))
    server.daemon_threads = True
    log("listening on 127.0.0.1:%d" % arguments.port)
    server.serve_forever()


if __name__ == "__main__":
    main()
