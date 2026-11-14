package org.sawiq.voicetranslate.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Small conversions on 16-bit PCM. */
public final class Pcm {
    /** What the voice mods deliver. */
    public static final int VOICE_SAMPLE_RATE = 48_000;
    /** What speech recognition is given. */
    public static final int SPEECH_SAMPLE_RATE = 16_000;

    private Pcm() {
    }

    /** A whole 48 kHz recording at the 16 kHz speech recognition wants. */
    public static short[] toSpeechRate(short[] voice) {
        return new Downsampler().process(voice);
    }

    /** A copy scaled by {@code gain}, clipped to 16 bits. */
    public static short[] scaled(short[] samples, float gain) {
        short[] result = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            result[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(samples[i] * gain)));
        }
        return result;
    }

    /** Loudness of a frame in dBFS; silence is -90. */
    public static double rmsDb(short[] samples) {
        if (samples.length == 0) {
            return -90.0;
        }
        double sum = 0.0;
        for (short sample : samples) {
            sum += (double) sample * sample;
        }
        double rms = Math.sqrt(sum / samples.length) / 32768.0;
        return rms <= 1e-5 ? -90.0 : 20.0 * Math.log10(rms);
    }

    /** Averages interleaved stereo down to mono. */
    public static short[] downmixStereo(short[] interleaved) {
        short[] mono = new short[interleaved.length / 2];
        for (int i = 0; i < mono.length; i++) {
            mono[i] = (short) ((interleaved[2 * i] + interleaved[2 * i + 1]) / 2);
        }
        return mono;
    }

    public static byte[] toLittleEndianBytes(short[] samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asShortBuffer().put(samples);
        return buffer.array();
    }

    /** A 16 kHz mono 16-bit WAV file, the format every speech API accepts. */
    public static byte[] toWav(short[] samples, int sampleRate) {
        int dataLength = samples.length * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[] {'R', 'I', 'F', 'F'}).putInt(36 + dataLength);
        buffer.put(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        buffer.putInt(16).putShort((short) 1).putShort((short) 1);
        buffer.putInt(sampleRate).putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
        buffer.put(new byte[] {'d', 'a', 't', 'a'}).putInt(dataLength);
        buffer.asShortBuffer().put(samples);
        return buffer.array();
    }
}
