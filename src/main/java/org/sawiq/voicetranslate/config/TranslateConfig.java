package org.sawiq.voicetranslate.config;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Everything the player can set. Mutable for Gson and the settings screen. */
public final class TranslateConfig {
    public boolean enabled = true;
    public RecognitionSettings recognition = new RecognitionSettings();
    public TranslationSettings translation = new TranslationSettings();
    public DisplaySettings display = new DisplaySettings();
    public ApiKeys keys = new ApiKeys();
    /** Players whose speech is not translated. */
    public Set<UUID> mutedPlayers = new LinkedHashSet<>();
    /** The one-time hint about the settings key has been shown. */
    public boolean hasSeenWelcome = false;
    /** Also translate the player's own microphone, so the mod can be tried out alone. */
    public boolean translateOwnVoice = false;

    /** Repairs what a hand-edited or older file left missing or out of range. */
    public TranslateConfig sanitize() {
        if (this.recognition == null) {
            this.recognition = new RecognitionSettings();
        }
        if (this.translation == null) {
            this.translation = new TranslationSettings();
        }
        if (this.display == null) {
            this.display = new DisplaySettings();
        }
        if (this.keys == null) {
            this.keys = new ApiKeys();
        }
        if (this.mutedPlayers == null) {
            this.mutedPlayers = new LinkedHashSet<>();
        }
        this.mutedPlayers.removeIf(Objects::isNull);
        this.recognition.sanitize();
        this.translation.sanitize();
        this.display.sanitize();
        this.keys.sanitize();
        return this;
    }
}
