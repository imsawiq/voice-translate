package org.sawiq.voicetranslate.pipeline;

import org.sawiq.voicetranslate.speech.SpeechRecognizer;
import org.sawiq.voicetranslate.translation.GameGlossary;
import org.sawiq.voicetranslate.translation.Translator;

/**
 * The recognizer and translator the pipeline currently uses, swapped as a
 * pair when the player changes settings so a job never mixes old and new.
 *
 * @param targetLanguage    ISO 639-1 code subtitles are translated into
 * @param fixedSpokenLanguage ISO code all speakers are assumed to use, or empty to detect per speaker
 * @param showUntranslated  whether speech already in the target language still gets a subtitle
 * @param wantsDrafts       whether growing drafts of unfinished sentences are recognised
 * @param glossary          the game's names in the target language, for English speech
 */
public record PipelineServices(
        SpeechRecognizer recognizer,
        Translator translator,
        String targetLanguage,
        String fixedSpokenLanguage,
        boolean showUntranslated,
        boolean wantsDrafts,
        GameGlossary glossary
) {
}
