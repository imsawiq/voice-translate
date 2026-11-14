package org.sawiq.voicetranslate.client.hud;

import java.util.UUID;

/** Colours shared by the bubbles and the panel. */
final class CaptionStyle {
    static final int TRANSLATION = 0xFFFFFF;
    static final int ORIGINAL = 0xA8A8A8;
    static final int DRAFT = 0xC8C8C8;
    static final int LANGUAGE_TAG = 0x8C8C8C;

    /** Readable on a dark background, distinct from each other, none of them red (red means failure). */
    private static final int[] NAME_COLOURS = {
            0x7FD3FF, 0xFFD27F, 0x9CF29C, 0xFF9CE6, 0xB7A6FF, 0x7FFFE3, 0xFFB38A, 0xE2F27F
    };

    private CaptionStyle() {
    }

    /** The same player always gets the same colour. */
    static int nameColour(UUID player) {
        return NAME_COLOURS[Math.floorMod(player.hashCode(), NAME_COLOURS.length)];
    }

    /** Packs a 0..1 opacity onto an RGB colour; text this faint is not drawn at all. */
    static int withOpacity(int rgb, float opacity) {
        int alpha = Math.round(Math.max(0f, Math.min(1f, opacity)) * 255);
        return Math.max(alpha, 8) << 24 | rgb & 0xFFFFFF;
    }

    static int background(float opacity, int backgroundPercent) {
        int alpha = Math.round(opacity * backgroundPercent / 100f * 255);
        return alpha << 24;
    }
}
