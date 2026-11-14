package org.sawiq.voicetranslate.pipeline;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.audio.Utterance;
import org.sawiq.voicetranslate.audio.UtteranceListener;
import org.sawiq.voicetranslate.caption.Caption;
import org.sawiq.voicetranslate.caption.CaptionBoard;
import org.sawiq.voicetranslate.language.SpeakerLanguages;
import org.sawiq.voicetranslate.net.ServiceException;
import org.sawiq.voicetranslate.speech.HallucinationFilter;
import org.sawiq.voicetranslate.speech.RecognitionRequest;
import org.sawiq.voicetranslate.speech.Transcript;
import org.sawiq.voicetranslate.translation.GlossaryTerm;
import org.sawiq.voicetranslate.translation.TranslationRequest;
import org.sawiq.voicetranslate.translation.TranslationResult;

/**
 * Takes utterances from the intake through recognition and translation to
 * the caption board.
 *
 * <p>Finished sentences always go before drafts, and only the newest draft
 * of a speaker is worth recognising: by the time an older one would run, the
 * speaker has said more. When the services cannot keep up, drafts are
 * dropped first and then sentences too old to still matter, so subtitles stay
 * close to the voice instead of trailing further and further behind.</p>
 */
public final class TranslationPipeline implements UtteranceListener, AutoCloseable {
    private static final int WORKERS = 3;
    private static final int MAX_QUEUED = 24;
    private static final long STALE_AFTER_MILLIS = 20_000L;
    private static final double HINT_ONLY_BELOW_SECONDS = 4.0;
    private static final int CONTEXT_LINES = 3;
    private static final int PROMPT_MAX_CHARACTERS = 200;
    private static final int REMEMBERED_FINALS = 256;
    /**
     * A speaker's draft is translated at most this often. Drafts change every
     * second, and translating each one would spend a paid service's budget
     * and get the free Google endpoints throttled; in between, the last
     * translated draft stays on screen.
     */
    private static final long DRAFT_TRANSLATION_INTERVAL_MILLIS = 2_500L;

    private final CaptionBoard board;
    private final SpeakerLanguages languages;
    private final Consumer<Caption> onFinished;
    private final Consumer<ServiceException> onFailure;
    private final ThreadPoolExecutor workers;
    private final Map<UUID, Long> newestDraft = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDraftTranslation = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> finalized = new LinkedHashMap<>(64, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
            return size() > REMEMBERED_FINALS;
        }
    };
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong translatedCount = new AtomicLong();
    private final AtomicLong droppedCount = new AtomicLong();
    private volatile PipelineServices services;
    private volatile Semaphore recognitionSlots = new Semaphore(1);
    private volatile ServiceException lastFailure;
    private volatile long lastFailureAt;
    private volatile long averageLatencyMillis;

    /**
     * @param onFinished called on a worker thread with every finished, non-empty caption
     * @param onFailure  called on a worker thread whenever a job fails
     */
    public TranslationPipeline(CaptionBoard board, SpeakerLanguages languages,
                               Consumer<Caption> onFinished, Consumer<ServiceException> onFailure) {
        this.board = board;
        this.languages = languages;
        this.onFinished = onFinished;
        this.onFailure = onFailure;
        Comparator<Runnable> order = Comparator.comparing(runnable -> (Job) runnable);
        this.workers = new ThreadPoolExecutor(WORKERS, WORKERS, 30, TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(MAX_QUEUED, order), runnable -> {
                    Thread thread = new Thread(runnable, "Voice Translate worker");
                    thread.setDaemon(true);
                    return thread;
                });
        this.workers.allowCoreThreadTimeOut(true);
    }

    /** Switches recognizer, translator and languages; jobs already running finish with the old ones. */
    public void use(PipelineServices next) {
        this.services = next;
        this.recognitionSlots = new Semaphore(Math.max(1, next.recognizer().parallelism()));
    }

    public PipelineHealth health() {
        return new PipelineHealth(this.lastFailure, this.lastFailureAt, this.averageLatencyMillis,
                this.translatedCount.get(), this.droppedCount.get());
    }

    @Override
    public void onUtterance(Utterance utterance) {
        PipelineServices current = this.services;
        if (current == null || (!utterance.isFinal() && !current.wantsDrafts())) {
            return;
        }
        if (!utterance.isFinal() && this.workers.getQueue().size() >= MAX_QUEUED) {
            this.droppedCount.incrementAndGet();
            return;
        }
        long ticket = this.sequence.incrementAndGet();
        if (utterance.isFinal()) {
            synchronized (this.finalized) {
                this.finalized.put(utterance.id(), Boolean.TRUE);
            }
        } else {
            this.newestDraft.put(utterance.speakerId(), ticket);
        }
        this.workers.execute(new Job(utterance, ticket, System.currentTimeMillis()));
    }

    @Override
    public void onDiscarded(UUID speakerId, long utteranceId) {
        this.board.remove(utteranceId);
    }

    @Override
    public void close() {
        this.workers.shutdownNow();
    }

    private boolean isFinalized(long utteranceId) {
        synchronized (this.finalized) {
            return this.finalized.containsKey(utteranceId);
        }
    }

    private void run(Job job) {
        Utterance utterance = job.utterance;
        PipelineServices current = this.services;
        if (current == null) {
            return;
        }
        if (!utterance.isFinal() && (isFinalized(utterance.id())
                || this.newestDraft.getOrDefault(utterance.speakerId(), 0L) != job.ticket)) {
            return;
        }
        long age = System.currentTimeMillis() - (utterance.startedAt() + (long) (utterance.seconds() * 1000));
        if (utterance.isFinal() && age > STALE_AFTER_MILLIS) {
            this.droppedCount.incrementAndGet();
            this.board.remove(utterance.id());
            return;
        }

        try {
            process(utterance, current, job.queuedAt);
            if (utterance.isFinal()) {
                this.lastFailure = null;
            }
        } catch (ServiceException exception) {
            // A draft is a preview; only a lost sentence is worth telling the player about.
            if (utterance.isFinal()) {
                this.lastFailure = exception;
                this.lastFailureAt = System.currentTimeMillis();
                this.board.remove(utterance.id());
                this.onFailure.accept(exception);
            }
        } catch (RuntimeException exception) {
            VoiceTranslate.LOGGER.error("Translating a sentence failed", exception);
        }
    }

    private void process(Utterance utterance, PipelineServices current, long queuedAt) throws ServiceException {
        Optional<String> learned = this.languages.hintFor(utterance.speakerId());
        String hint = !current.fixedSpokenLanguage().isEmpty()
                ? current.fixedSpokenLanguage()
                : utterance.seconds() < HINT_ONLY_BELOW_SECONDS ? learned.orElse("") : "";

        Transcript transcript = recognize(current, new RecognitionRequest(
                utterance.samples(), hint, promptFor(utterance.speakerId()), !utterance.isFinal()));
        String text = HallucinationFilter.clean(transcript, utterance.seconds());
        if (text.isEmpty()) {
            if (utterance.isFinal()) {
                this.board.remove(utterance.id());
            }
            return;
        }
        if (utterance.isFinal() && hint.isEmpty()) {
            this.languages.record(utterance.speakerId(), transcript.language(), transcript.languageProbability());
        }

        String source = transcript.language();
        String target = current.targetLanguage();
        Caption caption;
        if (source.equals(target)) {
            if (!current.showUntranslated()) {
                this.board.remove(utterance.id());
                return;
            }
            caption = caption(utterance, text, text, source, false);
        } else {
            if (!utterance.isFinal() && !mayTranslateDraft(utterance.speakerId())) {
                return;
            }
            List<String> context = this.board.recentOriginals(utterance.speakerId(), CONTEXT_LINES);
            List<GlossaryTerm> glossary = "en".equals(source) ? current.glossary().find(text) : List.of();
            TranslationResult result = current.translator().translate(new TranslationRequest(text, source, target, context, glossary));
            String detected = source.isEmpty() ? result.detectedLanguage() : source;
            boolean isSame = detected.equals(target) || result.text().equalsIgnoreCase(text);
            if (isSame && !current.showUntranslated()) {
                this.board.remove(utterance.id());
                return;
            }
            caption = caption(utterance, text, isSame ? text : result.text(), detected, !isSame);
        }

        if (!utterance.isFinal() && isFinalized(utterance.id())) {
            return;
        }
        this.board.publish(caption);
        if (utterance.isFinal()) {
            this.translatedCount.incrementAndGet();
            long latency = System.currentTimeMillis() - queuedAt;
            this.averageLatencyMillis = this.averageLatencyMillis == 0 ? latency : (this.averageLatencyMillis * 4 + latency) / 5;
            this.onFinished.accept(caption);
        }
    }

    private boolean mayTranslateDraft(UUID speakerId) {
        long now = System.currentTimeMillis();
        Long last = this.lastDraftTranslation.get(speakerId);
        if (last != null && now - last < DRAFT_TRANSLATION_INTERVAL_MILLIS) {
            return false;
        }
        this.lastDraftTranslation.put(speakerId, now);
        return true;
    }

    private Transcript recognize(PipelineServices current, RecognitionRequest request) throws ServiceException {
        Semaphore slots = this.recognitionSlots;
        try {
            slots.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException(ServiceException.Kind.NETWORK, "interrupted", exception);
        }
        try {
            return current.recognizer().transcribe(request);
        } finally {
            slots.release();
        }
    }

    /** The speaker's previous sentence: Whisper keeps names and spelling consistent with it. */
    private String promptFor(UUID speakerId) {
        List<String> previous = this.board.recentOriginals(speakerId, 1);
        if (previous.isEmpty()) {
            return "";
        }
        String line = previous.getFirst();
        return line.length() <= PROMPT_MAX_CHARACTERS ? line : line.substring(line.length() - PROMPT_MAX_CHARACTERS);
    }

    private static Caption caption(Utterance utterance, String original, String translation, String language, boolean isTranslated) {
        return new Caption(utterance.id(), utterance.speakerId(), utterance.speakerName(), original, translation,
                language, isTranslated, utterance.isFinal(), utterance.startedAt(), System.currentTimeMillis());
    }

    /** Finished sentences first, then by arrival; the ticket also tells a draft whether it is still the newest. */
    private final class Job implements Runnable, Comparable<Job> {
        private final Utterance utterance;
        private final long ticket;
        private final long queuedAt;

        private Job(Utterance utterance, long ticket, long queuedAt) {
            this.utterance = utterance;
            this.ticket = ticket;
            this.queuedAt = queuedAt;
        }

        @Override
        public void run() {
            TranslationPipeline.this.run(this);
        }

        @Override
        public int compareTo(Job other) {
            if (this.utterance.isFinal() != other.utterance.isFinal()) {
                return this.utterance.isFinal() ? -1 : 1;
            }
            return Long.compare(this.ticket, other.ticket);
        }
    }
}
