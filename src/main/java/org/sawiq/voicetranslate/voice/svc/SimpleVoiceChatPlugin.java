package org.sawiq.voicetranslate.voice.svc;

//? if neoforge
/*import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;*/
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import java.util.UUID;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.voice.VoiceBridge;

/**
 * Simple Voice Chat hands every voice it is about to play to its plugins.
 * Proximity voices arrive as entity sounds carrying the speaker's UUID, and
 * group calls as static sounds carrying the sender's. Locational sounds are
 * other mods' audio, such as music discs, and are left alone.
 */
//? if neoforge
/*@ForgeVoicechatPlugin*/
public final class SimpleVoiceChatPlugin implements VoicechatPlugin {
    /** Last, so the voice reaching us is the one the player hears. */
    private static final int PRIORITY = -100;
    private static final int SIMPLE_VOICE_CHAT_RATE = 48_000;

    @Override
    public String getPluginId() {
        return VoiceTranslate.NEOFORGE_MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, this::onEntitySound, PRIORITY);
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, this::onStaticSound, PRIORITY);
        registration.registerEvent(ClientSoundEvent.class, this::onOwnSound, PRIORITY);
        VoiceBridge.markSimpleVoiceChatHooked();
        VoiceTranslate.LOGGER.info("Listening to Simple Voice Chat");
    }

    private void onEntitySound(ClientReceiveSoundEvent.EntitySound event) {
        deliver(event, event.getEntityId() != null ? event.getEntityId() : event.getId());
    }

    private void onStaticSound(ClientReceiveSoundEvent.StaticSound event) {
        deliver(event, event.getId());
    }

    /** The player's own microphone as it is sent, once Simple Voice Chat decided they are talking. */
    private void onOwnSound(ClientSoundEvent event) {
        short[] audio = event.getRawAudio();
        if (audio == null) {
            return;
        }
        try {
            VoiceBridge.deliverOwn(audio, SIMPLE_VOICE_CHAT_RATE);
        } catch (RuntimeException exception) {
            VoiceTranslate.LOGGER.error("Could not take an own Simple Voice Chat frame", exception);
        }
    }

    private static void deliver(ClientReceiveSoundEvent event, UUID speakerId) {
        short[] audio = event.getRawAudio();
        if (audio == null) {
            return;
        }
        try {
            VoiceBridge.deliver(speakerId, "", audio, SIMPLE_VOICE_CHAT_RATE);
        } catch (RuntimeException exception) {
            // Thrown into Simple Voice Chat's audio thread, this would cut the
            // player's voice off for everyone listening.
            VoiceTranslate.LOGGER.error("Could not take a Simple Voice Chat frame", exception);
        }
    }
}
