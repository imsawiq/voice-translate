package org.sawiq.voicetranslate.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.sawiq.voicetranslate.VoiceTranslate;

/**
 * The Whisper models on disk, read straight from the Hugging Face cache the
 * engine downloads into, so the settings can show which models are already
 * there and free the space of the ones not in use, whether or not the engine
 * is running.
 */
public final class ModelCache {
    /** The same repositories the engine's MODEL_REPOS names. */
    private static final Map<String, String> REPOSITORIES = Map.of(
            "small", "Systran/faster-whisper-small",
            "medium", "Systran/faster-whisper-medium",
            "large-v3-turbo", "mobiuslabsgmbh/faster-whisper-large-v3-turbo",
            "large-v3", "Systran/faster-whisper-large-v3");
    /** Download sizes, for telling the player what a model costs before it is picked. */
    private static final Map<String, Integer> DOWNLOAD_MEGABYTES = Map.of(
            "small", 484,
            "medium", 1_530,
            "large-v3-turbo", 1_620,
            "large-v3", 3_090);

    /** A model that is fully on disk. */
    public record Stored(String model, long megabytes) {
    }

    /** What the last look at the disk found, per model. */
    private record Entry(boolean isComplete, long megabytes) {
    }

    /** The screens ask every frame; the disk is looked at again only this often. */
    private static final long RESCAN_MILLIS = 2_000L;
    private static volatile Map<String, Entry> scanned = Map.of();
    private static volatile long scannedAt;

    private ModelCache() {
    }

    /** Whether every file the model needs is there; a cut-off download does not count. */
    public static boolean isDownloaded(String model) {
        Entry entry = scan().get(model);
        return entry != null && entry.isComplete();
    }

    private static boolean isComplete(String model) {
        Path snapshots = folder(model).resolve("snapshots");
        if (!Files.isDirectory(snapshots)) {
            return false;
        }
        try (Stream<Path> versions = Files.list(snapshots)) {
            return versions.anyMatch(version -> Files.exists(version.resolve("model.bin"))
                    && Files.exists(version.resolve("tokenizer.json")));
        } catch (IOException exception) {
            return false;
        }
    }

    public static int downloadMegabytes(String model) {
        return DOWNLOAD_MEGABYTES.getOrDefault(model, 0);
    }

    /** Every downloaded model, largest first. */
    public static List<Stored> stored() {
        List<Stored> models = new ArrayList<>();
        scan().forEach((model, entry) -> {
            if (entry.isComplete()) {
                models.add(new Stored(model, entry.megabytes()));
            }
        });
        models.sort(Comparator.comparingLong(Stored::megabytes).reversed());
        return models;
    }

    /**
     * Deletes every model except the ones kept, cut-off downloads included.
     *
     * @return megabytes freed
     */
    public static long deleteAllExcept(Set<String> kept) {
        long freed = 0;
        for (String model : REPOSITORIES.keySet()) {
            Path folder = folder(model);
            if (kept.contains(model) || !Files.exists(folder)) {
                continue;
            }
            long size = megabytesOnDisk(folder);
            try {
                deleteTree(folder);
                freed += size;
            } catch (IOException exception) {
                VoiceTranslate.LOGGER.warn("Could not delete the {} model", model, exception);
            }
        }
        scannedAt = 0L;
        return freed;
    }

    /** Any model other than these taking space, finished or not. */
    public static long megabytesExcept(Set<String> kept) {
        long total = 0;
        for (Map.Entry<String, Entry> model : scan().entrySet()) {
            if (!kept.contains(model.getKey())) {
                total += model.getValue().megabytes();
            }
        }
        return total;
    }

    private static Map<String, Entry> scan() {
        long now = System.currentTimeMillis();
        if (now - scannedAt > RESCAN_MILLIS) {
            Map<String, Entry> found = new HashMap<>();
            for (String model : REPOSITORIES.keySet()) {
                Path folder = folder(model);
                if (Files.exists(folder)) {
                    found.put(model, new Entry(isComplete(model), megabytesOnDisk(folder)));
                }
            }
            scanned = Map.copyOf(found);
            scannedAt = now;
        }
        return scanned;
    }

    /** models--owner--name, the Hugging Face cache layout. */
    private static Path folder(String model) {
        String repository = REPOSITORIES.getOrDefault(model, model);
        return EnginePaths.modelsDirectory().resolve("models--" + repository.replace("/", "--"));
    }

    /** Symbolic links are skipped: the cache links snapshot files to blobs, which would count twice. */
    private static long megabytesOnDisk(Path folder) {
        if (!Files.exists(folder)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            long bytes = files.filter(path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path))
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException exception) {
                            return 0L;
                        }
                    })
                    .sum();
            return bytes / (1024 * 1024);
        } catch (IOException exception) {
            return 0;
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
