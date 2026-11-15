package org.sawiq.voicetranslate.dub;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.caption.Caption;
import org.sawiq.voicetranslate.net.ServiceException;

/**
 * Speaks other players' translated sentences from where they stand.
 *
 * <p>One sentence at a time, in order. When it falls behind, the
 * oldest waiting sentence is dropped; a translation spoken half a minute
 * after the fact helps nobody.</p>
 */
public final class DubbingService implements AutoCloseable {
    private static final int MAX_WAITING = 4;
    private static final long STALE_AFTER_MILLIS = 15_000L;

    /**
     * @param isEnabled whether dubbing is on
     * @param volume    0..2, the dub's loudness
     */
    public record Settings(boolean isEnabled, float volume) {
    }

    private final DubVoices voices;
    private final DubPlayer player;
    private final Supplier<Settings> settings;
    private final Consumer<ServiceException> onFailure;
    private final ThreadPoolExecutor worker;

    public DubbingService(DubVoices voices, DubPlayer player, Supplier<Settings> settings,
                          Consumer<ServiceException> onFailure) {
        this.voices = voices;
        this.player = player;
        this.settings = settings;
        this.onFailure = onFailure;
        this.worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(MAX_WAITING),
                runnable -> {
                    Thread thread = new Thread(runnable, "Voice Translate voice");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.DiscardOldestPolicy());
        this.worker.allowCoreThreadTimeOut(true);
    }

    /**
     * A finished, translated sentence of another player. Called on a pipeline
     * worker; queues the speaking and returns.
     *
     * @param language the language it was translated into
     */
    public void onSentence(Caption caption, String language) {
        Settings current = this.settings.get();
        if (!current.isEnabled() || !caption.isTranslated() || !this.voices.speaks(language)
                || !this.player.hasOutput()) {
            return;
        }
        this.worker.execute(() -> speak(caption, language));
    }

    private void speak(Caption caption, String language) {
        Settings current = this.settings.get();
        if (!current.isEnabled() || System.currentTimeMillis() - caption.updatedAt() > STALE_AFTER_MILLIS) {
            return;
        }
        UUID speaker = caption.speakerId();
        try {
            for (String sentence : Sentences.split(caption.translation())) {
                short[] pcm = this.voices.speak(sentence, language, speaker);
                if (pcm.length > 0) {
                    this.player.enqueue(speaker, pcm, current.volume());
                }
            }
        } catch (ServiceException exception) {
            this.onFailure.accept(exception);
        } catch (RuntimeException exception) {
            VoiceTranslate.LOGGER.error("Speaking a translation failed", exception);
        }
    }

    @Override
    public void close() {
        this.worker.shutdownNow();
    }
}
