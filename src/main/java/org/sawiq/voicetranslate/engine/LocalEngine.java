package org.sawiq.voicetranslate.engine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.net.Http;

/**
 * Keeps the local engine in the state the settings ask for: running with the
 * chosen model while local recognition is in use, stopped otherwise.
 *
 * <p>A background thread looks at the engine once a second, starts or stops
 * it, asks it to switch models, and turns what it reports into a
 * {@link Status} the screens can show. Nothing here blocks the game thread.</p>
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
     * @param device   "cuda" or "cpu" once a model is loaded
     * @param detail   never translated: a file size, the last log line, an error
     */
    public record Status(Phase phase, int progress, String model, String device, String detail) {
        static Status of(Phase phase) {
            return new Status(phase, -1, "", "", "");
        }
    }

    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(2);
    private static final long START_GRACE_MILLIS = 60_000L;
    private static final long RETRY_AFTER_FAILURE_MILLIS = 30_000L;
    private static final long DISK_CHECK_MILLIS = 5_000L;

    private final EngineInstaller installer = new EngineInstaller();
    private final EngineProcess process = new EngineProcess();
    private final ScheduledExecutorService poller;
    private volatile Status status = Status.of(Phase.STOPPED);
    private volatile boolean isWanted;
    private volatile String wantedModel = "auto";
    private volatile String wantedDevice = "auto";
    private volatile long lastFailureAt;
    private volatile long lastDiskCheck;
    private String requestedModel = "";

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
        this.isWanted = shouldRun;
        this.wantedModel = model;
        this.wantedDevice = device;
        if (shouldRun) {
            this.lastFailureAt = 0L;
        }
    }

    /** The engine's address while it can transcribe, otherwise null. */
    public String address() {
        return this.status.phase() == Phase.READY ? "http://127.0.0.1:" + this.process.port() : null;
    }

    /** The model "auto" stands for on this computer. */
    public String resolveModel(String model) {
        if (!"auto".equals(model)) {
            return model;
        }
        PackageSet packages = this.installer.packageSet();
        return packages == null ? PackageSet.CPU.recommendedModel() : packages.recommendedModel();
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
            this.requestedModel = "";
            return new Status(Phase.FAILED, -1, "", "", exit.lastLogLine());
        }
        if (!this.isWanted) {
            this.process.stop();
            this.requestedModel = "";
            return Status.of(Phase.STOPPED);
        }
        if (!this.process.isRunning()) {
            if (now - this.lastFailureAt < RETRY_AFTER_FAILURE_MILLIS) {
                return this.status;
            }
            return startProcess();
        }
        return askEngine(now);
    }

    private Status startProcess() {
        String model = resolveModel(this.wantedModel);
        try {
            this.process.start(model, this.wantedDevice);
            this.requestedModel = model + "/" + this.wantedDevice;
            return new Status(Phase.STARTING, -1, model, "", "");
        } catch (IOException exception) {
            this.lastFailureAt = System.currentTimeMillis();
            VoiceTranslate.LOGGER.warn("Could not start the speech engine", exception);
            return new Status(Phase.FAILED, -1, "", "", String.valueOf(exception.getMessage()));
        }
    }

    private Status askEngine(long now) {
        JsonObject answer;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.process.port() + "/status"))
                    .timeout(STATUS_TIMEOUT).GET().build();
            HttpResponse<String> response = Http.LOCAL.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            answer = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            // Python takes a few seconds to import its libraries before it listens.
            boolean isStillStarting = now - this.process.startedAt() < START_GRACE_MILLIS;
            return isStillStarting ? new Status(Phase.STARTING, -1, "", "", "") : this.status;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return this.status;
        }

        String state = answer.get("state").getAsString();
        String model = answer.get("model").getAsString();
        String device = answer.get("device").getAsString();
        String wanted = resolveModel(this.wantedModel) + "/" + this.wantedDevice;
        boolean isSettled = state.equals("ready") || state.equals("error") || state.equals("idle");
        if (isSettled && !wanted.equals(this.requestedModel)) {
            requestModel(wanted);
            return new Status(Phase.LOADING_MODEL, -1, resolveModel(this.wantedModel), "", "");
        }
        return switch (state) {
            case "ready" -> new Status(Phase.READY, 100, model, device, "");
            case "downloading" -> new Status(Phase.DOWNLOADING_MODEL, answer.get("progress").getAsInt(), resolveModel(this.wantedModel), "", "");
            case "error" -> new Status(Phase.FAILED, -1, model, device, answer.get("error").getAsString());
            default -> new Status(Phase.LOADING_MODEL, -1, resolveModel(this.wantedModel), "", "");
        };
    }

    private void requestModel(String wanted) {
        String[] parts = wanted.split("/", 2);
        JsonObject body = new JsonObject();
        body.addProperty("model", parts[0]);
        body.addProperty("device", parts[1]);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.process.port() + "/load"))
                .timeout(STATUS_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            Http.LOCAL.send(request, HttpResponse.BodyHandlers.discarding());
            this.requestedModel = wanted;
        } catch (IOException exception) {
            VoiceTranslate.LOGGER.warn("Could not ask the speech engine to load {}: {}", wanted, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
