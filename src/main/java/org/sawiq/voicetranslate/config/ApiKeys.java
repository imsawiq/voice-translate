package org.sawiq.voicetranslate.config;

/**
 * Keys for the services that need one, kept once per provider so a Groq key
 * serves both recognition and translation.
 *
 * <p>Stored in plain text in the config file, like every other mod's
 * settings: the game folder is the player's own. They are never logged.</p>
 */
public final class ApiKeys {
    public String groq = "";
    public String openAi = "";
    public String deepL = "";
    public String openRouter = "";
    /** For the custom recognition server and custom or Ollama LLM endpoints, if they want one. */
    public String custom = "";

    public String forRecognition(RecognitionService service) {
        return switch (service) {
            case GROQ -> this.groq;
            case OPENAI -> this.openAi;
            case CUSTOM -> this.custom;
            case LOCAL -> "";
        };
    }

    public String forLlm(LlmProvider provider) {
        return switch (provider) {
            case GROQ -> this.groq;
            case OPENAI -> this.openAi;
            case OPENROUTER -> this.openRouter;
            case OLLAMA, CUSTOM -> this.custom;
        };
    }

    public void setForRecognition(RecognitionService service, String key) {
        switch (service) {
            case GROQ -> this.groq = key;
            case OPENAI -> this.openAi = key;
            case CUSTOM -> this.custom = key;
            case LOCAL -> {
            }
        }
    }

    public void setForLlm(LlmProvider provider, String key) {
        switch (provider) {
            case GROQ -> this.groq = key;
            case OPENAI -> this.openAi = key;
            case OPENROUTER -> this.openRouter = key;
            case OLLAMA, CUSTOM -> this.custom = key;
        }
    }

    void sanitize() {
        this.groq = clean(this.groq);
        this.openAi = clean(this.openAi);
        this.deepL = clean(this.deepL);
        this.openRouter = clean(this.openRouter);
        this.custom = clean(this.custom);
    }

    private static String clean(String key) {
        return key == null ? "" : key.strip();
    }
}
