package org.sawiq.voicetranslate.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.net.Http;

/**
 * Keeps the local engine in the state the settings ask for: running with the
 * chosen model while local recognition is in use, with no model while only
 * its Piper voices are needed, stopped otherwise.
 *
 * <p>A background thread looks at the engine once a second and compares what
 * it reports it is working on with what the settings want. The engine's own
 * word is the truth: a request it refused, or one lost to a restart, is simply
 * made again on the next look, so the two cannot drift apart.</p>
 *
 * <p>A model choice only counts once it has stood for a moment. Cycling the
 * model button passes through every model on the way to the one wanted, and
 * none of those should start a download. When the engine is downloading a
 * model nobody wants any more, it is restarted: a download cannot be stopped
 * from inside it, and what it fetched so far is kept and resumed later.</p>
 */
public final class LocalEngine implements AutoCloseable {
    public enum Phase {
        UNSUPPORTED,
        NOT_INSTALLED,
        INSTALLING,
        STOPPED,
        STARTING,
        DOWNLOADING_MODEL,
        LOADING_MODEL,
        READY,
        FAILED
    }

    /**
     * @param progress 0..100 while installing or downloading, -1 when unknown
     * @param model    the model being prepared or in use
     * @param device   "cuda" or "cpu" once a model is loaded
     * @param detail   never translated: a file size, the last log line, an error
     */
    public record Status(Phase phase, int progress, String model, String device, String detail) {
        static Status of(Phase phase) {
            return new Status(phase, -1, "", "", "");
        }
    }

    /** What the engine said about itself. */
    private record Report(String state, String model, String device, String target, String targetDevice,
                          String error, int progress) {
    }

    public static final String PARAKEET = "parakeet";
    /** Runs the engine without a recognition model, for its voices alone. */
    public static final String NO_MODEL = "none";

    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(2);
    private static final long START_GRACE_MILLIS = 60_000L;
    private static final long RETRY_AFTER_FAILURE_MILLIS = 30_000L;
    private static final long DISK_CHECK_MILLIS = 5_000L;
    /** How long a model or device choice must stand before the engine acts on it. */
    private static final long CHOICE_SETTLE_MILLIS = 1_500L;
    /** A model that failed to download or load is tried again this often while it is still wanted. */
    private static final long MODEL_RETRY_MILLIS = 30_000L;

    private final EngineInstaller installer = new EngineInstaller(EngineComponent.SPEECH);
    private final EngineProcess process = new EngineProcess(EngineComponent.SPEECH);
    private final ScheduledExecutorService poller;
    private volatile Status status = Status.of(Phase.STOPPED);
    private volatile boolean isWanted;
    private volatile String wantedModel = "auto";
    private volatile String wantedDevice = "auto";
    private volatile long choiceChangedAt;
    private volatile long lastFailureAt;
    private volatile long lastDiskCheck;
    private volatile long lastModelErrorAt;
    private volatile Set<String> wantedVoices = Set.of();
    private Set<String> preparedVoices = Set.of();
    private long voicesPreparedForStart;

    public LocalEngine() {
        this.poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Voice Translate engine watch");
            thread.setDaemon(true);
            return thread;
        });
        this.installer.resumeInterruptedInstall();
        this.poller.scheduleWithFixedDelay(this::poll, 0, 1, TimeUnit.SECONDS);
    }

    public EngineInstaller installer() {
        return this.installer;
    }

    public Status status() {
        return this.status;
    }

    /** Asks for the engine to run with this model, or to stop. Cheap; call on every settings change. */
    public void want(boolean shouldRun, String model, String device) {
        if (!model.equals(this.wantedModel) || !device.equals(this.wantedDevice)) {
            this.choiceChangedAt = System.currentTimeMillis();
        }
        this.isWanted = shouldRun;
        this.wantedModel = model;
        this.wantedDevice = device;
        if (shouldRun) {
            this.lastFailureAt = 0L;
        }
    }

    /**
     * The languages whose voices should be on disk, so the first dubbed
     * sentence is not lost to a download. Cheap; call on every settings change.
     */
    public void wantVoices(Set<String> languages) {
        this.wantedVoices = Set.copyOf(languages);
    }

    /** The engine's address while it can transcribe, otherwise null. */
    public String address() {
        return this.status.phase() == Phase.READY ? "http://127.0.0.1:" + this.process.port() : null;
    }

    /** The engine's address while it answers at all: its voices work while a model is still loading. */
    public String voiceAddress() {
        return switch (this.status.phase()) {
            case READY, LOADING_MODEL, DOWNLOADING_MODEL -> "http://127.0.0.1:" + this.process.port();
            default -> null;
        };
    }

    /** The model "auto" stands for: Parakeet, quick and accurate on any processor. */
    public String resolveModel(String model) {
        return "auto".equals(model) ? PARAKEET : model;
    }

    /** The model the settings currently ask for, with "auto" resolved. */
    public String wantedModel() {
        return resolveModel(this.wantedModel);
    }

    /** Adds NVIDIA's libraries for Whisper; the engine starts again with them once they are in. */
    public void addGpuLibraries() {
        this.process.stop();
        this.installer.addGpuLibraries();
    }

    public void stopForUninstall() {
        this.isWanted = false;
        this.process.stop();
    }

    @Override
    public void close() {
        this.poller.shutdownNow();
        this.installer.interrupt();
        this.process.stop();
    }

    private void poll() {
        try {
            this.status = nextStatus();
        } catch (RuntimeException exception) {
            VoiceTranslate.LOGGER.error("Checking the speech engine failed", exception);
        }
    }

    private Status nextStatus() {
        long now = System.currentTimeMillis();
        if (now - this.lastDiskCheck > DISK_CHECK_MILLIS) {
            this.lastDiskCheck = now;
            this.installer.refresh();
        }
        EngineInstaller.Progress install = this.installer.progress();
        switch (install.state()) {
            case UNSUPPORTED -> {
                return Status.of(Phase.UNSUPPORTED);
            }
            case INSTALLING -> {
                return new Status(Phase.INSTALLING, install.percent(), "", "", install.detail());
            }
            case NOT_INSTALLED, FAILED -> {
                this.process.stop();
                return install.state() == EngineInstaller.State.FAILED
                        ? new Status(Phase.FAILED, -1, "", "", install.detail())
                        : Status.of(Phase.NOT_INSTALLED);
            }
            case INSTALLED -> {
                // carry on below
            }
        }
        if (this.installer.needsUpdate()) {
            this.process.stop();
            this.installer.install();
            return new Status(Phase.INSTALLING, -1, "", "", "");
        }

        EngineProcess.Exit exit = this.process.takeUnexpectedExit();
        if (exit != null) {
            VoiceTranslate.LOGGER.warn("Speech engine exited with code {} after {} ms: {}",
                    exit.exitCode(), exit.uptimeMillis(), exit.lastLogLine());
            this.lastFailureAt = now;
            return new Status(Phase.FAILED, -1, "", "", exit.lastLogLine());
        }
        if (!this.isWanted) {
            this.process.stop();
            return Status.of(Phase.STOPPED);
        }
        if (!this.process.isRunning()) {
            if (now - this.lastFailureAt < RETRY_AFTER_FAILURE_MILLIS) {
                return this.status;
            }
            return startProcess();
        }
        return followEngine(now);
    }

    private Status startProcess() {
        String model = resolveModel(this.wantedModel);
        try {
            this.process.start(List.of("--model", model, "--device", this.wantedDevice));
            return new Status(Phase.STARTING, -1, model, "", "");
        } catch (IOException exception) {
            this.lastFailureAt = System.currentTimeMillis();
            VoiceTranslate.LOGGER.warn("Could not start the speech engine", exception);
            return new Status(Phase.FAILED, -1, "", "", String.valueOf(exception.getMessage()));
        }
    }

    /** Turns the engine's report into a status, and steers it to the wanted model when it is elsewhere. */
    private Status followEngine(long now) {
        Report report = askEngine();
        if (report == null) {
            // Python takes a few seconds to import its libraries before it listens.
            boolean isStillStarting = now - this.process.startedAt() < START_GRACE_MILLIS;
            return isStillStarting ? new Status(Phase.STARTING, -1, resolveModel(this.wantedModel), "", "") : this.status;
        }

        prepareVoices();
        String model = resolveModel(this.wantedModel);
        boolean isOnWantedChoice = model.equals(report.target()) && this.wantedDevice.equals(report.targetDevice());
        boolean hasChoiceSettled = now - this.choiceChangedAt >= CHOICE_SETTLE_MILLIS;
        boolean isBusy = report.state().equals("downloading") || report.state().equals("loading");

        if (!isOnWantedChoice && hasChoiceSettled) {
            if (isBusy) {
                VoiceTranslate.LOGGER.info("Restarting the speech engine to switch from {} to {}", report.target(), model);
                this.process.stop();
                return new Status(Phase.STARTING, -1, model, "", "");
            }
            requestModel(model, this.wantedDevice);
            return new Status(Phase.LOADING_MODEL, -1, model, "", "");
        }

        return switch (report.state()) {
            case "ready" -> new Status(Phase.READY, 100, report.model(), report.device(), "");
            case "downloading" -> new Status(Phase.DOWNLOADING_MODEL, report.progress(), report.target(), "", "");
            case "error" -> {
                // The download may have failed for a dropped connection; try again now and then.
                if (this.lastModelErrorAt == 0L) {
                    this.lastModelErrorAt = now;
                } else if (now - this.lastModelErrorAt > MODEL_RETRY_MILLIS) {
                    this.lastModelErrorAt = 0L;
                    requestModel(report.target(), report.targetDevice());
                }
                yield new Status(Phase.FAILED, -1, report.target(), "", report.error());
            }
            default -> new Status(Phase.LOADING_MODEL, -1, report.target().isEmpty() ? model : report.target(), "", "");
        };
    }

    private Report askEngine() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.process.port() + "/status"))
                    .timeout(STATUS_TIMEOUT).GET().build();
            HttpResponse<String> response = Http.LOCAL.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject answer = JsonParser.parseString(response.body()).getAsJsonObject();
            return new Report(text(answer, "state"), text(answer, "model"), text(answer, "device"),
                    text(answer, "target"), text(answer, "target_device"), text(answer, "error"),
                    answer.has("progress") ? answer.get("progress").getAsInt() : -1);
        } catch (IOException | RuntimeException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Asks once per engine run and per change for the wanted voices to be downloaded. */
    private void prepareVoices() {
        Set<String> wanted = this.wantedVoices;
        long start = this.process.startedAt();
        if (wanted.isEmpty() || (wanted.equals(this.preparedVoices) && start == this.voicesPreparedForStart)) {
            return;
        }
        JsonArray languages = new JsonArray();
        wanted.forEach(languages::add);
        JsonObject body = new JsonObject();
        body.add("languages", languages);
        if (post("/prepare", body)) {
            this.preparedVoices = wanted;
            this.voicesPreparedForStart = start;
        }
    }

    /** A refusal (the engine is busy) needs no handling: the next look sees it and steers again. */
    private void requestModel(String model, String device) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("device", device);
        post("/load", body);
    }

    private boolean post(String path, JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.process.port() + path))
                .timeout(STATUS_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            Http.LOCAL.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (IOException exception) {
            VoiceTranslate.LOGGER.warn("The speech engine did not take {}: {}", path, exception.getMessage());
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
