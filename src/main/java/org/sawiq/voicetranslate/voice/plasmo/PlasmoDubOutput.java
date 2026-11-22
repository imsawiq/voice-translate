package org.sawiq.voicetranslate.voice.plasmo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.dub.DubPlayer;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.device.DeviceException;
import su.plo.voice.api.client.audio.source.LoopbackSource;

/**
 * Plays spoken translations through Plasmo Voice's own output device, on a
 * loopback source per speaker, so they mix with the voices and follow
 * Plasmo Voice's output device and volume.
 *
 * <p>Sources stay open between sentences: opening one takes an OpenAL
 * source, and a speaker usually says more than one sentence. The least
 * recently used are closed past a limit.</p>
 */
final class PlasmoDubOutput implements DubPlayer.Output {
    private static final int MAX_SOURCES = 8;

    private final PlasmoVoiceClient voiceClient;
    private final Map<UUID, LoopbackSource> sources = new LinkedHashMap<>(16, 0.75f, true);

    PlasmoDubOutput(PlasmoVoiceClient voiceClient) {
        this.voiceClient = voiceClient;
    }

    @Override
    public synchronized void play(UUID speaker, short[] frame) {
        LoopbackSource source = this.sources.get(speaker);
        if (source == null || source.isClosed()) {
            source = open();
            if (source == null) {
                return;
            }
            this.sources.put(speaker, source);
            evictOldest();
        }
        source.write(frame);
    }

    @Override
    public void finish(UUID speaker) {
        // Kept open for the speaker's next sentence.
    }

    synchronized void closeAll() {
        this.sources.values().forEach(LoopbackSource::close);
        this.sources.clear();
    }

    private LoopbackSource open() {
        try {
            LoopbackSource source = this.voiceClient.getSourceManager().createLoopbackSource(true);
            source.initialize(false);
            return source;
        } catch (DeviceException | RuntimeException exception) {
            VoiceTranslate.LOGGER.warn("Could not open a Plasmo Voice source for a spoken translation: {}", exception.getMessage());
            return null;
        }
    }

    private void evictOldest() {
        while (this.sources.size() > MAX_SOURCES) {
            UUID oldest = this.sources.keySet().iterator().next();
            this.sources.remove(oldest).close();
        }
    }
}
