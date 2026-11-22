package org.sawiq.voicetranslate.dub;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuts a translation into sentences for speaking. The voice model takes a
 * little longer than the speech it makes, so the first sentence is spoken
 * while the next is still being made, instead of everything waiting for the
 * end of the longest.
 */
final class Sentences {
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?…。！？])\\s+");
    /** Fragments shorter than this are joined to the next; a one-word sentence sounds clipped. */
    private static final int MIN_PIECE_CHARACTERS = 24;

    private Sentences() {
    }

    static List<String> split(String text) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Matcher matcher = SENTENCE_END.matcher(text);
        int start = 0;
        while (matcher.find()) {
            append(current, text.substring(start, matcher.start()));
            start = matcher.end();
            if (current.length() >= MIN_PIECE_CHARACTERS) {
                pieces.add(current.toString());
                current.setLength(0);
            }
        }
        append(current, text.substring(start));
        if (!current.isEmpty()) {
            if (current.length() < MIN_PIECE_CHARACTERS && !pieces.isEmpty()) {
                pieces.set(pieces.size() - 1, pieces.getLast() + " " + current);
            } else {
                pieces.add(current.toString());
            }
        }
        return pieces;
    }

    private static void append(StringBuilder current, String part) {
        String trimmed = part.strip();
        if (trimmed.isEmpty()) {
            return;
        }
        if (!current.isEmpty()) {
            current.append(' ');
        }
        current.append(trimmed);
    }
}
