package org.sawiq.voicetranslate.client;

import org.sawiq.voicetranslate.config.RecognitionService;
import org.sawiq.voicetranslate.config.TranslateConfig;
import org.sawiq.voicetranslate.engine.LocalEngine;
import org.sawiq.voicetranslate.language.Language;
import org.sawiq.voicetranslate.pipeline.PipelineServices;
import org.sawiq.voicetranslate.speech.CloudRecognizer;
import org.sawiq.voicetranslate.speech.LocalWhisperRecognizer;
import org.sawiq.voicetranslate.speech.SpeechRecognizer;
import org.sawiq.voicetranslate.translation.CachingTranslator;
import org.sawiq.voicetranslate.translation.DeepLTranslator;
import org.sawiq.voicetranslate.translation.FallbackTranslator;
import org.sawiq.voicetranslate.translation.GameGlossary;
import org.sawiq.voicetranslate.translation.GoogleTranslator;
import org.sawiq.voicetranslate.translation.LlmTranslator;
import org.sawiq.voicetranslate.translation.Translator;

/** Builds the recognizer and translator the settings describe. */
final class ServiceFactory {
    private ServiceFactory() {
    }

    /**
     * @param gameLanguage Minecraft's language code, used when the target is "auto"
     * @param glossary     the game's names in the target language
     */
    static PipelineServices create(TranslateConfig config, LocalEngine engine, String gameLanguage, GameGlossary glossary) {
        SpeechRecognizer recognizer = recognizer(config, engine);
        String target = targetLanguage(config, gameLanguage);
        String spoken = "auto".equals(config.recognition.spokenLanguage) ? "" : config.recognition.spokenLanguage;
        boolean wantsDrafts = config.display.showDrafts && recognizer.isCheapPerRequest();
        return new PipelineServices(recognizer, translator(config), target, spoken,
                config.translation.showUntranslated, wantsDrafts, glossary);
    }

    /** The ISO code subtitles are translated into: the chosen one, or the game's own language. */
    static String targetLanguage(TranslateConfig config, String gameLanguage) {
        return "auto".equals(config.translation.targetLanguage)
                ? Language.fromGameLanguage(gameLanguage).code()
                : config.translation.targetLanguage;
    }

    private static SpeechRecognizer recognizer(TranslateConfig config, LocalEngine engine) {
        RecognitionService service = config.recognition.service;
        return switch (service) {
            case LOCAL -> new LocalWhisperRecognizer(engine::address);
            case GROQ -> new CloudRecognizer("Groq", service.defaultUrl(), config.keys.groq, config.recognition.groqModel);
            case OPENAI -> new CloudRecognizer("OpenAI", service.defaultUrl(), config.keys.openAi, config.recognition.openAiModel);
            case CUSTOM -> new CloudRecognizer("The speech server", config.recognition.customUrl,
                    config.keys.custom, config.recognition.customModel);
        };
    }

    private static Translator translator(TranslateConfig config) {
        GoogleTranslator google = new GoogleTranslator();
        Translator chosen = switch (config.translation.service) {
            case GOOGLE -> google;
            case DEEPL -> new FallbackTranslator(new DeepLTranslator(config.keys.deepL), google);
            case LLM -> new FallbackTranslator(new LlmTranslator(
                    providerName(config), config.translation.llmUrl,
                    config.keys.forLlm(config.translation.llmProvider), config.translation.llmModel), google);
        };
        return new CachingTranslator(chosen);
    }

    private static String providerName(TranslateConfig config) {
        return switch (config.translation.llmProvider) {
            case OPENAI -> "OpenAI";
            case GROQ -> "Groq";
            case OPENROUTER -> "OpenRouter";
            case OLLAMA -> "Ollama";
            case CUSTOM -> "The language model";
        };
    }
}
