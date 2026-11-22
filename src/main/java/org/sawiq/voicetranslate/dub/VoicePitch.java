package org.sawiq.voicetranslate.dub;

import java.util.Arrays;

/**
 * Tells a low voice from a high one, so a player is dubbed by a Piper voice
 * that at least matches theirs in that.
 *
 * <p>Plain autocorrelation over voiced 40 ms frames, on speech already cut
 * into clean sentences. The median pitch decides: most men speak below
 * about 165 Hz, most women above.</p>
 */
public final class VoicePitch {
    private static final int RATE = 8_000;
    private static final int FRAME = RATE / 25;
    private static final int MIN_LAG = RATE / 400;
    private static final int MAX_LAG = RATE / 60;
    private static final double LOW_VOICE_BELOW_HZ = 165.0;
    /** How alike a frame must be to itself one period later to count as voiced. */
    private static final double MIN_PERIODICITY = 0.5;
    private static final int MIN_VOICED_FRAMES = 10;

    private VoicePitch() {
    }

    /**
     * @param speech16k 16 kHz mono speech
     * @return whether it is a low voice; true when there is too little voiced speech to tell
     */
    public static boolean isLow(short[] speech16k) {
        double median = medianPitch(speech16k);
        return Double.isNaN(median) || median < LOW_VOICE_BELOW_HZ;
    }

    /** The median pitch in Hz of the voiced frames, or NaN when there are too few. */
    static double medianPitch(short[] speech16k) {
        float[] signal = halve(speech16k);
        double[] pitches = new double[signal.length / FRAME + 1];
        int count = 0;
        for (int start = 0; start + FRAME + MAX_LAG <= signal.length; start += FRAME) {
            double pitch = framePitch(signal, start);
            if (!Double.isNaN(pitch)) {
                pitches[count++] = pitch;
            }
        }
        if (count < MIN_VOICED_FRAMES) {
            return Double.NaN;
        }
        double[] voiced = Arrays.copyOf(pitches, count);
        Arrays.sort(voiced);
        return voiced[count / 2];
    }

    private static double framePitch(float[] signal, int start) {
        double energy = 0;
        for (int i = start; i < start + FRAME; i++) {
            energy += signal[i] * signal[i];
        }
        if (energy / FRAME < 1e-5) {
            return Double.NaN;
        }
        double best = 0;
        int bestLag = -1;
        for (int lag = MIN_LAG; lag <= MAX_LAG; lag++) {
            double product = 0;
            double shifted = 0;
            for (int i = start; i < start + FRAME; i++) {
                product += signal[i] * signal[i + lag];
                shifted += signal[i + lag] * signal[i + lag];
            }
            double periodicity = product / Math.sqrt(energy * shifted + 1e-12);
            if (periodicity > best) {
                best = periodicity;
                bestLag = lag;
            }
        }
        return best >= MIN_PERIODICITY ? (double) RATE / bestLag : Double.NaN;
    }

    /** 16 kHz to 8 kHz by averaging pairs, scaled to -1..1; pitch lives far below 4 kHz. */
    private static float[] halve(short[] speech16k) {
        float[] halved = new float[speech16k.length / 2];
        for (int i = 0; i < halved.length; i++) {
            halved[i] = (speech16k[2 * i] + speech16k[2 * i + 1]) / 65536f;
        }
        return halved;
    }
}
