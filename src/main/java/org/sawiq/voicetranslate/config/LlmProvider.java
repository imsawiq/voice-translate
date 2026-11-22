package org.sawiq.voicetranslate.config;

/** Ready-made settings for the OpenAI-compatible chat services players use most. */
public enum LlmProvider {
    OPENAI("https://api.openai.com/v1", "gpt-4.1-mini", true),
    GROQ("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", true),
    OPENROUTER("https://openrouter.ai/api/v1", "google/gemini-2.5-flash", true),
    /** A model running at home; nothing leaves the computer. */
    OLLAMA("http://127.0.0.1:11434/v1", "gemma3:12b", false),
    CUSTOM("http://127.0.0.1:1234/v1", "", false);

    private final String defaultUrl;
    private final String defaultModel;
    private final boolean needsKey;

    LlmProvider(String defaultUrl, String defaultModel, boolean needsKey) {
        this.defaultUrl = defaultUrl;
        this.defaultModel = defaultModel;
        this.needsKey = needsKey;
    }

    public String defaultUrl() {
        return this.defaultUrl;
    }

    public String defaultModel() {
        return this.defaultModel;
    }

    public boolean needsKey() {
        return this.needsKey;
    }
}
