package org.sawiq.voicetranslate.voice;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import org.sawiq.voicetranslate.dub.DubPlayer;
import org.sawiq.voicetranslate.dub.OwnSpeechTranslator;
import org.sawiq.voicetranslate.VoiceTranslate;
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
    private static volatile ToDoubleFunction<UUID> originalGain = speaker -> 1.0;
    private static volatile DubPlayer dubPlayer;
    private static volatile OwnSpeechTranslator ownSpeech;
    private static final List<Runnable> TICKERS = new CopyOnWriteArrayList<>();
    private static volatile Function<UUID, Object> entities = id -> null;
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

    /**
     * How loud a player's own voice should be played, 0..1: lower while their
     * speech is being dubbed, so the translation is what is heard.
     */
    public static float originalGain(UUID speaker) {
        return speaker == null ? 1f : (float) originalGain.applyAsDouble(speaker);
    }

    public static void setOriginalGain(ToDoubleFunction<UUID> gain) {
        originalGain = gain;
    }

    public static void connectDubbing(DubPlayer player, OwnSpeechTranslator translator) {
        dubPlayer = player;
        ownSpeech = translator;
    }

    /** The voice mod in use says where spoken translations are played; null when it goes away. */
    public static void setDubOutput(DubPlayer.Output output) {
        DubPlayer player = dubPlayer;
        if (player != null) {
            player.setOutput(output);
        }
    }

    /** Whether the player's speech is being translated before it is sent. */
    public static boolean isTranslatingOwnSpeech() {
        OwnSpeechTranslator translator = ownSpeech;
        return translator != null && translator.isEnabled();
    }

    public static boolean isOwnSpeechBusy() {
        OwnSpeechTranslator translator = ownSpeech;
        return translator != null && translator.isBusy();
    }

    /**
     * One 20 ms microphone tick, on the voice mod's capture thread.
     *
     * @param spoken the 48 kHz mono frame the player said, or null while silent
     * @return what to send: the frame unchanged while own speech is not translated
     */
    public static short[] processOwnMicTick(short[] spoken) {
        OwnSpeechTranslator translator = ownSpeech;
        if (translator == null || !translator.isEnabled()) {
            return spoken;
        }
        return translator.onTick(spoken);
    }

    /** The client's player entity for a speaker, or null; for placing their dub. */
    public static Object entityOf(UUID speaker) {
        return entities.apply(speaker);
    }

    public static void setEntityLookup(Function<UUID, Object> lookup) {
        entities = lookup;
    }

    /** Work a voice mod integration does on the game thread every tick. */
    public static void addTicker(Runnable ticker) {
        TICKERS.add(ticker);
    }

    /** Game thread, every client tick. */
    public static void tick() {
        for (Runnable ticker : TICKERS) {
            try {
                ticker.run();
            } catch (RuntimeException exception) {
                VoiceTranslate.LOGGER.error("A voice mod hook failed", exception);
            }
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
