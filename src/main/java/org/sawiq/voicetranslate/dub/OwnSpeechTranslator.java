package org.sawiq.voicetranslate.dub;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.audio.Pcm;
import org.sawiq.voicetranslate.caption.Caption;
import org.sawiq.voicetranslate.net.ServiceException;
import org.sawiq.voicetranslate.pipeline.PipelineServices;
import org.sawiq.voicetranslate.speech.HallucinationFilter;
import org.sawiq.voicetranslate.speech.RecognitionRequest;
import org.sawiq.voicetranslate.speech.Transcript;
import org.sawiq.voicetranslate.translation.GlossaryTerm;
import org.sawiq.voicetranslate.translation.TranslationRequest;

/**
 * Sends the player's speech in another language, in their own voice.
 *
 * <p>While they talk nothing is sent: the phrase is recorded. When they stop,
 * it is recognised, translated and spoken, and the spoken translation goes
 * out through the voice mod as if they had said it. Other players need
 * nothing installed.</p>
 *
 * <p>If any step fails, the original recording is sent instead, a little
 * late: being heard in the wrong language beats not being heard at all.
 * Speech already in the target language is also sent as it was.</p>
 *
 * <p>{@link #onTick} runs on the voice mod's capture thread every 20 ms; the
 * slow work happens on a worker of its own.</p>
 */
public final class OwnSpeechTranslator implements AutoCloseable {
    private static final int FRAME = DubPlayer.FRAME_SAMPLES;
    /** Shorter than this is a tap on the key or a cough. */
    private static final int MIN_PHRASE_FRAMES = 20;
    /** A monologue is cut and sent after this, rather than waiting for its end. */
    private static final int MAX_PHRASE_FRAMES = 15 * 50;
    private static final int MAX_WAITING_PHRASES = 3;
    private static final int MAX_QUEUED_FRAMES = 60 * 50;

    /**
     * @param isEnabled      whether the player turned this on
     * @param targetLanguage what to speak in
     * @param volume         0..2
     */
    public record Settings(boolean isEnabled, String targetLanguage, float volume) {
    }

    private final Supplier<PipelineServices> services;
    private final VoiceSynthesizer synthesizer;
    private final VoiceSamples samples;
    private final Supplier<UUID> self;
    private final Supplier<Settings> settings;
    private final Consumer<Caption> onSpoken;
    private final Consumer<ServiceException> onFailure;
    private final ThreadPoolExecutor worker;
    private final ConcurrentLinkedQueue<short[]> playback = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedFrames = new AtomicInteger();
    private final AtomicInteger pendingPhrases = new AtomicInteger();

    private short[] recording = new short[FRAME * 100];
    private int recordedFrames;

    public OwnSpeechTranslator(Supplier<PipelineServices> services, VoiceSynthesizer synthesizer, VoiceSamples samples,
                               Supplier<UUID> self, Supplier<Settings> settings,
                               Consumer<Caption> onSpoken, Consumer<ServiceException> onFailure) {
        this.services = services;
        this.synthesizer = synthesizer;
        this.samples = samples;
        this.self = self;
        this.settings = settings;
        this.onSpoken = onSpoken;
        this.onFailure = onFailure;
        this.worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(MAX_WAITING_PHRASES),
                runnable -> {
                    Thread thread = new Thread(runnable, "Voice Translate own speech");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.DiscardOldestPolicy());
        this.worker.allowCoreThreadTimeOut(true);
    }

    public boolean isEnabled() {
        return this.settings.get().isEnabled();
    }

    /** Whether there is something to send now or soon, so the voice mod keeps its stream open. */
    public boolean isBusy() {
        return !this.playback.isEmpty() || this.pendingPhrases.get() > 0 || this.recordedFrames > 0;
    }

    /**
     * One 20 ms tick of the microphone.
     *
     * @param spoken the 48 kHz mono frame the player said, or null while they are silent
     * @return the frame to send instead, or null to send nothing
     */
    public short[] onTick(short[] spoken) {
        if (spoken != null && spoken.length == FRAME) {
            record(spoken);
            if (this.recordedFrames >= MAX_PHRASE_FRAMES) {
                submitPhrase();
            }
        } else if (this.recordedFrames > 0) {
            submitPhrase();
        }
        short[] frame = this.playback.poll();
        if (frame != null) {
            this.queuedFrames.decrementAndGet();
        }
        return frame;
    }

    /** Drops everything waiting, for turning the feature off or leaving a server. */
    public void reset() {
        this.recordedFrames = 0;
        this.playback.clear();
        this.queuedFrames.set(0);
    }

    @Override
    public void close() {
        this.worker.shutdownNow();
        reset();
    }

    private void record(short[] frame) {
        int needed = (this.recordedFrames + 1) * FRAME;
        if (needed > this.recording.length) {
            this.recording = Arrays.copyOf(this.recording, this.recording.length * 2);
        }
        System.arraycopy(frame, 0, this.recording, this.recordedFrames * FRAME, FRAME);
        this.recordedFrames++;
    }

    private void submitPhrase() {
        int frames = this.recordedFrames;
        this.recordedFrames = 0;
        if (frames < MIN_PHRASE_FRAMES) {
            return;
        }
        short[] phrase = Arrays.copyOf(this.recording, frames * FRAME);
        this.pendingPhrases.incrementAndGet();
        this.worker.execute(() -> {
            try {
                translate(phrase);
            } finally {
                this.pendingPhrases.decrementAndGet();
            }
        });
    }

    private void translate(short[] phrase48k) {
        Settings current = this.settings.get();
        PipelineServices services = this.services.get();
        UUID speaker = this.self.get();
        if (!current.isEnabled() || services == null || speaker == null) {
            play(phrase48k, 1f);
            return;
        }
        long startedAt = System.currentTimeMillis();
        try {
            short[] speech = Pcm.toSpeechRate(phrase48k);
            Transcript transcript = services.recognizer().transcribe(
                    new RecognitionRequest(speech, services.fixedSpokenLanguage(), "", false));
            String text = HallucinationFilter.clean(transcript, speech.length / (double) Pcm.SPEECH_SAMPLE_RATE);
            if (text.isEmpty()) {
                return;
            }
            this.samples.offer(speaker, speech);
            String target = current.targetLanguage();
            if (target.equals(transcript.language()) || !DubLanguages.isSpoken(target)) {
                play(phrase48k, 1f);
                return;
            }
            // The game glossary maps English names into the reader's language, so it fits only that pair.
            List<GlossaryTerm> glossary = "en".equals(transcript.language()) && target.equals(services.targetLanguage())
                    ? services.glossary().find(text) : List.of();
            String translation = services.translator()
                    .translate(new TranslationRequest(text, transcript.language(), target, List.of(), glossary))
                    .text();
            for (String sentence : Sentences.split(translation)) {
                play(this.synthesizer.speak(sentence, target, this.samples.sampleFor(speaker)), current.volume());
            }
            this.onSpoken.accept(new Caption(-startedAt, speaker, "", text, translation, transcript.language(),
                    true, true, startedAt, System.currentTimeMillis()));
        } catch (ServiceException exception) {
            play(phrase48k, 1f);
            this.onFailure.accept(exception);
        } catch (RuntimeException exception) {
            play(phrase48k, 1f);
            VoiceTranslate.LOGGER.error("Translating own speech failed", exception);
        }
    }

    private void play(short[] pcm48k, float gain) {
        for (int start = 0; start < pcm48k.length; start += FRAME) {
            if (this.queuedFrames.get() >= MAX_QUEUED_FRAMES) {
                return;
            }
            short[] frame = new short[FRAME];
            int length = Math.min(FRAME, pcm48k.length - start);
            for (int i = 0; i < length; i++) {
                frame[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(pcm48k[start + i] * gain)));
            }
            this.playback.add(frame);
            this.queuedFrames.incrementAndGet();
        }
    }
}
