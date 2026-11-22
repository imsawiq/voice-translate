package org.sawiq.voicetranslate.audio;

/**
 * How speech is cut into utterances.
 *
 * @param pauseMillis       silence that ends a sentence
 * @param maxSeconds        longest utterance; longer speech is split at its quietest moment
 * @param draftIntervalMillis how often a growing draft is sent while someone talks; 0 for none
 * @param sensitivityDb     how far above the background noise a frame must be to count as speech
 */
public record SegmenterSettings(int pauseMillis, int maxSeconds, int draftIntervalMillis, int sensitivityDb) {
    public static final SegmenterSettings DEFAULT = new SegmenterSettings(650, 12, 1200, 9);

    public boolean wantsDrafts() {
        return this.draftIntervalMillis > 0;
    }
}
