package org.sawiq.voicetranslate.translation;

import java.util.List;

/**
 * @param text           what the player said
 * @param sourceLanguage ISO 639-1 code, or empty when nobody knows yet
 * @param targetLanguage ISO 639-1 code of the reader's language
 * @param context        the same player's previous lines, oldest first, so
 *                       pronouns and half-sentences translate sensibly
 * @param glossary       Minecraft names in the text with their official
 *                       translations, so "nether" is not turned into "void"
 */
public record TranslationRequest(String text, String sourceLanguage, String targetLanguage, List<String> context,
                                 List<GlossaryTerm> glossary) {
    /** The text with the game's official names already in the target language, for translators without a glossary. */
    public String textWithGlossary() {
        return this.glossary.isEmpty() ? this.text : GameGlossary.substitute(this.text, this.glossary);
    }
}
