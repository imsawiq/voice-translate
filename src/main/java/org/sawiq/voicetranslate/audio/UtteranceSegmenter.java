package org.sawiq.voicetranslate.audio;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Cuts one player's voice into utterances.
 *
 * <p>The voice mods already stop sending when a player releases push-to-talk
 * or goes quiet under voice activation, so a gap in the packets is the
 * clearest end of a sentence. A player with an open microphone streams all
 * the time, though, so the frames are also judged by loudness against a
 * running estimate of that player's background noise: a pause in speech ends
 * the utterance even while packets keep coming.</p>
 *
 * <p>Thread-safe: frames arrive on the voice mod's audio thread and the end
 * of a stream is noticed on the intake's timer.</p>
 */
final class UtteranceSegmenter {
    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private static final int SAMPLES_PER_MILLI = Downsampler.OUTPUT_RATE / 1000;
    private static final int PRE_ROLL_MILLIS = 300;
    private static final int TRAILING_SILENCE_KEPT_MILLIS = 250;
    private static final int MIN_VOICED_MILLIS = 280;
    private static final int MIN_DRAFT_MILLIS = 1_000;
    /** No packet for this long means the sender stopped transmitting. */
    private static final int STREAM_GAP_MILLIS = 400;
    private static final double SILENCE_FLOOR_DB = -60.0;
    /** Quieter than this is never speech, however quiet the background is. */
    private static final double ABSOLUTE_SPEECH_DB = -50.0;
    private static final int SPLIT_SEARCH_MILLIS = 2_500;
    private static final int SPLIT_WINDOW_MILLIS = 200;

    private final UUID speakerId;
    private final UtteranceListener listener;
    private final Supplier<SegmenterSettings> settings;
    private final RateConverter rateConverter = new RateConverter();
    private final Downsampler downsampler = new Downsampler();
    private final SampleBuffer preRoll = new SampleBuffer(PRE_ROLL_MILLIS * SAMPLES_PER_MILLI * 2, PRE_ROLL_MILLIS * SAMPLES_PER_MILLI * 4);
    private final SampleBuffer speech;

    private String speakerName = "";
    private double noiseFloorDb = SILENCE_FLOOR_DB;
    private boolean isSpeaking;
    private long utteranceId;
    private long startedAt;
    private long lastFrameAt;
    private long lastVoiceAt;
    private long lastDraftAt;
    private int voicedMillis;
    private boolean hasSentDraft;

    UtteranceSegmenter(UUID speakerId, UtteranceListener listener, Supplier<SegmenterSettings> settings) {
        this.speakerId = speakerId;
        this.listener = listener;
        this.settings = settings;
        int maxSamples = 30 * Downsampler.OUTPUT_RATE;
        this.speech = new SampleBuffer(4 * Downsampler.OUTPUT_RATE, maxSamples);
    }

    /** @param sampleRate the rate the voice mod plays this voice at; 48 kHz for Simple Voice Chat */
    synchronized void acceptFrame(short[] samples, int sampleRate, String name, long now) {
        if (name != null && !name.isEmpty()) {
            this.speakerName = name;
        }
        SegmenterSettings current = this.settings.get();
        if (this.isSpeaking && now - this.lastFrameAt > STREAM_GAP_MILLIS) {
            finish();
        }
        this.lastFrameAt = now;

        short[] mono48k = this.rateConverter.toVoiceRate(samples, sampleRate);
        if (mono48k.length == 0) {
            return;
        }
        short[] frame = this.downsampler.process(mono48k);
        int frameMillis = Math.max(1, frame.length / SAMPLES_PER_MILLI);
        double level = Pcm.rmsDb(mono48k);
        boolean isVoiced = level > Math.max(this.noiseFloorDb + current.sensitivityDb(), ABSOLUTE_SPEECH_DB);
        if (!isVoiced) {
            // Slow, so a single loud breath does not move it, and only on
            // quiet frames, so the speech itself never raises it.
            this.noiseFloorDb += (level - this.noiseFloorDb) * 0.05;
        }

        if (!this.isSpeaking) {
            if (isVoiced) {
                begin(now);
            } else {
                rememberPreRoll(frame);
                return;
            }
        }

        this.speech.append(frame);
        if (isVoiced) {
            this.lastVoiceAt = now;
            this.voicedMillis += frameMillis;
        }

        if (now - this.lastVoiceAt >= current.pauseMillis()) {
            finish();
        } else if (this.speech.length() >= current.maxSeconds() * Downsampler.OUTPUT_RATE) {
            splitAtQuietestPoint(now);
        } else if (current.wantsDrafts() && shouldSendDraft(now, current)) {
            sendDraft(now);
        }
    }

    /** Ends an utterance whose sender stopped transmitting. */
    synchronized void checkTimeout(long now) {
        if (this.isSpeaking && now - this.lastFrameAt > STREAM_GAP_MILLIS) {
            finish();
        }
    }

    synchronized boolean isIdleSince(long now, long millis) {
        return !this.isSpeaking && now - this.lastFrameAt > millis;
    }

    private void begin(long now) {
        this.isSpeaking = true;
        this.utteranceId = NEXT_ID.getAndIncrement();
        this.startedAt = now;
        this.lastVoiceAt = now;
        this.lastDraftAt = now;
        this.voicedMillis = 0;
        this.hasSentDraft = false;
        this.speech.clear();
        this.speech.append(this.preRoll.copy());
        this.preRoll.clear();
    }

    private void rememberPreRoll(short[] frame) {
        this.preRoll.append(frame);
        int keep = PRE_ROLL_MILLIS * SAMPLES_PER_MILLI;
        if (this.preRoll.length() > keep) {
            this.preRoll.dropBefore(this.preRoll.length() - keep);
        }
    }

    private boolean shouldSendDraft(long now, SegmenterSettings current) {
        return this.voicedMillis >= MIN_DRAFT_MILLIS && now - this.lastDraftAt >= current.draftIntervalMillis();
    }

    private void sendDraft(long now) {
        this.lastDraftAt = now;
        this.hasSentDraft = true;
        this.listener.onUtterance(new Utterance(this.utteranceId, this.speakerId, this.speakerName,
                this.speech.copy(), this.startedAt, false));
    }

    private void finish() {
        this.isSpeaking = false;
        this.downsampler.reset();
        this.rateConverter.reset();
        if (this.voicedMillis < MIN_VOICED_MILLIS) {
            if (this.hasSentDraft) {
                this.listener.onDiscarded(this.speakerId, this.utteranceId);
            }
            this.speech.clear();
            return;
        }
        trimTrailingSilence();
        this.listener.onUtterance(new Utterance(this.utteranceId, this.speakerId, this.speakerName,
                this.speech.copy(), this.startedAt, true));
        this.speech.clear();
    }

    private void trimTrailingSilence() {
        long silentMillis = Math.max(0L, this.lastFrameAt - this.lastVoiceAt);
        long dropMillis = silentMillis - TRAILING_SILENCE_KEPT_MILLIS;
        if (dropMillis > 0) {
            this.speech.truncate(this.speech.length() - (int) (dropMillis * SAMPLES_PER_MILLI));
        }
    }

    /**
     * A long monologue is cut where the speaker drew breath rather than in
     * the middle of a word; the rest carries on as a new utterance.
     */
    private void splitAtQuietestPoint(long now) {
        int searchFrom = Math.max(0, this.speech.length() - SPLIT_SEARCH_MILLIS * SAMPLES_PER_MILLI);
        int window = SPLIT_WINDOW_MILLIS * SAMPLES_PER_MILLI;
        int cut = this.speech.quietestWindowStart(searchFrom, window) + window / 2;

        this.listener.onUtterance(new Utterance(this.utteranceId, this.speakerId, this.speakerName,
                this.speech.copyRange(0, cut), this.startedAt, true));

        this.speech.dropBefore(cut);
        this.utteranceId = NEXT_ID.getAndIncrement();
        this.startedAt = now;
        this.lastDraftAt = now;
        this.hasSentDraft = false;
        this.voicedMillis = this.speech.length() / SAMPLES_PER_MILLI;
    }
}
