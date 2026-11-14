package org.sawiq.voicetranslate.voice;

import java.util.UUID;
import org.sawiq.voicetranslate.audio.VoiceIntake;

/**
 * The meeting point between the voice mods and the translator.
 *
 * <p>The voice mods create the integration classes themselves (Simple Voice
 * Chat through its plugin entrypoint, Plasmo Voice through its addon loader),
 * so those cannot be handed the intake in a constructor. They deliver here
 * instead, and frames that arrive before the client has started, or after it
 * stopped, are simply dropped.</p>
 */
public final class VoiceBridge {
    private static volatile VoiceIntake intake;
    private static volatile UUID ownId;
    private static volatile boolean isSimpleVoiceChatHooked;
    private static volatile boolean isPlasmoVoiceHooked;

    private VoiceBridge() {
    }

    public static void connect(VoiceIntake target) {
        intake = target;
    }

    public static void disconnect() {
        intake = null;
    }

    /** Called on the voice mod's audio thread with another player's decoded voice. */
    public static void deliver(UUID speakerId, String speakerName, short[] mono, int sampleRate) {
        VoiceIntake target = intake;
        if (target != null) {
            target.accept(speakerId, speakerName, mono, sampleRate);
        }
    }

    /**
     * The player's own microphone, for checking the mod alone. The intake
     * drops it unless the player turned that on.
     */
    public static void deliverOwn(short[] mono, int sampleRate) {
        UUID self = ownId;
        if (self != null) {
            deliver(self, "", mono, sampleRate);
        }
    }

    /** Set on the game thread when the player joins or leaves a world. */
    public static void setOwnId(UUID id) {
        ownId = id;
    }

    public static void markSimpleVoiceChatHooked() {
        isSimpleVoiceChatHooked = true;
    }

    public static void markPlasmoVoiceHooked() {
        isPlasmoVoiceHooked = true;
    }

    /** Whether any voice mod is feeding audio in, for the settings screen. */
    public static boolean isAnyVoiceModHooked() {
        return isSimpleVoiceChatHooked || isPlasmoVoiceHooked;
    }

    public static boolean isSimpleVoiceChatHooked() {
        return isSimpleVoiceChatHooked;
    }

    public static boolean isPlasmoVoiceHooked() {
        return isPlasmoVoiceHooked;
    }
}
