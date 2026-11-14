package org.sawiq.voicetranslate.translation;

/**
 * @param detectedLanguage the source language the translator saw, or empty
 *                         when it did not say
 */
public record TranslationResult(String text, String detectedLanguage) {
}
