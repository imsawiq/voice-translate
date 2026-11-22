package org.sawiq.voicetranslate.voice.svc;

import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.ClientAudioChannel;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.dub.DubPlayer;

/**
 * Plays spoken translations through Simple Voice Chat, from the speaker's
 * position like their voice, under a volume slider of its own in Simple
 * Voice Chat's volume settings.
 *
 * <p>A speaker who is not in view, such as someone in a group call far away,
 * is played without a position, the way Simple Voice Chat plays group calls.</p>
 */
final class SimpleVoiceChatDubOutput implements DubPlayer.Output {
    private static final String CATEGORY = "voice_translate";

    private final VoicechatClientApi api;
    private final Function<UUID, Object> entities;
    private final Map<UUID, ClientAudioChannel> channels = new ConcurrentHashMap<>();

    /** @param entities the player entity of a speaker, or null when they are not loaded */
    SimpleVoiceChatDubOutput(VoicechatClientApi api, Function<UUID, Object> entities) {
        this.api = api;
        this.entities = entities;
        try {
            VolumeCategory category = api.volumeCategoryBuilder()
                    .setId(CATEGORY)
                    .setName("Voice Translate")
                    .setNameTranslationKey("voice-translate.svc.category")
                    .setDescription("Spoken translations of other players")
                    .setDescriptionTranslationKey("voice-translate.svc.category.desc")
                    .build();
            api.registerClientVolumeCategory(category);
        } catch (RuntimeException exception) {
            VoiceTranslate.LOGGER.warn("Could not add the Voice Translate volume slider to Simple Voice Chat", exception);
        }
    }

    @Override
    public void play(UUID speaker, short[] frame) {
        ClientAudioChannel channel = this.channels.computeIfAbsent(speaker, this::openChannel);
        channel.play(frame);
    }

    @Override
    public void finish(UUID speaker) {
        // A new sentence gets a fresh channel, placed where the speaker is by then.
        this.channels.remove(speaker);
    }

    private ClientAudioChannel openChannel(UUID speaker) {
        Object entity = this.entities.apply(speaker);
        ClientAudioChannel channel = entity != null
                ? this.api.createEntityAudioChannel(UUID.randomUUID(), this.api.fromEntity(entity))
                : this.api.createStaticAudioChannel(UUID.randomUUID());
        channel.setCategory(CATEGORY);
        return channel;
    }
}
