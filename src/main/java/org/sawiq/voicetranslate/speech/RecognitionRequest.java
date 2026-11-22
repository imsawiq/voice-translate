package org.sawiq.voicetranslate.speech;

/**
 * @param samples      16 kHz mono PCM
 * @param languageHint ISO 639-1 code the speaker is known to use, or empty to let the model detect it
 * @param prompt       text that came just before, to keep names and spelling consistent; may be empty
 * @param isDraft      a growing draft of an unfinished sentence, where speed beats accuracy
 */
public record RecognitionRequest(short[] samples, String languageHint, String prompt, boolean isDraft) {
}
