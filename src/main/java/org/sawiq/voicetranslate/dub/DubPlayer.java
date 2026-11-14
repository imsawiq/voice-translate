package org.sawiq.voicetranslate.dub;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.sawiq.voicetranslate.VoiceTranslate;

/**
 * Plays spoken translations into the voice mod, one 20 ms frame per speaker
 * per tick, the pace both voice mods play voices at.
 *
 * <p>Each speaker has a queue of their own, so two players' translations
 * play side by side as their voices would, and one player's sentences play
 * in the order they were said.</p>
 */
public final class DubPlayer implements AutoCloseable {
    public static final int FRAME_SAMPLES = 960;
    private static final long FRAME_MICROS = 20_000L;
    /** A queue longer than this is out of date; its oldest sentence goes. */
    private static final int MAX_QUEUED_FRAMES = 30 * 50;

    /** Where frames go: a voice mod's audio channel at the speaker. */
    public interface Output {
        void play(UUID speaker, short[] frame);

        /** The speaker's queue ran out; the channel may be let go. */
        void finish(UUID speaker);
    }

    private final Map<UUID, Deque<short[]>> queues = new HashMap<>();
    private final ScheduledExecutorService pump;
    private volatile Output output;

    public DubPlayer() {
        this.pump = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Voice Translate dubbing");
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY - 1);
            return thread;
        });
        this.pump.scheduleAtFixedRate(this::tick, FRAME_MICROS, FRAME_MICROS, TimeUnit.MICROSECONDS);
    }

    /** The voice mod in use hands its output in; null when it goes away. */
    public void setOutput(Output output) {
        this.output = output;
    }

    public boolean hasOutput() {
        return this.output != null;
    }

    /** Queues a spoken sentence, 48 kHz mono, scaled by {@code gain}. */
    public synchronized void enqueue(UUID speaker, short[] pcm, float gain) {
        Deque<short[]> queue = this.queues.computeIfAbsent(speaker, id -> new ArrayDeque<>());
        for (int start = 0; start < pcm.length; start += FRAME_SAMPLES) {
            short[] frame = new short[FRAME_SAMPLES];
            int length = Math.min(FRAME_SAMPLES, pcm.length - start);
            for (int i = 0; i < length; i++) {
                frame[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(pcm[start + i] * gain)));
            }
            queue.addLast(frame);
        }
        while (queue.size() > MAX_QUEUED_FRAMES) {
            queue.removeFirst();
        }
    }

    public synchronized boolean isPlaying(UUID speaker) {
        Deque<short[]> queue = this.queues.get(speaker);
        return queue != null && !queue.isEmpty();
    }

    public synchronized void clear() {
        this.queues.clear();
    }

    @Override
    public void close() {
        this.pump.shutdownNow();
        clear();
    }

    private void tick() {
        Output target = this.output;
        if (target == null) {
            return;
        }
        try {
            Map<UUID, short[]> frames = new HashMap<>();
            Map<UUID, Boolean> finished = new HashMap<>();
            synchronized (this) {
                Iterator<Map.Entry<UUID, Deque<short[]>>> entries = this.queues.entrySet().iterator();
                while (entries.hasNext()) {
                    Map.Entry<UUID, Deque<short[]>> entry = entries.next();
                    short[] frame = entry.getValue().pollFirst();
                    if (frame != null) {
                        frames.put(entry.getKey(), frame);
                    }
                    if (entry.getValue().isEmpty()) {
                        finished.put(entry.getKey(), Boolean.TRUE);
                        entries.remove();
                    }
                }
            }
            frames.forEach(target::play);
            finished.keySet().forEach(target::finish);
        } catch (RuntimeException exception) {
            // A scheduled task that throws is never run again.
            VoiceTranslate.LOGGER.error("Playing a translation failed", exception);
        }
    }
}
