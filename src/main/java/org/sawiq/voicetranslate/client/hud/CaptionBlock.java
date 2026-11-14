package org.sawiq.voicetranslate.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.sawiq.voicetranslate.caption.Caption;

/**
 * One caption laid out as wrapped lines: an optional header with the
 * speaker's name, the translation, and the original in smaller, dimmer text.
 * Measured once, then drawn as a bubble or a panel row.
 */
final class CaptionBlock {
    static final int PADDING = 3;
    private static final float ORIGINAL_SCALE = 0.8f;
    private static final int LINE_GAP = 1;

    private record Line(FormattedCharSequence text, int colour, float scale) {
    }

    private final List<Line> lines = new ArrayList<>();
    private final Font font;
    private int width;
    private int height;

    /**
     * @param header    the speaker line, or null for a bubble, which already sits above its speaker
     * @param maxWidth  wrap width in unscaled GUI pixels
     */
    CaptionBlock(Font font, Caption caption, Component header, boolean showOriginal, int maxWidth) {
        this.font = font;
        if (header != null) {
            add(font.split(header, maxWidth), 0xFFFFFF, 1f);
        }
        MutableComponent translation = Component.literal(caption.translation());
        if (!caption.isFinal()) {
            translation = translation.withStyle(ChatFormatting.ITALIC);
        }
        add(font.split(translation, maxWidth), caption.isFinal() ? CaptionStyle.TRANSLATION : CaptionStyle.DRAFT, 1f);
        if (showOriginal && caption.isTranslated() && caption.isFinal()) {
            int originalWidth = Math.round(maxWidth / ORIGINAL_SCALE);
            add(font.split(Component.literal(caption.original()), originalWidth), CaptionStyle.ORIGINAL, ORIGINAL_SCALE);
        }
    }

    /** "Steve · RU", the speaker's name in their colour and the language they spoke. */
    static Component header(String name, Caption caption) {
        MutableComponent header = Component.literal(name).withColor(CaptionStyle.nameColour(caption.speakerId()));
        if (caption.isTranslated() && !caption.sourceLanguage().isEmpty()) {
            header.append(Component.literal(" · " + caption.sourceLanguage().toUpperCase(Locale.ROOT))
                    .withColor(CaptionStyle.LANGUAGE_TAG));
        }
        return header;
    }

    int width() {
        return this.width + PADDING * 2;
    }

    int height() {
        return this.height + PADDING * 2;
    }

    /** Draws with the top-left corner at (x, y). */
    void draw(GuiGraphicsExtractor graphics, int x, int y, float opacity, int backgroundPercent, boolean isCentered) {
        graphics.fill(x, y, x + width(), y + height(), CaptionStyle.background(opacity, backgroundPercent));
        int lineY = y + PADDING;
        for (Line line : this.lines) {
            int lineWidth = Math.round(this.font.width(line.text) * line.scale);
            int lineX = isCentered ? x + (width() - lineWidth) / 2 : x + PADDING;
            int colour = CaptionStyle.withOpacity(line.colour, opacity);
            if (line.scale == 1f) {
                graphics.text(this.font, line.text, lineX, lineY, colour, true);
            } else {
                graphics.pose().pushMatrix();
                graphics.pose().translate(lineX, lineY);
                graphics.pose().scale(line.scale);
                graphics.text(this.font, line.text, 0, 0, colour, true);
                graphics.pose().popMatrix();
            }
            lineY += Math.round(this.font.lineHeight * line.scale) + LINE_GAP;
        }
    }

    private void add(List<FormattedCharSequence> wrapped, int colour, float scale) {
        for (FormattedCharSequence text : wrapped) {
            this.lines.add(new Line(text, colour, scale));
            this.width = Math.max(this.width, Math.round(this.font.width(text) * scale));
            this.height += Math.round(this.font.lineHeight * scale) + LINE_GAP;
        }
    }
}
