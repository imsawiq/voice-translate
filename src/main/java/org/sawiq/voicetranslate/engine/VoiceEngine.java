package org.sawiq.voicetranslate.engine;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.net.Http;

/**
 * Keeps the voice engine, which speaks translations, running while dubbing
 * or translating the player's own speech is on, and stopped otherwise.
 *
 * <p>Its states are the speech engine's, so the screens describe both the
 * same way. There is one model and nothing to switch; a device change
 * restarts the process.</p>
 */
public final class VoiceEngine implements AutoCloseable {
    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(2);
    private static final long START_GRACE_MILLIS = 120_000L;
    private static final long RETRY_AFTER_FAILURE_MILLIS = 30_000L;
    private static final long DISK_CHECK_MILLIS = 5_000L;

    private final EngineInstaller installer = new EngineInstaller(EngineComponent.VOICE);
    private final EngineProcess process = new EngineProcess(EngineComponent.VOICE);
    private final ScheduledExecutorService poller;
    private volatile LocalEngine.Status status = LocalEngine.Status.of(LocalEngine.Phase.STOPPED);
    private volatile boolean isWanted;
    private volatile String wantedDevice = "auto";
    private volatile String runningDevice = "";
    private volatile long lastFailureAt;
    private volatile long lastDiskCheck;

    public VoiceEngine() {
        this.poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Voice Translate voice engine watch");
            thread.setDaemon(true);
            return thread;
        });
        this.installer.resumeInterruptedInstall();
        this.poller.scheduleWithFixedDelay(this::poll, 0, 1, TimeUnit.SECONDS);
    }

    public EngineInstaller installer() {
        return this.installer;
    }

    public LocalEngine.Status status() {
        return this.status;
    }

    public void want(boolean shouldRun, String device) {
        this.isWanted = shouldRun;
        this.wantedDevice = device;
        if (shouldRun) {
            this.lastFailureAt = 0L;
        }
    }

    /** The engine's address while it can speak, otherwise null. */
    public String address() {
        return this.status.phase() == LocalEngine.Phase.READY ? "http://127.0.0.1:" + this.process.port() : null;
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
            VoiceTranslate.LOGGER.error("Checking the voice engine failed", exception);
        }
    }

    private LocalEngine.Status nextStatus() {
        long now = System.currentTimeMillis();
        if (now - this.lastDiskCheck > DISK_CHECK_MILLIS) {
            this.lastDiskCheck = now;
            this.installer.refresh();
        }
        EngineInstaller.Progress install = this.installer.progress();
        switch (install.state()) {
            case UNSUPPORTED -> {
                return LocalEngine.Status.of(LocalEngine.Phase.UNSUPPORTED);
            }
            case INSTALLING -> {
                return new LocalEngine.Status(LocalEngine.Phase.INSTALLING, install.percent(), "", "", install.detail());
            }
            case NOT_INSTALLED, FAILED -> {
                this.process.stop();
                return install.state() == EngineInstaller.State.FAILED
                        ? new LocalEngine.Status(LocalEngine.Phase.FAILED, -1, "", "", install.detail())
                        : LocalEngine.Status.of(LocalEngine.Phase.NOT_INSTALLED);
            }
            case INSTALLED -> {
                // carry on below
            }
        }
        if (this.installer.needsUpdate()) {
            this.process.stop();
            this.installer.install();
            return new LocalEngine.Status(LocalEngine.Phase.INSTALLING, -1, "", "", "");
        }

        EngineProcess.Exit exit = this.process.takeUnexpectedExit();
        if (exit != null) {
            VoiceTranslate.LOGGER.warn("Voice engine exited with code {} after {} ms: {}",
                    exit.exitCode(), exit.uptimeMillis(), exit.lastLogLine());
            this.lastFailureAt = now;
            return new LocalEngine.Status(LocalEngine.Phase.FAILED, -1, "", "", exit.lastLogLine());
        }
        if (!this.isWanted) {
            this.process.stop();
            return LocalEngine.Status.of(LocalEngine.Phase.STOPPED);
        }
        if (this.process.isRunning() && !this.wantedDevice.equals(this.runningDevice)) {
            this.process.stop();
        }
        if (!this.process.isRunning()) {
            if (now - this.lastFailureAt < RETRY_AFTER_FAILURE_MILLIS) {
                return this.status;
            }
            try {
                this.process.start(List.of("--device", this.wantedDevice));
                this.runningDevice = this.wantedDevice;
                return LocalEngine.Status.of(LocalEngine.Phase.STARTING);
            } catch (IOException exception) {
                this.lastFailureAt = now;
                VoiceTranslate.LOGGER.warn("Could not start the voice engine", exception);
                return new LocalEngine.Status(LocalEngine.Phase.FAILED, -1, "", "", String.valueOf(exception.getMessage()));
            }
        }
        return askEngine(now);
    }

    private LocalEngine.Status askEngine(long now) {
        JsonObject answer;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.process.port() + "/status"))
                    .timeout(STATUS_TIMEOUT).GET().build();
            HttpResponse<String> response = Http.LOCAL.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            answer = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            // Importing PyTorch takes a while before the engine listens.
            boolean isStillStarting = now - this.process.startedAt() < START_GRACE_MILLIS;
            return isStillStarting ? LocalEngine.Status.of(LocalEngine.Phase.STARTING) : this.status;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return this.status;
        }
        String device = text(answer, "device");
        return switch (text(answer, "state")) {
            case "ready" -> new LocalEngine.Status(LocalEngine.Phase.READY, 100, "chatterbox", device, "");
            case "downloading" -> new LocalEngine.Status(LocalEngine.Phase.DOWNLOADING_MODEL,
                    answer.has("progress") ? answer.get("progress").getAsInt() : -1, "chatterbox", "", "");
            case "error" -> new LocalEngine.Status(LocalEngine.Phase.FAILED, -1, "chatterbox", "", text(answer, "error"));
            default -> new LocalEngine.Status(LocalEngine.Phase.LOADING_MODEL, -1, "chatterbox", "", "");
        };
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
