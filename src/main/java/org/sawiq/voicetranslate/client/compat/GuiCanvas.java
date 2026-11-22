package org.sawiq.voicetranslate.client.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The drawing the mod's screens and subtitles need, expressed once so they
 * do not have to know which Minecraft rendering API they run on.
 *
 * <p>This is the Minecraft 26.x variant, backed by {@code GuiGraphicsExtractor}
 * and its 2D matrix stack. A 1.21 build ships a copy backed by
 * {@code GuiGraphics}; the methods here stay the same in every copy, so
 * nothing outside this package changes.</p>
 */
public final class GuiCanvas {
    private final GuiGraphicsExtractor graphics;
    private final Font font;

    public GuiCanvas(GuiGraphicsExtractor graphics, Font font) {
        this.graphics = graphics;
        this.font = font;
    }

    public Font font() {
        return this.font;
    }

    /** The GUI-scaled width of the screen. */
    public int width() {
        return this.graphics.guiWidth();
    }

    /** The GUI-scaled height of the screen. */
    public int height() {
        return this.graphics.guiHeight();
    }

    /** Fills a rectangle with a packed ARGB colour. */
    public void fill(int left, int top, int right, int bottom, int argb) {
        this.graphics.fill(left, top, right, bottom, argb);
    }

    /** Text with a shadow, left edge at x. */
    public void text(Component text, int x, int y, int argb) {
        this.graphics.text(this.font, text, x, y, argb, true);
    }

    /** Text with a shadow, left edge at x. */
    public void text(FormattedCharSequence text, int x, int y, int argb) {
        this.graphics.text(this.font, text, x, y, argb, true);
    }

    public void centeredText(Component text, int centerX, int y, int argb) {
        this.graphics.centeredText(this.font, text, centerX, y, argb);
    }

    /** Saves the current transform; every push is paired with a {@link #pop}. */
    public void push() {
        this.graphics.pose().pushMatrix();
    }

    public void pop() {
        this.graphics.pose().popMatrix();
    }

    public void translate(float x, float y) {
        this.graphics.pose().translate(x, y);
    }

    /** Scales both axes alike. */
    public void scale(float factor) {
        this.graphics.pose().scale(factor);
    }
}
