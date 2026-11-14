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
import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent;
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
    private static final int DEFAULT_SAMPLE_RATE = 48_000;

    @InjectPlasmoVoice
    private PlasmoVoiceClient voiceClient;

    /** Hands the addon to Plasmo Voice, which initialises it once its client is up. */
    public static void register() {
        PlasmoVoiceClient.getAddonsLoader().load(new PlasmoVoiceAddon());
    }

    @Override
    public void onAddonInitialize() {
        this.voiceClient.getEventBus().register(this, AudioSourceWriteEvent.class, EventPriority.LOWEST, this::onSourceWrite);
        this.voiceClient.getEventBus().register(this, AudioCaptureProcessedEvent.class, EventPriority.LOWEST, this::onOwnCapture);
        VoiceBridge.markPlasmoVoiceHooked();
        try {
            new PlasmoSettings().register(this.voiceClient.getAddonConfig(this));
        } catch (RuntimeException exception) {
            // Translation works without the settings section; only the menu entry is lost.
            VoiceTranslate.LOGGER.error("Could not add Voice Translate to Plasmo Voice's settings", exception);
        }
        VoiceTranslate.LOGGER.info("Listening to Plasmo Voice");
    }

    /** The player's own microphone after Plasmo Voice's noise suppression, for checking the mod alone. */
    private void onOwnCapture(AudioCaptureProcessedEvent event) {
        try {
            short[] mono = event.getProcessed().getMono();
            if (mono != null && mono.length > 0) {
                VoiceBridge.deliverOwn(mono, sampleRate());
            }
        } catch (RuntimeException exception) {
            VoiceTranslate.LOGGER.error("Could not take an own Plasmo Voice frame", exception);
        }
    }

    /** The server sets Plasmo Voice's sample rate; 48 kHz until it has said. */
    private int sampleRate() {
        return this.voiceClient.getServerInfo()
                .map(info -> info.getVoiceInfo().getCaptureInfo().getSampleRate())
                .orElse(DEFAULT_SAMPLE_RATE);
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
            VoiceBridge.deliver(speakerId, speakerName, info.isStereo() ? Pcm.downmixStereo(samples) : samples, sampleRate());
        } catch (RuntimeException exception) {
            // Thrown here, this would break Plasmo Voice's playback of the source.
            VoiceTranslate.LOGGER.error("Could not take a Plasmo Voice frame", exception);
        }
    }
}
