package org.sawiq.voicetranslate.client.ui;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Everything the settings screen draws besides widgets: text, fills,
 * progress bars. The one class that touches Minecraft's drawing API, so the
 * 1.21 builds only have to adapt this.
 */
final class UiPaint {
    static final int WHITE = 0xFFFFFFFF;
    static final int TEXT = 0xFFE0E0E0;
    static final int GREY = 0xFFA0A0A0;
    static final int DIM = 0xFF808080;
    static final int GREEN = 0xFF66DD66;
    static final int YELLOW = 0xFFFFCC55;
    static final int RED = 0xFFFF7777;
    static final int BLUE = 0xFF5599FF;
    static final int LINE_HEIGHT = 10;

    private static final int BAR_TRACK = 0xFF202020;

    private final GuiGraphicsExtractor graphics;
    private final Font font;

    UiPaint(GuiGraphicsExtractor graphics, Font font) {
        this.graphics = graphics;
        this.font = font;
    }

    void fill(int left, int top, int right, int bottom, int argb) {
        this.graphics.fill(left, top, right, bottom, argb);
    }

    void text(Component text, int x, int y, int argb) {
        this.graphics.text(this.font, text, x, y, argb, true);
    }

    void text(FormattedCharSequence text, int x, int y, int argb) {
        this.graphics.text(this.font, text, x, y, argb, true);
    }

    void centeredText(Component text, int centerX, int y, int argb) {
        this.graphics.centeredText(this.font, text, centerX, y, argb);
    }

    int textWidth(Component text) {
        return this.font.width(text);
    }

    /** @param fraction 0 to 1; values outside are clamped */
    void bar(int x, int y, int width, int height, double fraction, int color) {
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        fill(x, y, x + width, y + height, BAR_TRACK);
        fill(x, y, x + (int) Math.round(width * clamped), y + height, color);
    }

    /** A block sliding across the track, for work whose total is not known. */
    void sweep(int x, int y, int width, int height, long nowMillis, int color) {
        int block = Math.max(8, width / 4);
        int travel = width + block;
        int start = x - block + (int) (nowMillis % 1600L * travel / 1600L);
        fill(x, y, x + width, y + height, BAR_TRACK);
        fill(Math.max(x, start), y, Math.min(x + width, start + block), y + height, color);
    }

    /** At most {@code maxLines} lines wrapped to a width, styles kept; a cut last line ends in "...". */
    void paragraph(Component text, int x, int y, int width, int maxLines, int color) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            boolean isCut = i == maxLines - 1 && lines.size() > maxLines;
            if (isCut) {
                String rest = text.getString();
                line(Component.literal(rest.substring(Math.min(rest.length(), approximateOffset(lines, i)))), x, y, width, color);
            } else {
                text(lines.get(i), x, y, color);
            }
            y += LINE_HEIGHT;
        }
    }

    /** One line cut to the width with an ellipsis, for names and paths of any length. */
    void line(Component text, int x, int y, int width, int color) {
        if (this.font.width(text) <= width) {
            text(text, x, y, color);
            return;
        }
        String cut = this.font.plainSubstrByWidth(text.getString(), width - this.font.width("...")) + "...";
        text(Component.literal(cut).withStyle(text.getStyle()), x, y, color);
    }

    private int approximateOffset(List<FormattedCharSequence> lines, int upTo) {
        int characters = 0;
        for (int i = 0; i < upTo; i++) {
            StringBuilder plain = new StringBuilder();
            lines.get(i).accept((index, style, codePoint) -> {
                plain.appendCodePoint(codePoint);
                return true;
            });
            characters += plain.length() + 1;
        }
        return characters;
    }
}
