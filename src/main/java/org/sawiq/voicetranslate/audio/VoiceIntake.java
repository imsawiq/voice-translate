package org.sawiq.voicetranslate.audio;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.sawiq.voicetranslate.VoiceTranslate;

/**
 * Where both voice mod integrations hand over the voices they play, and where
 * those voices are cut into utterances, one segmenter per player.
 *
 * <p>The voice mods call in on their audio threads, so this does as little as
 * it can there: a copy, a downsample and a loudness check. Everything slow
 * happens later, on the translation pipeline's own threads.</p>
 */
public final class VoiceIntake implements AutoCloseable {
    private static final int MAX_SPEAKERS = 64;
    private static final long FORGET_SPEAKER_AFTER_MILLIS = 5 * 60_000L;
    private static final long TIMER_PERIOD_MILLIS = 100L;

    private final Map<UUID, UtteranceSegmenter> segmenters = new ConcurrentHashMap<>();
    private final UtteranceListener listener;
    private final Supplier<SegmenterSettings> settings;
    private final Predicate<UUID> isWanted;
    private final ScheduledExecutorService timer;
    private volatile Heard lastHeard;

    /**
     * @param isWanted decides, per speaker, whether their voice is worth
     *                 cutting at all; answering quickly matters, it runs on
     *                 the audio thread
     */
    public VoiceIntake(UtteranceListener listener, Supplier<SegmenterSettings> settings, Predicate<UUID> isWanted) {
        this.listener = listener;
        this.settings = settings;
        this.isWanted = isWanted;
        this.timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Voice Translate speech timer");
            thread.setDaemon(true);
            return thread;
        });
        this.timer.scheduleAtFixedRate(this::checkTimeouts, TIMER_PERIOD_MILLIS, TIMER_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * One decoded frame of a player's voice.
     *
     * @param speakerName the name if the voice mod knows it, else empty
     * @param samples     mono samples; not kept, so the caller may reuse the array
     * @param sampleRate  their rate: 48 kHz for Simple Voice Chat, the server's choice for Plasmo Voice
     */
    public void accept(UUID speakerId, String speakerName, short[] samples, int sampleRate) {
        if (speakerId == null || samples.length == 0 || !this.isWanted.test(speakerId)) {
            return;
        }
        this.lastHeard = new Heard(speakerId, speakerName, System.currentTimeMillis());
        UtteranceSegmenter segmenter = this.segmenters.get(speakerId);
        if (segmenter == null) {
            if (this.segmenters.size() >= MAX_SPEAKERS) {
                return;
            }
            segmenter = this.segmenters.computeIfAbsent(speakerId,
                    id -> new UtteranceSegmenter(id, this.listener, this.settings));
        }
        segmenter.acceptFrame(samples, sampleRate, speakerName, System.currentTimeMillis());
    }

    /** Who was heard last and when, so the settings can show that voices arrive at all; null before anyone. */
    public Heard lastHeard() {
        return this.lastHeard;
    }

    /**
     * @param speakerName as the voice mod gave it, possibly empty
     * @param at          epoch millis
     */
    public record Heard(UUID speakerId, String speakerName, long at) {
    }

    /** Forgets everyone, for leaving a server. */
    public void clear() {
        this.segmenters.clear();
    }

    @Override
    public void close() {
        this.timer.shutdownNow();
        clear();
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        try {
            this.segmenters.values().forEach(segmenter -> segmenter.checkTimeout(now));
            this.segmenters.values().removeIf(segmenter -> segmenter.isIdleSince(now, FORGET_SPEAKER_AFTER_MILLIS));
        } catch (RuntimeException exception) {
            // A scheduled task that throws is never run again, which would
            // leave every sentence after this one unfinished.
            VoiceTranslate.LOGGER.error("Speech timer failed", exception);
        }
    }
}
