package org.sawiq.voicetranslate.client.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Screen base class that hides the version-specific render and key-event
 * signatures behind stable hooks.
 *
 * <p>This is the Minecraft 26.x variant: the render entry point is
 * {@code extractRenderState(GuiGraphicsExtractor, ...)} and key events arrive
 * as a {@code KeyEvent}. A 1.21 build ships a copy whose entry point is
 * {@code render(GuiGraphics, ...)} and which takes raw key codes. Subclasses
 * see the same three hooks either way.</p>
 */
public abstract class CompatScreen extends Screen {
    protected CompatScreen(Component title) {
        super(title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        GuiCanvas canvas = new GuiCanvas(graphics, this.font);
        paintBehindWidgets(canvas);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        paintAboveWidgets(canvas, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return onKeyPressed(event.key()) || super.keyPressed(event);
    }

    /** Drawn before the widgets: backdrops and panels. */
    protected void paintBehindWidgets(GuiCanvas canvas) {
    }

    /** Drawn after the widgets: headings, text rows, scrollbars. */
    protected void paintAboveWidgets(GuiCanvas canvas, int mouseX, int mouseY) {
    }

    /**
     * @param keyCode GLFW key code
     * @return true when the key was handled and should not fall through
     */
    protected boolean onKeyPressed(int keyCode) {
        return false;
    }
}
