package org.sawiq.voicetranslate.config;

/** Spoken translations, both ways. Mutable for Gson and the settings screen. */
public final class DubbingSettings {
    /** Speak other players' translations in their voices. */
    public boolean dubOthers = false;
    /** In their own voice, copied from their speech, rather than the engine's. */
    public boolean cloneVoices = true;
    /** How loud the original voice stays while it is being dubbed, percent. */
    public int originalVolume = 25;
    /** How loud the dub is, percent. */
    public int dubVolume = 100;
    /** Send the player's own speech translated, in their voice. */
    public boolean translateMySpeech = false;
    /** ISO code the player's speech is translated into. */
    public String mySpeechLanguage = "en";
    /** Where the voice engine runs: "auto", "cuda" or "cpu". */
    public String device = "auto";

    void sanitize() {
        this.originalVolume = Math.clamp(this.originalVolume, 0, 100);
        this.dubVolume = Math.clamp(this.dubVolume, 10, 200);
        this.mySpeechLanguage = RecognitionSettings.orDefault(this.mySpeechLanguage, "en");
        if (!"cuda".equals(this.device) && !"cpu".equals(this.device)) {
            this.device = "auto";
        }
    }

    /** Whether anything needs the voice engine running. */
    public boolean needsVoiceEngine() {
        return this.dubOthers || this.translateMySpeech;
    }
}
