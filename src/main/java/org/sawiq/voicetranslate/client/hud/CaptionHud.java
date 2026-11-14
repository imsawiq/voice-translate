package org.sawiq.voicetranslate.client.hud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.sawiq.voicetranslate.caption.Caption;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.client.compat.ClientAccess;
import org.sawiq.voicetranslate.config.DisplaySettings;

/**
 * Draws the subtitles: a bubble above each visible speaker, and a panel near
 * the bottom of the screen for everyone else, such as players behind you,
 * too far away to read, or in a group call on the other side of the world.
 */
public final class CaptionHud {
    private static final int BUBBLE_WIDTH = 170;
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_ROWS = 4;
    private static final int PANEL_BOTTOM_MARGIN = 72;
    private static final int PANEL_TOP_MARGIN = 24;
    private static final int ROW_GAP = 2;
    private static final double HEAD_CLEARANCE = 0.5;
    private static final double FULL_SIZE_WITHIN = 8.0;
    private static final float SMALLEST_BUBBLE = 0.6f;

    private CaptionHud() {
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        VoiceTranslateClient translator = VoiceTranslateClient.INSTANCE;
        if (client.player == null || client.level == null || !translator.config().enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Caption> captions = translator.captions().visible(now);
        if (captions.isEmpty()) {
            return;
        }
        DisplaySettings display = translator.config().display;
        float scale = display.textScale / 100f;

        Set<UUID> inBubbles = display.bubbles
                ? drawBubbles(graphics, client, captions, delta, display, scale, now)
                : Set.of();
        if (display.panelMode != DisplaySettings.PanelMode.HIDDEN) {
            List<Caption> forPanel = new ArrayList<>();
            for (Caption caption : captions) {
                boolean isShownAbove = inBubbles.contains(caption.speakerId());
                if (display.panelMode == DisplaySettings.PanelMode.EVERYONE || !isShownAbove) {
                    forPanel.add(caption);
                }
            }
            drawPanel(graphics, client, forPanel, display, scale, now);
        }
    }

    /** Draws the newest caption of every speaker who is on screen; returns who got one. */
    private static Set<UUID> drawBubbles(GuiGraphicsExtractor graphics, Minecraft client, List<Caption> captions,
                                         DeltaTracker delta, DisplaySettings display, float scale, long now) {
        Map<UUID, Caption> newest = new LinkedHashMap<>();
        for (Caption caption : captions) {
            newest.put(caption.speakerId(), caption);
        }
        Camera camera = ClientAccess.camera();
        float partialTick = delta.getGameTimeDeltaPartialTick(true);
        float fov = camera.getFov() > 1f ? camera.getFov() : client.options.fov().get().floatValue();
        Set<UUID> drawn = new HashSet<>();

        for (Caption caption : newest.values()) {
            Player speaker = client.level.getPlayerByUUID(caption.speakerId());
            if (speaker == null || speaker.isInvisibleTo(client.player)) {
                continue;
            }
            Vec3 head = speaker.getPosition(partialTick).add(0, speaker.getBbHeight() + HEAD_CLEARANCE, 0);
            Optional<ScreenProjector.Projection> projection =
                    ScreenProjector.project(camera, fov, head, graphics.guiWidth(), graphics.guiHeight());
            if (projection.isEmpty() || projection.get().distance() > display.bubbleDistance) {
                continue;
            }
            float bubbleScale = scale * sizeAtDistance(projection.get().distance(), display.bubbleDistance);
            CaptionBlock block = new CaptionBlock(client.font, caption, null, display.showOriginal, BUBBLE_WIDTH);

            graphics.pose().pushMatrix();
            graphics.pose().translate(projection.get().x(), projection.get().y());
            graphics.pose().scale(bubbleScale);
            block.draw(graphics, -block.width() / 2, -block.height(), caption.opacityAt(now), display.backgroundOpacity, true);
            graphics.pose().popMatrix();
            drawn.add(caption.speakerId());
        }
        return drawn;
    }

    /** Full size nearby, shrinking towards the far limit so distant bubbles do not cover the view. */
    private static float sizeAtDistance(double distance, int limit) {
        if (distance <= FULL_SIZE_WITHIN) {
            return 1f;
        }
        double progress = (distance - FULL_SIZE_WITHIN) / Math.max(1.0, limit - FULL_SIZE_WITHIN);
        return (float) (1.0 - Math.min(1.0, progress) * (1.0 - SMALLEST_BUBBLE));
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, Minecraft client, List<Caption> captions,
                                  DisplaySettings display, float scale, long now) {
        if (captions.isEmpty()) {
            return;
        }
        List<Caption> rows = captions.subList(Math.max(0, captions.size() - PANEL_ROWS), captions.size());
        List<CaptionBlock> blocks = new ArrayList<>();
        for (Caption caption : rows) {
            String name = VoiceTranslateClient.INSTANCE.displayName(caption);
            blocks.add(new CaptionBlock(client.font, caption, CaptionBlock.header(name, caption), display.showOriginal, PANEL_WIDTH));
        }

        float centerX = graphics.guiWidth() / 2f;
        boolean isAtBottom = display.panelPosition == DisplaySettings.PanelPosition.BOTTOM;
        graphics.pose().pushMatrix();
        if (isAtBottom) {
            graphics.pose().translate(centerX, graphics.guiHeight() - PANEL_BOTTOM_MARGIN);
            graphics.pose().scale(scale);
            int y = 0;
            for (int i = blocks.size() - 1; i >= 0; i--) {
                CaptionBlock block = blocks.get(i);
                y -= block.height();
                block.draw(graphics, -block.width() / 2, y, rows.get(i).opacityAt(now), display.backgroundOpacity, false);
                y -= ROW_GAP;
            }
        } else {
            graphics.pose().translate(centerX, PANEL_TOP_MARGIN);
            graphics.pose().scale(scale);
            int y = 0;
            for (int i = 0; i < blocks.size(); i++) {
                CaptionBlock block = blocks.get(i);
                block.draw(graphics, -block.width() / 2, y, rows.get(i).opacityAt(now), display.backgroundOpacity, false);
                y += block.height() + ROW_GAP;
            }
        }
        graphics.pose().popMatrix();
    }
}
