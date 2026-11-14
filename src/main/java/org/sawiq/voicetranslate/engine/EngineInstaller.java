package org.sawiq.voicetranslate.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.net.Http;

/**
 * Installs one part of the local engine after the player agreed to it, and
 * removes it again.
 *
 * <p>Nothing is installed system-wide. uv, a single program, is downloaded
 * into the engine folder and fetches Python and the packages into that same
 * folder. uv is checked against a hash built into the mod, and the engine
 * scripts ship inside the mod jar, so nothing runs that the mod did not bring
 * or verify.</p>
 */
public final class EngineInstaller {
    public enum State {
        UNSUPPORTED,
        NOT_INSTALLED,
        INSTALLING,
        INSTALLED,
        FAILED
    }

    /** The install steps in order, as translation key suffixes. */
    public static final List<String> STEPS = List.of("download_uv", "python", "packages");

    /**
     * @param step    translation key suffix of the current step, empty when idle
     * @param percent 0..100 for downloads with a known size, -1 otherwise
     * @param detail  the last line uv printed, or why the install failed; never translated
     */
    public record Progress(State state, String step, int percent, String detail) {
        public int stepNumber() {
            return STEPS.indexOf(this.step) + 1;
        }
    }

    private static final int MAX_DETAIL_LENGTH = 140;
    private static final String RESOURCE_FOLDER = "/voice-translate-engine/";
    private static final List<String> BUNDLED_FILES = List.of(
            "vt_engine.py", "requirements-cuda.txt", "requirements-cpu.txt",
            "vt_voice.py", "requirements-voice-cuda.txt", "requirements-voice-cpu.txt");

    private final EngineComponent component;
    private volatile Progress progress;
    private volatile Progress outcome;
    private volatile PackageSet packageSet;
    private volatile Process child;
    private volatile boolean isCancelled;

    public EngineInstaller(EngineComponent component) {
        this.component = component;
        this.progress = idle();
        if (EnginePaths.isSupported()) {
            Thread detection = new Thread(() -> this.packageSet = PackageSet.detect(), "Voice Translate GPU detection");
            detection.setDaemon(true);
            detection.start();
        }
    }

    public EngineComponent component() {
        return this.component;
    }

    public Progress progress() {
        return this.progress;
    }

    /** Looks at the disk again; the folder may have been deleted by hand. */
    public void refresh() {
        if (this.progress.state() != State.INSTALLING) {
            this.progress = idle();
        }
    }

    public boolean isInstalled() {
        return this.progress.state() == State.INSTALLED;
    }

    /** The package set for this computer, or null while the graphics card is still being looked at. */
    public PackageSet packageSet() {
        PackageSet installed = installedPackageSet().orElse(null);
        return installed != null ? installed : this.packageSet;
    }

    /** The install that just ended, once. */
    public Progress takeOutcome() {
        Progress ended = this.outcome;
        this.outcome = null;
        return ended;
    }

    public synchronized void install() {
        if (this.progress.state() == State.INSTALLING || !EnginePaths.isSupported()) {
            return;
        }
        try {
            Files.createDirectories(EnginePaths.root());
            Files.writeString(this.component.pendingInstallMarker(), this.component.version(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            VoiceTranslate.LOGGER.warn("Could not note the engine install for the next start", exception);
        }
        this.isCancelled = false;
        this.progress = new Progress(State.INSTALLING, "download_uv", -1, "");
        Thread worker = new Thread(this::runInstall, "Voice Translate " + this.component.name().toLowerCase() + " install");
        worker.setDaemon(true);
        worker.start();
    }

    /** Carries on with an install that the game was closed during. */
    public void resumeInterruptedInstall() {
        if (EnginePaths.isSupported() && Files.isRegularFile(this.component.pendingInstallMarker()) && !isInstalled()) {
            VoiceTranslate.LOGGER.info("Carrying on with the {} engine install from the last session", this.component);
            install();
        }
    }

    /** An install made by an older mod version, whose packages no longer match the pins. */
    public boolean needsUpdate() {
        return isInstalled() && !this.component.version().equals(installedProperty("version").orElse(""));
    }

    public void cancel() {
        this.isCancelled = true;
        forgetPendingInstall();
        stopChild();
    }

    /** For the game closing: the note stays, so the next start carries on. */
    public void interrupt() {
        this.isCancelled = true;
        stopChild();
    }

    /**
     * Removes this part. Speech recognition is the base: removing it deletes
     * the whole engine folder, voice included. The caller stops the engine first.
     */
    public synchronized void uninstall() throws IOException {
        cancel();
        if (this.component == EngineComponent.SPEECH) {
            deleteTree(EnginePaths.root());
        } else {
            deleteTree(this.component.environmentDirectory());
            deleteTree(this.component.modelsDirectory());
            Files.deleteIfExists(this.component.marker());
        }
        this.progress = idle();
    }

    /** Copies the bundled scripts over the installed ones, so an engine always runs the mod's own version. */
    public static void refreshBundledFiles() throws IOException {
        Files.createDirectories(EnginePaths.engineDirectory());
        for (String name : BUNDLED_FILES) {
            try (InputStream input = EngineInstaller.class.getResourceAsStream(RESOURCE_FOLDER + name)) {
                if (input == null) {
                    throw new IOException(name + " is missing from the mod jar");
                }
                Files.copy(input, EnginePaths.engineDirectory().resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // --- Steps ----------------------------------------------------------------

    private void runInstall() {
        try {
            EnginePaths.UvBuild uv = EnginePaths.uvBuild().orElseThrow(() -> new IOException("unsupported system"));
            Files.createDirectories(EnginePaths.root());
            if (!Files.isRegularFile(EnginePaths.uvExecutable())) {
                Path archive = download("download_uv", uv.url(), uv.sha256());
                Archives.extractFile(archive, uv.isZip(), uv.executableName(), EnginePaths.uvExecutable());
                Files.deleteIfExists(archive);
            }
            refreshBundledFiles();

            String environment = EnginePaths.engineDirectory().relativize(this.component.environmentDirectory()).toString();
            runUv("python", List.of("venv", "--python", EnginePaths.PYTHON_VERSION, "--allow-existing", environment));
            PackageSet packages = this.packageSet != null ? this.packageSet : PackageSet.detect();
            this.packageSet = packages;
            List<String> arguments = new ArrayList<>(List.of("pip", "install",
                    "--python", this.component.pythonForInstall().toString()));
            arguments.addAll(this.component.installArguments());
            arguments.addAll(List.of("-r", this.component.requirementsFile(packages)));
            runUv("packages", arguments);

            writeMarker(packages);
            finish(new Progress(State.INSTALLED, "", 100, ""));
        } catch (IOException | RuntimeException exception) {
            String reason = this.isCancelled ? "cancelled" : describe(exception);
            if (!this.isCancelled) {
                VoiceTranslate.LOGGER.warn("{} engine install failed: {}", this.component, reason);
            }
            finish(new Progress(State.FAILED, "", -1, reason));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            finish(new Progress(State.FAILED, "", -1, "cancelled"));
        } finally {
            this.child = null;
        }
    }

    private void finish(Progress ended) {
        this.progress = ended;
        if (!this.isCancelled) {
            forgetPendingInstall();
            this.outcome = ended;
        }
    }

    /** Streams to a temporary file, hashing as it goes, and refuses a file that does not match. */
    private Path download(String step, String url, String expectedSha256) throws IOException, InterruptedException {
        this.progress = new Progress(State.INSTALLING, step, 0, "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).GET().build();
        HttpResponse<InputStream> response = Http.CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Download failed, HTTP " + response.statusCode() + ": " + url);
        }

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        Path target = Files.createTempFile(EnginePaths.root(), "download", ".part");
        MessageDigest digest = sha256();
        try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            long done = 0;
            int read;
            while ((read = input.read(buffer)) > 0) {
                if (this.isCancelled) {
                    throw new IOException("cancelled");
                }
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                done += read;
                int percent = total > 0 ? (int) (done * 100 / total) : -1;
                this.progress = new Progress(State.INSTALLING, step, percent, done / (1024 * 1024) + " MB");
            }
        } catch (IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }

        if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedSha256)) {
            Files.deleteIfExists(target);
            throw new IOException("The download does not match the expected hash: " + url);
        }
        return target;
    }

    /** Runs uv in the engine folder with its cache and Python kept inside the engine root. */
    private void runUv(String step, List<String> arguments) throws IOException, InterruptedException {
        this.progress = new Progress(State.INSTALLING, step, -1, "");
        List<String> command = Stream.concat(Stream.of(EnginePaths.uvExecutable().toString()), arguments.stream()).toList();
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(EnginePaths.engineDirectory().toFile())
                .redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.put("UV_CACHE_DIR", EnginePaths.root().resolve("cache").toString());
        environment.put("UV_PYTHON_INSTALL_DIR", EnginePaths.root().resolve("python").toString());
        // Never a Python the player installed: the engine would break the
        // day they update or remove it.
        environment.put("UV_MANAGED_PYTHON", "1");
        environment.put("UV_NO_PROGRESS", "1");
        // PyTorch's wheels are over two gigabytes; uv's default 30 s read timeout is too short on slow links.
        environment.put("UV_HTTP_TIMEOUT", "300");

        Files.createDirectories(EnginePaths.logDirectory());
        Path log = EnginePaths.logDirectory().resolve(this.component.installLogName());
        Process process = builder.start();
        this.child = process;
        try (BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = output.readLine()) != null) {
                Files.writeString(log, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                if (!line.isBlank()) {
                    this.progress = new Progress(State.INSTALLING, step, -1, shorten(line.strip()));
                }
            }
        }
        int exit = process.waitFor();
        if (this.isCancelled) {
            throw new IOException("cancelled");
        }
        if (exit != 0) {
            throw new IOException("uv " + arguments.getFirst() + " failed (exit " + exit + "), see " + log);
        }
    }

    // --- Files ----------------------------------------------------------------

    private void stopChild() {
        Process process = this.child;
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private Progress idle() {
        if (!EnginePaths.isSupported()) {
            return new Progress(State.UNSUPPORTED, "", -1, "");
        }
        boolean isInstalled = installedProperty("version").isPresent() && Files.isRegularFile(this.component.python());
        return new Progress(isInstalled ? State.INSTALLED : State.NOT_INSTALLED, "", -1, "");
    }

    private Optional<PackageSet> installedPackageSet() {
        return installedProperty("packages").flatMap(name -> {
            try {
                return Optional.of(PackageSet.valueOf(name));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        });
    }

    private Optional<String> installedProperty(String key) {
        Path marker = this.component.marker();
        if (!Files.isRegularFile(marker)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(marker)) {
            properties.load(input);
            return Optional.ofNullable(properties.getProperty(key));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private void writeMarker(PackageSet packages) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", this.component.version());
        properties.setProperty("packages", packages.name());
        try (OutputStream output = Files.newOutputStream(this.component.marker())) {
            properties.store(output, "Voice Translate " + this.component.name().toLowerCase() + " engine");
        }
    }

    private void forgetPendingInstall() {
        try {
            Files.deleteIfExists(this.component.pendingInstallMarker());
        } catch (IOException exception) {
            VoiceTranslate.LOGGER.warn("Could not clear the engine install note", exception);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is missing from this Java", exception);
        }
    }

    private static String shorten(String line) {
        return line.length() <= MAX_DETAIL_LENGTH ? line : line.substring(0, MAX_DETAIL_LENGTH) + "...";
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
