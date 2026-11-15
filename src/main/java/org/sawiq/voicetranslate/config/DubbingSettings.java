package org.sawiq.voicetranslate.config;

/** Spoken translations, both ways. Mutable for Gson and the settings screen. */
public final class DubbingSettings {
    /** Speak other players' translations. */
    public boolean dubOthers = false;
    /**
     * In each player's own voice, copied from their speech by the optional
     * voice copying engine, rather than a Piper voice that matches theirs.
     */
    public boolean cloneVoices = false;
    /** How loud the original voice stays while it is being dubbed, percent. */
    public int originalVolume = 25;
    /** How loud the dub is, percent. */
    public int dubVolume = 100;
    /** Send the player's own speech translated. */
    public boolean translateMySpeech = false;
    /** ISO code the player's speech is translated into. */
    public String mySpeechLanguage = "en";

    void sanitize() {
        this.originalVolume = Math.clamp(this.originalVolume, 0, 100);
        this.dubVolume = Math.clamp(this.dubVolume, 10, 200);
        this.mySpeechLanguage = RecognitionSettings.orDefault(this.mySpeechLanguage, "en");
    }

    /** Whether anything is spoken, which takes the speech engine's voices. */
    public boolean speaksAnything() {
        return this.dubOthers || this.translateMySpeech;
    }

    /** Whether the voice copying engine should run. */
    public boolean needsVoiceEngine() {
        return this.cloneVoices && speaksAnything();
    }
}
