package org.sawiq.voicetranslate.engine;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Where the local speech engine lives and which uv build this computer needs.
 *
 * <p>One install per computer, outside every Minecraft instance: Python, the
 * CUDA libraries and a model take a few gigabytes, and a player with five
 * modpacks should not have them five times. Deleting the folder removes
 * every trace.</p>
 */
public final class EnginePaths {
    /** Packages newer than this are never installed, so every install resolves to the same versions. */
    static final String PACKAGES_PUBLISHED_BEFORE = "2026-11-01T00:00:00Z";
    static final String PYTHON_VERSION = "3.12";

    private static final String UV_VERSION = "0.13.8";
    private static final String UV_BASE = "https://github.com/astral-sh/uv/releases/download/" + UV_VERSION + "/uv-";

    /** A uv download: its archive, the hash it must match, and the program's name inside. */
    record UvBuild(String url, String sha256, String executableName, boolean isZip) {
    }

    private EnginePaths() {
    }

    static Optional<UvBuild> uvBuild() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean isX64 = arch.equals("amd64") || arch.equals("x86_64");
        boolean isArm64 = arch.equals("aarch64") || arch.equals("arm64");
        if (os.contains("win") && isX64) {
            return Optional.of(new UvBuild(UV_BASE + "x86_64-pc-windows-msvc.zip",
                    "529e78a9cec6cd7e3e014f7ecd8bda0c113969d4ed0f2efeedb85c30943325b8", "uv.exe", true));
        }
        if (os.contains("linux") && isX64) {
            return Optional.of(new UvBuild(UV_BASE + "x86_64-unknown-linux-gnu.tar.gz",
                    "5acfd9177cfecf0bf87578f059728215ccc884388988f55f7a65041b57387d91", "uv", false));
        }
        if (os.contains("linux") && isArm64) {
            return Optional.of(new UvBuild(UV_BASE + "aarch64-unknown-linux-gnu.tar.gz",
                    "74f2f5c6024d397481db228db8fc25eed420db192243f88438c118e7e013e585", "uv", false));
        }
        if (os.contains("mac") && isArm64) {
            return Optional.of(new UvBuild(UV_BASE + "aarch64-apple-darwin.tar.gz",
                    "6b71028a6b904f2ae3340ef52e595512de807d06c0a20eaef2f692581b246558", "uv", false));
        }
        if (os.contains("mac") && isX64) {
            return Optional.of(new UvBuild(UV_BASE + "x86_64-apple-darwin.tar.gz",
                    "a04c2ffeac28b2003a2c13caf538ab425fcd7def3e4a41571f4a8fb370a66af3", "uv", false));
        }
        return Optional.empty();
    }

    public static boolean isSupported() {
        return uvBuild().isPresent();
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    public static Path root() {
        String home = System.getProperty("user.home");
        if (isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path base = localAppData != null && !localAppData.isBlank()
                    ? Path.of(localAppData)
                    : Path.of(home, "AppData", "Local");
            return base.resolve("VoiceTranslateEngine");
        }
        if (isMac()) {
            return Path.of(home, "Library", "Application Support", "VoiceTranslateEngine");
        }
        String dataHome = System.getenv("XDG_DATA_HOME");
        Path base = dataHome != null && !dataHome.isBlank() ? Path.of(dataHome) : Path.of(home, ".local", "share");
        return base.resolve("voice-translate-engine");
    }

    static Path uvExecutable() {
        return root().resolve("uv").resolve(isWindows() ? "uv.exe" : "uv");
    }

    static Path engineDirectory() {
        return root().resolve("engine");
    }

    public static Path modelsDirectory() {
        return root().resolve("models");
    }

    static Path logDirectory() {
        return root().resolve("logs");
    }

}
