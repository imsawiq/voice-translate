package org.sawiq.voicetranslate.client.compat;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.sawiq.voicetranslate.client.hud.CaptionHud;

/**
 * The subtitles' entry point from each loader's HUD layer API, which hands
 * over this version's drawing context; the subtitles themselves only see a
 * {@link GuiCanvas}.
 */
public final class HudLayer {
    private HudLayer() {
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        CaptionHud.render(new GuiCanvas(graphics, Minecraft.getInstance().font), delta.getGameTimeDeltaPartialTick(true));
    }
}
