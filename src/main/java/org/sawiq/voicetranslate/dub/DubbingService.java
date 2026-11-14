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
 * Speaks other players' translated sentences in their own voices.
 *
 * <p>One sentence at a time: the voice model uses the whole graphics card,
 * and two at once would only make both late. When it falls behind, the
 * oldest waiting sentence is dropped; a translation spoken half a minute
 * after the fact helps nobody.</p>
 */
public final class DubbingService implements AutoCloseable {
    private static final int MAX_WAITING = 4;
    private static final long STALE_AFTER_MILLIS = 15_000L;

    /**
     * @param isEnabled    whether dubbing is on
     * @param cloneVoices  whether to copy the speaker's voice or use the engine's own
     * @param volume       0..2, the dub's loudness
     */
    public record Settings(boolean isEnabled, boolean cloneVoices, float volume) {
    }

    private final VoiceSynthesizer synthesizer;
    private final VoiceSamples samples;
    private final DubPlayer player;
    private final Supplier<Settings> settings;
    private final Consumer<ServiceException> onFailure;
    private final ThreadPoolExecutor worker;

    public DubbingService(VoiceSynthesizer synthesizer, VoiceSamples samples, DubPlayer player,
                          Supplier<Settings> settings, Consumer<ServiceException> onFailure) {
        this.synthesizer = synthesizer;
        this.samples = samples;
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
        if (!current.isEnabled() || !caption.isTranslated() || !DubLanguages.isSpoken(language)
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
        VoiceSample voice = current.cloneVoices() ? this.samples.sampleFor(speaker) : null;
        try {
            for (String sentence : Sentences.split(caption.translation())) {
                short[] pcm = this.synthesizer.speak(sentence, language, voice);
                this.player.enqueue(speaker, pcm, current.volume());
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
