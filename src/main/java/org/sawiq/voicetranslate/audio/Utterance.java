package org.sawiq.voicetranslate.audio;

import java.util.UUID;

/**
 * One stretch of a player's speech, ready for recognition.
 *
 * <p>While someone is still talking the same utterance is sent several times
 * as a growing draft ({@code isFinal == false}), so the subtitle can follow
 * along; the last one, sent when they pause, is final and replaces the
 * drafts.</p>
 *
 * @param id         shared by the drafts and the final version of one utterance
 * @param speakerId  the player who spoke
 * @param speakerName their name if the voice mod told us, otherwise empty
 * @param samples    16 kHz mono PCM
 * @param startedAt  epoch millis of the first voiced frame
 */
public record Utterance(long id, UUID speakerId, String speakerName, short[] samples, long startedAt, boolean isFinal) {
    public double seconds() {
        return this.samples.length / (double) Downsampler.OUTPUT_RATE;
    }
}
