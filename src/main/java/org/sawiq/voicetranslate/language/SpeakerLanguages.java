package org.sawiq.voicetranslate.language;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Remembers which language each player speaks.
 *
 * <p>Whisper guesses the language from the first seconds of every clip, and
 * on a two-word phrase it guesses wrong often enough to turn Russian into
 * "Bulgarian" or English into "Welsh". Once a player has been heard in one
 * language a few times with confidence, that language is passed to Whisper
 * as a hint, which makes short phrases far more accurate. Old evidence fades,
 * so a player who switches language is followed after a few sentences.</p>
 */
public final class SpeakerLanguages {
    private static final int MAX_SPEAKERS = 256;
    private static final double DECAY = 0.75;
    private static final double MIN_EVIDENCE = 1.6;
    private static final double MIN_SHARE = 0.7;
    /** Detections less sure than this are ignored: they are exactly the guesses this exists to override. */
    private static final double MIN_PROBABILITY = 0.6;

    private final Map<UUID, Map<String, Double>> evidence = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Map<String, Double>> eldest) {
            return size() > MAX_SPEAKERS;
        }
    };

    public synchronized void record(UUID speaker, String language, double probability) {
        if (language == null || language.isEmpty() || probability < MIN_PROBABILITY) {
            return;
        }
        Map<String, Double> scores = this.evidence.computeIfAbsent(speaker, id -> new HashMap<>());
        scores.replaceAll((code, score) -> score * DECAY);
        scores.merge(language, probability, Double::sum);
        scores.values().removeIf(score -> score < 0.05);
    }

    /** The language to tell Whisper this player speaks, when there is enough evidence. */
    public synchronized Optional<String> hintFor(UUID speaker) {
        Map<String, Double> scores = this.evidence.get(speaker);
        if (scores == null || scores.isEmpty()) {
            return Optional.empty();
        }
        double total = 0.0;
        Map.Entry<String, Double> best = null;
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            total += entry.getValue();
            if (best == null || entry.getValue() > best.getValue()) {
                best = entry;
            }
        }
        boolean isConfident = best.getValue() >= MIN_EVIDENCE && best.getValue() / total >= MIN_SHARE;
        return isConfident ? Optional.of(best.getKey()) : Optional.empty();
    }

    public synchronized void forget(UUID speaker) {
        this.evidence.remove(speaker);
    }

    public synchronized void clear() {
        this.evidence.clear();
    }
}
