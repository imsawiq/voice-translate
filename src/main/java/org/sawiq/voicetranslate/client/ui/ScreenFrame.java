package org.sawiq.voicetranslate.client.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * The window the settings sit in, laid out like Plasmo Voice's own settings
 * and the voice changer studio, so the mods look like one family: the title
 * in the top left corner, a row of tab buttons under it with the open one
 * greyed out, a separator, the scrolling list, another separator, and the
 * footer.
 *
 * <p>Plain fills rather than vanilla's menu textures: texture drawing changes
 * between Minecraft versions, and fills look the same on every one.</p>
 */
final class ScreenFrame {
    static final int HEADER_HEIGHT = 62;
    static final int FOOTER_HEIGHT = 33;
    static final int MARGIN = 14;
    static final int WIDGET_HEIGHT = 20;

    private static final int TITLE_Y = 15;
    private static final int TABS_Y = 36;
    private static final int TAB_PADDING = 16;
    private static final int TAB_GAP = 4;
    private static final int SCREEN_DIM = 0x90000000;
    private static final int LIST_DIM = 0x50000000;
    private static final int SEPARATOR_LIGHT = 0x40FFFFFF;
    private static final int SEPARATOR_DARK = 0xC0000000;

    /** One tab in the header row. */
    record Tab(Component name, Component description, boolean isCurrent, Runnable open) {
    }

    private ScreenFrame() {
    }

    static int listTop() {
        return HEADER_HEIGHT;
    }

    static int listBottom(int screenHeight) {
        return Math.max(HEADER_HEIGHT + 24, screenHeight - FOOTER_HEIGHT);
    }

    static int footerY(int screenHeight) {
        return screenHeight - FOOTER_HEIGHT + (FOOTER_HEIGHT - WIDGET_HEIGHT) / 2;
    }

    /**
     * Adds the tab buttons, each as wide as its name the way Plasmo Voice
     * sizes them, squeezed evenly when a narrow window cannot fit them all.
     */
    static void addTabs(List<Tab> tabs, int screenWidth, ToIntFunction<Component> textWidth, Consumer<Button> addWidget) {
        int[] widths = new int[tabs.size()];
        int total = TAB_GAP * (tabs.size() - 1);
        for (int i = 0; i < tabs.size(); i++) {
            widths[i] = textWidth.applyAsInt(tabs.get(i).name()) + TAB_PADDING;
            total += widths[i];
        }
        int available = screenWidth - 2 * MARGIN;
        double squeeze = total > available ? (available - TAB_GAP * (tabs.size() - 1)) / (double) (total - TAB_GAP * (tabs.size() - 1)) : 1.0;

        int x = MARGIN;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int width = (int) Math.floor(widths[i] * squeeze);
            Button button = Button.builder(tab.name(), ignored -> tab.open().run())
                    .bounds(x, TABS_Y, width, WIDGET_HEIGHT)
                    .tooltip(Tooltip.create(tab.description()))
                    .build();
            button.active = !tab.isCurrent();
            addWidget.accept(button);
            x += width + TAB_GAP;
        }
    }

    /** The darkened screen and the list's own shade, drawn before the widgets. */
    static void paintBackground(UiPaint paint, int width, int height) {
        paint.fill(0, 0, width, height, SCREEN_DIM);
        paint.fill(0, listTop(), width, listBottom(height), LIST_DIM);
    }

    /** The title and the two separators, drawn over the widgets. */
    static void paintChrome(UiPaint paint, int width, int height, Component title) {
        paint.text(title, MARGIN, TITLE_Y, UiPaint.WHITE);
        int top = listTop();
        paint.fill(0, top - 2, width, top - 1, SEPARATOR_DARK);
        paint.fill(0, top - 1, width, top, SEPARATOR_LIGHT);
        int bottom = listBottom(height);
        paint.fill(0, bottom, width, bottom + 1, SEPARATOR_LIGHT);
        paint.fill(0, bottom + 1, width, bottom + 2, SEPARATOR_DARK);
    }
}
