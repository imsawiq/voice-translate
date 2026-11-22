package org.sawiq.voicetranslate.config;

/** Who translates the recognised text. */
public enum TranslationService {
    /** No key needed. */
    GOOGLE,
    /** The most natural for European languages; a free key covers a lot. */
    DEEPL,
    /** A language model: understands slang, game terms and context best. */
    LLM
}
