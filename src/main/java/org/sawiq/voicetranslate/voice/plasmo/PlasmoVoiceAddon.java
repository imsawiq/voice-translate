package org.sawiq.voicetranslate.voice.plasmo;

import java.util.UUID;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.audio.Pcm;
import org.sawiq.voicetranslate.voice.VoiceBridge;
import su.plo.slib.api.entity.player.McGameProfile;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.event.audio.source.AudioSourceWriteEvent;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.proto.data.audio.source.DirectSourceInfo;
import su.plo.voice.proto.data.audio.source.PlayerSourceInfo;
import su.plo.voice.proto.data.audio.source.SourceInfo;

/**
 * A client addon for Plasmo Voice. Every decoded frame Plasmo Voice is about
 * to play passes through {@link AudioSourceWriteEvent}; frames from player
 * sources (proximity) and direct sources with a sender (groups and other
 * addons' calls) are a player speaking. Static and entity sources are other
 * addons' audio and are left alone.
 */
@Addon(
        id = "voice-translate",
        name = "Voice Translate",
        scope = AddonLoaderScope.CLIENT,
        version = "1.0.0",
        authors = {"sawiq_"}
)
public final class PlasmoVoiceAddon implements AddonInitializer {
    @InjectPlasmoVoice
    private PlasmoVoiceClient voiceClient;

    /** Hands the addon to Plasmo Voice, which initialises it once its client is up. */
    public static void register() {
        PlasmoVoiceClient.getAddonsLoader().load(new PlasmoVoiceAddon());
    }

    @Override
    public void onAddonInitialize() {
        this.voiceClient.getEventBus().register(this, AudioSourceWriteEvent.class, EventPriority.LOWEST, this::onSourceWrite);
        VoiceBridge.markPlasmoVoiceHooked();
        VoiceTranslate.LOGGER.info("Listening to Plasmo Voice");
    }

    private void onSourceWrite(AudioSourceWriteEvent event) {
        try {
            SourceInfo info = event.getSource().getSourceInfo();
            short[] samples = event.getSamples();
            if (info == null || samples == null || samples.length == 0) {
                return;
            }
            UUID speakerId;
            String speakerName;
            if (info instanceof PlayerSourceInfo player && player.getPlayerInfo() != null) {
                speakerId = player.getPlayerInfo().getPlayerId();
                speakerName = player.getPlayerInfo().getPlayerNick();
            } else if (info instanceof DirectSourceInfo direct && direct.getSender() != null) {
                McGameProfile sender = direct.getSender();
                speakerId = sender.getId();
                speakerName = sender.getName();
            } else {
                return;
            }
            VoiceBridge.deliver(speakerId, speakerName, info.isStereo() ? Pcm.downmixStereo(samples) : samples);
        } catch (RuntimeException exception) {
            // Thrown here, this would break Plasmo Voice's playback of the source.
            VoiceTranslate.LOGGER.error("Could not take a Plasmo Voice frame", exception);
        }
    }
}
