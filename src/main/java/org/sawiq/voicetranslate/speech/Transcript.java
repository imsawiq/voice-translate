package org.sawiq.voicetranslate.speech;

import java.util.List;

/**
 * What the recognizer heard.
 *
 * @param language            ISO 639-1 code of the detected or hinted language; empty if unknown
 * @param languageProbability how sure the model is of the language, 0..1; 1 when it was given
 * @param segments            per-segment scores where the service reports them, else empty
 */
public record Transcript(String text, String language, double languageProbability, List<Segment> segments) {
    /**
     * Whisper's own confidence in one stretch of the text.
     *
     * @param averageLogProbability mean token log probability; below about -1 is a guess
     * @param noSpeechProbability   chance the stretch held no speech at all
     * @param compressionRatio      how repetitive the text is; above 2.4 is a loop
     */
    public record Segment(String text, double averageLogProbability, double noSpeechProbability, double compressionRatio) {
    }

    public static Transcript empty() {
        return new Transcript("", "", 0.0, List.of());
    }
}
