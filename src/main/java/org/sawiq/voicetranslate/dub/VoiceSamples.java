package org.sawiq.voicetranslate.dub;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.sawiq.voicetranslate.audio.Pcm;

/**
 * Collects a few seconds of each player's speech, which the voice engine
 * copies their voice from.
 *
 * <p>Pieces are taken from sentences that were recognised cleanly, so the
 * sample is speech and not keyboard noise. Once enough is gathered the
 * sample stays as it is: the engine prepares a voice once per sample, and a
 * sample that kept changing would make it do that work for every sentence.</p>
 */
public final class VoiceSamples {
    private static final int RATE = Pcm.SPEECH_SAMPLE_RATE;
    private static final int TARGET_SAMPLES = 9 * RATE;
    /** Below this the copied voice is too unsure to be better than the engine's own. */
    private static final int USABLE_SAMPLES = 3 * RATE;
    private static final int MIN_PIECE_SAMPLES = RATE * 3 / 2;
    private static final int GAP_SAMPLES = RATE / 6;
    private static final int MAX_SPEAKERS = 64;

    private record Collected(short[] samples, int version) {
    }

    private final Map<UUID, Collected> collected = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Collected> eldest) {
            return size() > MAX_SPEAKERS;
        }
    };

    /** A cleanly recognised sentence of this speaker, 16 kHz mono. */
    public synchronized void offer(UUID speaker, short[] sentence) {
        if (sentence.length < MIN_PIECE_SAMPLES) {
            return;
        }
        Collected current = this.collected.get(speaker);
        int have = current == null ? 0 : current.samples().length;
        if (have >= TARGET_SAMPLES) {
            return;
        }
        int room = TARGET_SAMPLES - have;
        int gap = have == 0 ? 0 : GAP_SAMPLES;
        int take = Math.min(sentence.length, Math.max(0, room - gap));
        if (take < MIN_PIECE_SAMPLES / 2) {
            return;
        }
        short[] grown = current == null ? new short[0] : current.samples();
        grown = Arrays.copyOf(grown, have + gap + take);
        System.arraycopy(sentence, 0, grown, have + gap, take);
        int version = current == null ? 1 : current.version() + 1;
        this.collected.put(speaker, new Collected(grown, version));
    }

    /** The sample to copy this speaker's voice from, or null while there is too little of them yet. */
    public synchronized VoiceSample sampleFor(UUID speaker) {
        Collected current = this.collected.get(speaker);
        if (current == null || current.samples().length < USABLE_SAMPLES) {
            return null;
        }
        return new VoiceSample(speaker + "#" + current.version(), current.samples());
    }

    public synchronized void clear() {
        this.collected.clear();
    }
}
