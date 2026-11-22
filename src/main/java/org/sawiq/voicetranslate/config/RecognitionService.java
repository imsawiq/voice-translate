package org.sawiq.voicetranslate.config;

/** Where speech is turned into text. */
public enum RecognitionService {
    /** Whisper on this computer; private, free, best with a graphics card. */
    LOCAL("", ""),
    /** Groq's hosted Whisper: very fast, has a free tier, needs a key. */
    GROQ("https://api.groq.com/openai/v1", "whisper-large-v3-turbo"),
    OPENAI("https://api.openai.com/v1", "gpt-4o-transcribe"),
    /** Any server with an OpenAI-compatible transcription endpoint. */
    CUSTOM("http://127.0.0.1:8000/v1", "Systran/faster-whisper-large-v3");

    private final String defaultUrl;
    private final String defaultModel;

    RecognitionService(String defaultUrl, String defaultModel) {
        this.defaultUrl = defaultUrl;
        this.defaultModel = defaultModel;
    }

    public String defaultUrl() {
        return this.defaultUrl;
    }

    public String defaultModel() {
        return this.defaultModel;
    }

    /** Whether other players' voices leave this computer. */
    public boolean isCloud() {
        return this == GROQ || this == OPENAI;
    }
}
