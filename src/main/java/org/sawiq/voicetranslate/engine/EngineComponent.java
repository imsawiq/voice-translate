package org.sawiq.voicetranslate.engine;

import java.nio.file.Path;
import java.util.List;

/**
 * The two parts of the local engine, each with a Python environment of its
 * own: the light base, which recognises speech and speaks translations with
 * Piper voices, and the optional one that turns those voices into players'
 * own.
 *
 * <p>Voice copying needs PyTorch, over a gigabyte on disk even in its
 * processor build, which the base does without; keeping it apart keeps the
 * base light. Both live in the same engine folder and share uv and its
 * Python.</p>
 */
public enum EngineComponent {
    SPEECH("speech", ".venv", "vt_engine.py", "1.1.0", 1_430, 180),
    VOICE("voice", ".venv-voice", "vt_voice.py", "2.0.0", 450, 450);

    private final String id;
    private final String environment;
    private final String script;
    private final String version;
    private final int gpuDownloadMegabytes;
    private final int cpuDownloadMegabytes;

    EngineComponent(String id, String environment, String script, String version,
                    int gpuDownloadMegabytes, int cpuDownloadMegabytes) {
        this.id = id;
        this.environment = environment;
        this.script = script;
        this.version = version;
        this.gpuDownloadMegabytes = gpuDownloadMegabytes;
        this.cpuDownloadMegabytes = cpuDownloadMegabytes;
    }

    String version() {
        return this.version;
    }

    /** The pinned package list for this computer, one of the files bundled in the mod jar. */
    String requirementsFile(PackageSet packages) {
        boolean isGpu = packages == PackageSet.CUDA;
        return switch (this) {
            case SPEECH -> isGpu ? "requirements-cuda.txt" : "requirements-cpu.txt";
            case VOICE -> "requirements-voice.txt";
        };
    }

    /**
     * Extra uv arguments. The voice copying packages come partly from
     * PyTorch's own index, which carries no upload dates, so they are pinned
     * to exact versions instead of being cut off by date.
     */
    List<String> installArguments() {
        return switch (this) {
            case SPEECH -> List.of("--exclude-newer", EnginePaths.PACKAGES_PUBLISHED_BEFORE);
            case VOICE -> List.of("--index-strategy", "unsafe-best-match");
        };
    }

    /** Roughly what the packages and the model download, for telling the player before they agree. */
    public int downloadMegabytes(PackageSet packages) {
        return packages == PackageSet.CUDA ? this.gpuDownloadMegabytes : this.cpuDownloadMegabytes;
    }

    String script() {
        return this.script;
    }

    Path environmentDirectory() {
        return EnginePaths.engineDirectory().resolve(this.environment);
    }

    /** On Windows the windowless Python, so the engine does not pop up a console. */
    Path python() {
        return EnginePaths.isWindows()
                ? environmentDirectory().resolve("Scripts").resolve("pythonw.exe")
                : environmentDirectory().resolve("bin").resolve("python");
    }

    /** The console Python uv installs packages for. */
    Path pythonForInstall() {
        return EnginePaths.isWindows()
                ? environmentDirectory().resolve("Scripts").resolve("python.exe")
                : environmentDirectory().resolve("bin").resolve("python");
    }

    Path marker() {
        return EnginePaths.root().resolve(this == SPEECH ? "installed.properties" : "installed-" + this.id + ".properties");
    }

    Path pendingInstallMarker() {
        return EnginePaths.root().resolve(this == SPEECH ? "install-pending" : "install-" + this.id + "-pending");
    }

    String logName() {
        return this == SPEECH ? "engine-output.log" : this.id + "-output.log";
    }

    String installLogName() {
        return this == SPEECH ? "install.log" : "install-" + this.id + ".log";
    }

    /** Where the component keeps the models it downloads. */
    public Path modelsDirectory() {
        return this == SPEECH ? EnginePaths.modelsDirectory() : EnginePaths.root().resolve("voice-models");
    }
}
