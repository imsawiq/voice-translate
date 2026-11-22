package org.sawiq.voicetranslate.caption;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The subtitles on screen right now and the history of what was said.
 *
 * <p>Written by pipeline workers, read every frame by the HUD, so every
 * method takes the same lock and hands out copies.</p>
 */
public final class CaptionBoard {
    private static final int HISTORY_SIZE = 300;
    private static final int MAX_ACTIVE = 32;

    private final Map<Long, Caption> active = new LinkedHashMap<>();
    private final Deque<Caption> history = new ArrayDeque<>();

    /**
     * Shows a caption or replaces the earlier version of the same sentence.
     * A draft that arrives after its final version is ignored: recognition
     * runs in parallel, and a slow draft must not undo the finished sentence.
     */
    public synchronized void publish(Caption caption) {
        Caption previous = this.active.get(caption.utteranceId());
        if (previous != null && previous.isFinal() && !caption.isFinal()) {
            return;
        }
        this.active.put(caption.utteranceId(), caption);
        if (caption.isFinal()) {
            this.history.addLast(caption);
            while (this.history.size() > HISTORY_SIZE) {
                this.history.removeFirst();
            }
        }
        while (this.active.size() > MAX_ACTIVE) {
            Long oldest = this.active.keySet().iterator().next();
            this.active.remove(oldest);
        }
    }

    public synchronized void remove(long utteranceId) {
        this.active.remove(utteranceId);
    }

    /** Captions still on screen at {@code now}, oldest first. */
    public synchronized List<Caption> visible(long now) {
        this.active.values().removeIf(caption -> caption.visibleUntil() <= now);
        List<Caption> captions = new ArrayList<>(this.active.values());
        captions.sort(Comparator.comparingLong(Caption::createdAt));
        return captions;
    }

    /** Final captions, oldest first. */
    public synchronized List<Caption> history() {
        return List.copyOf(this.history);
    }

    /** What one player said last, newest last, for translation context. */
    public synchronized List<String> recentOriginals(UUID speakerId, int count) {
        List<String> lines = new ArrayList<>();
        var iterator = this.history.descendingIterator();
        while (iterator.hasNext() && lines.size() < count) {
            Caption caption = iterator.next();
            if (caption.speakerId().equals(speakerId)) {
                lines.addFirst(caption.original());
            }
        }
        return lines;
    }

    /** Everyone heard recently, newest first, for the per-player switches. */
    public synchronized List<Caption> recentSpeakers() {
        Map<UUID, Caption> latest = new LinkedHashMap<>();
        var iterator = this.history.descendingIterator();
        while (iterator.hasNext()) {
            Caption caption = iterator.next();
            latest.putIfAbsent(caption.speakerId(), caption);
        }
        return List.copyOf(latest.values());
    }

    public synchronized void clearActive() {
        this.active.clear();
    }

    public synchronized void clearHistory() {
        this.history.clear();
    }
}
