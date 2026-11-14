package org.sawiq.voicetranslate.engine;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The running engine: started without a window when translation needs it,
 * stopped when it does not or the game closes.
 *
 * <p>The engine is told the game's process id and exits by itself if the game
 * goes away without saying goodbye, so a crash cannot leave it holding the
 * graphics card in the background.</p>
 */
final class EngineProcess {
    private static final int LOG_TAIL_BYTES = 4096;

    private final EngineComponent component;
    private Process process;
    private int port;
    private long startedAt;

    /** How a run that nobody stopped ended. */
    record Exit(int exitCode, long uptimeMillis, String lastLogLine) {
    }

    EngineProcess(EngineComponent component) {
        this.component = component;
    }

    synchronized boolean isRunning() {
        return this.process != null && this.process.isAlive();
    }

    synchronized int port() {
        return this.port;
    }

    synchronized long startedAt() {
        return this.startedAt;
    }

    /** @param arguments what this component's script needs besides port, models folder and parent */
    synchronized void start(List<String> arguments) throws IOException {
        if (isRunning()) {
            return;
        }
        EngineInstaller.refreshBundledFiles();
        Files.createDirectories(EnginePaths.logDirectory());
        Files.createDirectories(this.component.modelsDirectory());
        this.port = freePort();
        List<String> command = new ArrayList<>(List.of(
                this.component.python().toString(), "-I",
                EnginePaths.engineDirectory().resolve(this.component.script()).toString(),
                "--port", Integer.toString(this.port),
                "--models", this.component.modelsDirectory().toString(),
                "--parent-pid", Long.toString(ProcessHandle.current().pid())));
        command.addAll(arguments);
        this.process = new ProcessBuilder(command)
                .directory(EnginePaths.engineDirectory().toFile())
                .redirectErrorStream(true)
                .redirectOutput(outputLog().toFile())
                .start();
        this.startedAt = System.currentTimeMillis();
    }

    /** Reports, once, that the engine exited although nobody asked it to; null otherwise. */
    synchronized Exit takeUnexpectedExit() {
        if (this.process == null || this.process.isAlive()) {
            return null;
        }
        Exit exit = new Exit(this.process.exitValue(), System.currentTimeMillis() - this.startedAt, lastLogLine());
        this.process = null;
        return exit;
    }

    /** Asks the engine to stop and returns at once; one still running three seconds later is killed. */
    synchronized void stop() {
        if (this.process == null) {
            return;
        }
        Process running = this.process;
        this.process = null;
        running.destroy();
        running.onExit()
                .completeOnTimeout(running, 3, TimeUnit.SECONDS)
                .thenAccept(exited -> {
                    if (exited.isAlive()) {
                        exited.descendants().forEach(ProcessHandle::destroyForcibly);
                        exited.destroyForcibly();
                    }
                });
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private Path outputLog() {
        return EnginePaths.logDirectory().resolve(this.component.logName());
    }

    /** The last non-empty line the engine printed; a Python traceback ends with the reason. */
    private String lastLogLine() {
        try (RandomAccessFile file = new RandomAccessFile(outputLog().toFile(), "r")) {
            long start = Math.max(0L, file.length() - LOG_TAIL_BYTES);
            byte[] tail = new byte[(int) (file.length() - start)];
            file.seek(start);
            file.readFully(tail);
            String[] lines = new String(tail, StandardCharsets.UTF_8).split("\\R");
            for (int i = lines.length - 1; i >= 0; i--) {
                if (!lines[i].isBlank()) {
                    return lines[i].strip();
                }
            }
        } catch (IOException ignored) {
            // No log: the exit code is all there is.
        }
        return "";
    }
}
