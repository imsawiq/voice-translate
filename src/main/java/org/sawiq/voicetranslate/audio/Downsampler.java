package org.sawiq.voicetranslate.audio;

import java.util.Arrays;

/**
 * Turns the voice mods' 48 kHz audio into the 16 kHz Whisper listens to, one
 * frame at a time.
 *
 * <p>Dropping two of every three samples would fold everything above 8 kHz
 * back into the speech band as noise, so the signal goes through a windowed
 * sinc low-pass first. The filter keeps its history between frames, which is
 * why each speaker needs an instance of their own.</p>
 */
final class Downsampler {
    static final int INPUT_RATE = Pcm.VOICE_SAMPLE_RATE;
    static final int OUTPUT_RATE = Pcm.SPEECH_SAMPLE_RATE;
    private static final int FACTOR = INPUT_RATE / OUTPUT_RATE;
    private static final int TAPS = 63;
    private static final double CUTOFF_HZ = 7_200.0;
    private static final float[] KERNEL = buildKernel();

    private final float[] history = new float[TAPS - 1];
    /** Where the next output sample falls, counted in input samples from the start of the next frame. */
    private int phase;

    /**
     * @param input 48 kHz mono samples
     * @return the 16 kHz samples this frame completes
     */
    short[] process(short[] input) {
        int total = this.history.length + input.length;
        float[] window = new float[total];
        System.arraycopy(this.history, 0, window, 0, this.history.length);
        for (int i = 0; i < input.length; i++) {
            window[this.history.length + i] = input[i];
        }

        int count = 0;
        short[] output = new short[input.length / FACTOR + 1];
        int position = this.phase;
        while (position < input.length) {
            output[count++] = filteredSampleAt(window, position + this.history.length);
            position += FACTOR;
        }
        this.phase = position - input.length;

        System.arraycopy(window, total - this.history.length, this.history, 0, this.history.length);
        return count == output.length ? output : Arrays.copyOf(output, count);
    }

    void reset() {
        Arrays.fill(this.history, 0f);
        this.phase = 0;
    }

    /** Convolves the kernel ending at {@code end}, the sample being produced. */
    private static short filteredSampleAt(float[] window, int end) {
        double sum = 0.0;
        int start = end - (TAPS - 1);
        for (int tap = 0; tap < TAPS; tap++) {
            sum += window[start + tap] * KERNEL[tap];
        }
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sum)));
    }

    /** A Blackman-windowed sinc, normalised to unity gain at DC. */
    private static float[] buildKernel() {
        float[] kernel = new float[TAPS];
        double normalizedCutoff = CUTOFF_HZ / INPUT_RATE;
        int middle = (TAPS - 1) / 2;
        double sum = 0.0;
        for (int i = 0; i < TAPS; i++) {
            int offset = i - middle;
            double sinc = offset == 0
                    ? 2 * normalizedCutoff
                    : Math.sin(2 * Math.PI * normalizedCutoff * offset) / (Math.PI * offset);
            double blackman = 0.42
                    - 0.5 * Math.cos(2 * Math.PI * i / (TAPS - 1))
                    + 0.08 * Math.cos(4 * Math.PI * i / (TAPS - 1));
            kernel[i] = (float) (sinc * blackman);
            sum += kernel[i];
        }
        for (int i = 0; i < TAPS; i++) {
            kernel[i] /= (float) sum;
        }
        return kernel;
    }
}
