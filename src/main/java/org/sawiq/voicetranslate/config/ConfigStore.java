package org.sawiq.voicetranslate.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.sawiq.voicetranslate.VoiceTranslate;

/** Reads and writes {@code config/voice-translate.json}. */
public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;

    public ConfigStore(Path configDirectory) {
        this.file = configDirectory.resolve(VoiceTranslate.MOD_ID + ".json");
    }

    /** The saved settings; defaults when there are none or the file is broken. */
    public TranslateConfig load() {
        if (!Files.isRegularFile(this.file)) {
            return new TranslateConfig();
        }
        try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            TranslateConfig config = GSON.fromJson(reader, TranslateConfig.class);
            return (config == null ? new TranslateConfig() : config).sanitize();
        } catch (IOException | JsonParseException exception) {
            VoiceTranslate.LOGGER.warn("Could not read {}, using defaults: {}", this.file, exception.getMessage());
            keepBrokenCopy();
            return new TranslateConfig();
        }
    }

    /**
     * Writes to a temporary file first and moves it into place, so a crash
     * mid-write cannot leave half a config behind.
     */
    public void save(TranslateConfig config) {
        try {
            Files.createDirectories(this.file.getParent());
            Path temporary = this.file.resolveSibling(this.file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            try {
                Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            VoiceTranslate.LOGGER.error("Could not save {}", this.file, exception);
        }
    }

    /** A broken file is the player's edits; set it aside instead of overwriting it. */
    private void keepBrokenCopy() {
        try {
            Files.copy(this.file, this.file.resolveSibling(this.file.getFileName() + ".broken"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Nothing more can be done; the defaults still load.
        }
    }
}
