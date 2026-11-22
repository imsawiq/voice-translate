package org.sawiq.voicetranslate.voice.plasmo;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.sawiq.voicetranslate.dub.DubPlayer;
import org.sawiq.voicetranslate.voice.VoiceBridge;
import su.plo.voice.api.client.audio.capture.ClientActivation;

/**
 * Fits translating the player's own speech into Plasmo Voice's capture loop,
 * the way the voice changer's AI voice fits in.
 *
 * <p>Plasmo Voice decides per activation and per 20 ms tick whether to send,
 * from the push-to-talk key or the voice level, and only then runs the
 * microphone filters on what it sends. A translated phrase is sent after the
 * player stops talking, and nothing is sent while they talk, so that
 * decision has to be this class's:</p>
 * <ol>
 *     <li>at the start of each tick ({@link #beginTick}) the translator gets
 *     the raw frame, if the player was talking, and says what to send;</li>
 *     <li>each activation's own decision is rewritten ({@link #translate}) to
 *     send exactly when there is a frame;</li>
 *     <li>the microphone filter swaps the frame in ({@link #takeOutput}).</li>
 * </ol>
 *
 * <p>Whether the player is talking is read from the previous tick's
 * decisions, since this tick's are made after the frame is handed over. All
 * of this runs on Plasmo Voice's capture thread.</p>
 */
final class PlasmoOwnSpeechCapture {
    private static final int FRAME = DubPlayer.FRAME_SAMPLES;

    private final Set<UUID> speakingActivations = new HashSet<>();
    private final Set<UUID> sendingActivations = new HashSet<>();
    private boolean wasTalkingLastTick;
    private boolean isTalkingThisTick;
    private boolean isBypassed = true;
    private short[] output;

    /** Start of a capture tick, before any activation decided anything. */
    void beginTick(short[] samples) {
        this.wasTalkingLastTick = this.isTalkingThisTick;
        this.isTalkingThisTick = false;
        int channels = samples.length / FRAME;
        // Translation works at 48 kHz in 20 ms frames, Plasmo Voice's default;
        // a server set to another rate gets the player's voice as it is.
        this.isBypassed = !VoiceBridge.isTranslatingOwnSpeech()
                || samples.length % FRAME != 0 || channels < 1 || channels > 2;
        if (this.isBypassed) {
            this.output = null;
            this.speakingActivations.clear();
            this.sendingActivations.clear();
            return;
        }
        short[] spoken = this.wasTalkingLastTick ? toMono(samples, channels) : null;
        this.output = VoiceBridge.processOwnMicTick(spoken);
        if (this.output == null && !this.wasTalkingLastTick && !VoiceBridge.isOwnSpeechBusy()) {
            this.speakingActivations.clear();
        }
    }

    /**
     * Rewrites one activation's decision for this tick. Inheriting activations
     * follow their parent's rewritten result; the others send while there is
     * a translated frame, if the player used them for the phrase.
     */
    ClientActivation.Result translate(ClientActivation activation, ClientActivation.Result natural) {
        if (this.isBypassed || activation.getType() == ClientActivation.Type.INHERIT) {
            return natural;
        }
        UUID id = activation.getId();
        if (natural == ClientActivation.Result.ACTIVATED) {
            this.isTalkingThisTick = true;
            this.speakingActivations.add(id);
        }
        if (this.output != null && this.speakingActivations.contains(id)) {
            this.sendingActivations.add(id);
            return ClientActivation.Result.ACTIVATED;
        }
        if (this.sendingActivations.remove(id)) {
            return ClientActivation.Result.END;
        }
        return ClientActivation.Result.NOT_ACTIVATED;
    }

    /**
     * Writes this tick's frame into every channel of what Plasmo Voice sends.
     *
     * @return false when own speech is not being translated this tick
     */
    boolean takeOutput(short[] samples, int channels) {
        if (this.isBypassed) {
            return false;
        }
        short[] frame = this.output != null ? this.output : new short[FRAME];
        int frames = Math.min(FRAME, samples.length / Math.max(1, channels));
        for (int i = 0; i < frames; i++) {
            for (int channel = 0; channel < channels; channel++) {
                samples[i * channels + channel] = frame[i];
            }
        }
        return true;
    }

    private static short[] toMono(short[] samples, int channels) {
        if (channels == 1) {
            return samples.clone();
        }
        short[] mono = new short[samples.length / channels];
        for (int i = 0; i < mono.length; i++) {
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                sum += samples[i * channels + channel];
            }
            mono[i] = (short) (sum / channels);
        }
        return mono;
    }
}
