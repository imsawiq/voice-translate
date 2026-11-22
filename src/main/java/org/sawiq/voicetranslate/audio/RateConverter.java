package org.sawiq.voicetranslate.audio;

/**
 * Brings a voice to the 48 kHz the {@link Downsampler} expects.
 *
 * <p>Simple Voice Chat always sends 48 kHz, but a Plasmo Voice server picks
 * its own rate, and 24 kHz is a common choice. Treated as 48 kHz, such a
 * voice would reach Whisper at half speed and an octave down, and nothing
 * would be recognised.</p>
 *
 * <p>Linear interpolation is enough here: everything above 7.2 kHz is cut by
 * the downsampler's filter right after, and the images interpolation leaves
 * sit above that. The position between samples carries over from frame to
 * frame, so there is no click at frame boundaries.</p>
 */
final class RateConverter {
    private int inputRate = Pcm.VOICE_SAMPLE_RATE;
    private double position;
    private short previous;

    short[] toVoiceRate(short[] input, int sampleRate) {
        if (sampleRate == Pcm.VOICE_SAMPLE_RATE || sampleRate <= 0 || input.length == 0) {
            return input;
        }
        if (sampleRate != this.inputRate) {
            this.inputRate = sampleRate;
            this.position = 0.0;
            this.previous = 0;
        }
        double step = sampleRate / (double) Pcm.VOICE_SAMPLE_RATE;
        // Every output position from here up to, but not including, the end of this frame.
        int count = (int) Math.ceil((input.length - this.position) / step);
        short[] output = new short[Math.max(0, count)];
        double cursor = this.position;
        for (int i = 0; i < output.length; i++) {
            int index = (int) Math.floor(cursor);
            double fraction = cursor - index;
            short left = index - 1 < 0 ? this.previous : input[index - 1];
            short right = input[Math.min(index, input.length - 1)];
            // Positions count from one sample before the frame, so the first
            // output blends the previous frame's last sample with this one's first.
            output[i] = (short) Math.round(left + (right - left) * fraction);
            cursor += step;
        }
        this.position = cursor - input.length;
        this.previous = input[input.length - 1];
        return output;
    }

    void reset() {
        this.position = 0.0;
        this.previous = 0;
    }
}
