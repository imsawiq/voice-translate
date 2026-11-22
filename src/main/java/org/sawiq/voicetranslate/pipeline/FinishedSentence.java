package org.sawiq.voicetranslate.pipeline;

import org.sawiq.voicetranslate.caption.Caption;

/**
 * A sentence the pipeline finished with.
 *
 * @param samples        what was said, 16 kHz mono, for copying the speaker's voice
 * @param targetLanguage what it was translated into
 */
public record FinishedSentence(Caption caption, short[] samples, String targetLanguage) {
}
