package org.sawiq.voicetranslate.caption;

import java.util.UUID;

/**
 * One subtitle: what a player said and its translation.
 *
 * @param utteranceId    shared by the drafts and the final caption of one sentence
 * @param speakerName    the name the voice mod reported, may be empty; the
 *                       HUD looks the current name up itself
 * @param original       the recognised speech
 * @param translation    the translation, or the original when no translation was needed
 * @param sourceLanguage ISO 639-1 code of what was spoken, empty if unknown
 * @param isTranslated   false when the speech was already in the reader's language
 * @param isFinal        false for a draft of a sentence still being spoken
 * @param createdAt      epoch millis when the speaker began
 * @param updatedAt      epoch millis of the last change, for fading out
 */
public record Caption(
        long utteranceId,
        UUID speakerId,
        String speakerName,
        String original,
        String translation,
        String sourceLanguage,
        boolean isTranslated,
        boolean isFinal,
        long createdAt,
        long updatedAt
) {
    private static final long MIN_VISIBLE_MILLIS = 3_500L;
    private static final long MAX_VISIBLE_MILLIS = 12_000L;
    private static final long MILLIS_PER_CHARACTER = 65L;
    /** A draft whose final never comes (the engine failed) disappears on its own. */
    private static final long DRAFT_VISIBLE_MILLIS = 8_000L;

    /** Long enough to read: a little more per character, within limits. */
    public long visibleUntil() {
        if (!this.isFinal) {
            return this.updatedAt + DRAFT_VISIBLE_MILLIS;
        }
        long reading = this.translation.length() * MILLIS_PER_CHARACTER;
        return this.updatedAt + Math.max(MIN_VISIBLE_MILLIS, Math.min(MAX_VISIBLE_MILLIS, MIN_VISIBLE_MILLIS / 2 + reading));
    }

    /** 1 while fully visible, falling to 0 over the last half second. */
    public float opacityAt(long now) {
        long left = visibleUntil() - now;
        return left >= 500 ? 1f : Math.max(0f, left / 500f);
    }
}
