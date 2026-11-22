package org.sawiq.voicetranslate.config;

/** Translation settings. Mutable for Gson and the settings screen. */
public final class TranslationSettings {
    public TranslationService service = TranslationService.GOOGLE;
    /** ISO code subtitles are translated into, or "auto" for the game's language. */
    public String targetLanguage = "auto";
    /** Subtitles for speech already in the target language, untranslated. */
    public boolean showUntranslated = true;
    public LlmProvider llmProvider = LlmProvider.GROQ;
    public String llmUrl = LlmProvider.GROQ.defaultUrl();
    public String llmModel = LlmProvider.GROQ.defaultModel();

    /** Switches provider and resets the address and model to that provider's. */
    public void selectLlmProvider(LlmProvider provider) {
        this.llmProvider = provider;
        this.llmUrl = provider.defaultUrl();
        this.llmModel = provider.defaultModel();
    }

    void sanitize() {
        if (this.service == null) {
            this.service = TranslationService.GOOGLE;
        }
        if (this.llmProvider == null) {
            this.llmProvider = LlmProvider.GROQ;
        }
        this.targetLanguage = RecognitionSettings.orDefault(this.targetLanguage, "auto");
        this.llmUrl = RecognitionSettings.orDefault(this.llmUrl, this.llmProvider.defaultUrl());
        this.llmModel = this.llmModel == null ? this.llmProvider.defaultModel() : this.llmModel.strip();
    }
}
