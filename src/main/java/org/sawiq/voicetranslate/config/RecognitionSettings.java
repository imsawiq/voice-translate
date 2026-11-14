package org.sawiq.voicetranslate.config;

/** Speech recognition settings. Mutable for Gson and the settings screen. */
public final class RecognitionSettings {
    /** Model sizes the local engine offers, smallest first; "auto" picks by hardware. */
    public static final String[] LOCAL_MODELS = {"auto", "small", "medium", "large-v3-turbo", "large-v3"};
    public static final String[] LOCAL_DEVICES = {"auto", "cuda", "cpu"};

    public RecognitionService service = RecognitionService.LOCAL;
    public String localModel = "auto";
    public String localDevice = "auto";
    /** Used for {@link RecognitionService#CUSTOM}; the cloud services have fixed addresses. */
    public String customUrl = RecognitionService.CUSTOM.defaultUrl();
    public String customModel = RecognitionService.CUSTOM.defaultModel();
    public String groqModel = RecognitionService.GROQ.defaultModel();
    public String openAiModel = RecognitionService.OPENAI.defaultModel();
    /**
     * ISO code every speaker is assumed to use, or "auto" to detect it per
     * player. Fixing it helps on a server where everyone speaks one language.
     */
    public String spokenLanguage = "auto";
    /** Silence that ends a sentence. */
    public int pauseMillis = 650;
    /** Decibels above a player's background noise that count as speech. */
    public int sensitivityDb = 9;

    public String modelFor(RecognitionService target) {
        return switch (target) {
            case GROQ -> this.groqModel;
            case OPENAI -> this.openAiModel;
            case CUSTOM -> this.customModel;
            case LOCAL -> this.localModel;
        };
    }

    void sanitize() {
        if (this.service == null) {
            this.service = RecognitionService.LOCAL;
        }
        this.localModel = oneOf(this.localModel, LOCAL_MODELS);
        this.localDevice = oneOf(this.localDevice, LOCAL_DEVICES);
        this.customUrl = orDefault(this.customUrl, RecognitionService.CUSTOM.defaultUrl());
        this.customModel = orDefault(this.customModel, RecognitionService.CUSTOM.defaultModel());
        this.groqModel = orDefault(this.groqModel, RecognitionService.GROQ.defaultModel());
        this.openAiModel = orDefault(this.openAiModel, RecognitionService.OPENAI.defaultModel());
        this.spokenLanguage = orDefault(this.spokenLanguage, "auto");
        this.pauseMillis = Math.clamp(this.pauseMillis, 300, 2_000);
        this.sensitivityDb = Math.clamp(this.sensitivityDb, 3, 24);
    }

    static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String oneOf(String value, String[] allowed) {
        for (String option : allowed) {
            if (option.equals(value)) {
                return value;
            }
        }
        return allowed[0];
    }
}
