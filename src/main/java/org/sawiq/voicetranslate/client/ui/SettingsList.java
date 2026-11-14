package org.sawiq.voicetranslate.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * The rows of a settings page, laid out like Plasmo Voice's settings list
 * and the voice changer studio: section titles centred in a row of their
 * own, then options with the label on the left and the control on the right,
 * every row 24 pixels tall in a column at most 303 wide.
 *
 * <p>Rows are added top to bottom while the screen builds its widgets; the
 * list places each widget, scrolls them together, and draws the labels and
 * text that go with them.</p>
 */
final class SettingsList {
    static final int ROW_HEIGHT = 24;
    static final int CONTROL_WIDTH = 124;
    static final int WIDGET_HEIGHT = 20;

    private static final int MAX_WIDTH = 303;
    private static final int GAP = 4;
    private static final int TOP_PADDING = 4;
    private static final int LABEL_Y = (ROW_HEIGHT - 8) / 2;
    private static final int SCROLLBAR_GAP = 6;

    /** Something drawn in a row: a label, a title, text, or a picture such as a progress bar. */
    @FunctionalInterface
    interface Painter {
        void paint(UiPaint paint, int x, int y, int width);
    }

    private record Painted(int y, int height, Painter painter) {
    }

    private final ScrollPanel scroll = new ScrollPanel();
    private final List<Painted> painted = new ArrayList<>();
    private final List<Runnable> tickers = new ArrayList<>();
    private final Consumer<AbstractWidget> addWidget;
    private final int left;
    private final int width;
    private int y = ScreenFrame.listTop() + TOP_PADDING;
    private int bottom;

    SettingsList(int screenWidth, Consumer<AbstractWidget> addWidget) {
        this.addWidget = addWidget;
        this.width = Math.min(MAX_WIDTH, screenWidth - 2 * ScreenFrame.MARGIN);
        this.left = (screenWidth - this.width) / 2;
    }

    int width() {
        return this.width;
    }

    /** A section title, centred. */
    void category(Component title) {
        this.painted.add(new Painted(this.y, ROW_HEIGHT, (paint, x, top, width) ->
                paint.centeredText(title, x + width / 2, top + LABEL_Y, UiPaint.WHITE)));
        this.y += ROW_HEIGHT;
    }

    /** A label on the left and a control on the right. */
    <T extends AbstractWidget> T option(Component label, T control) {
        return option(label, null, control);
    }

    /** @param description shown when the pointer rests on the control; may be null */
    <T extends AbstractWidget> T option(Component label, Component description, T control) {
        int labelWidth = this.width - CONTROL_WIDTH - GAP;
        this.painted.add(new Painted(this.y, ROW_HEIGHT, (paint, x, top, width) ->
                paint.line(label, x, top + LABEL_Y, labelWidth, UiPaint.WHITE)));
        if (description != null) {
            control.setTooltip(Tooltip.create(description));
        }
        place(control, this.left + this.width - CONTROL_WIDTH, CONTROL_WIDTH);
        this.y += ROW_HEIGHT;
        return control;
    }

    /** Widgets side by side across the whole row, sharing its width. */
    void row(AbstractWidget... widgets) {
        int each = (this.width - GAP * (widgets.length - 1)) / widgets.length;
        for (int i = 0; i < widgets.length; i++) {
            place(widgets[i], this.left + i * (each + GAP), each);
        }
        this.y += ROW_HEIGHT;
    }

    /** Wrapped text in a block of a fixed number of lines, so rows under it do not jump when it changes. */
    void text(Supplier<Component> text, IntSupplier color, int lines) {
        int height = lines * UiPaint.LINE_HEIGHT + 4;
        this.painted.add(new Painted(this.y, height, (paint, x, top, width) ->
                paint.paragraph(text.get(), x, top + 2, width, lines, color.getAsInt())));
        this.y += height;
    }

    void text(Component text, int color, int lines) {
        text(() -> text, () -> color, lines);
    }

    /** A row of the given height drawn by the caller. */
    void custom(int height, Painter painter) {
        this.painted.add(new Painted(this.y, height, painter));
        this.y += height;
    }

    void gap(int pixels) {
        this.y += pixels;
    }

    /** Something to refresh every tick, such as a button that follows the engine's state. */
    void everyTick(Runnable ticker) {
        this.tickers.add(ticker);
        ticker.run();
    }

    void tick() {
        this.tickers.forEach(Runnable::run);
    }

    /** Ends the layout; called once every row is in. */
    void finish(int screenHeight, int scrollOffset) {
        this.bottom = ScreenFrame.listBottom(screenHeight);
        this.scroll.layout(ScreenFrame.listTop(), this.bottom, this.y + TOP_PADDING);
        if (scrollOffset > 0) {
            this.scroll.setScrollOffset(scrollOffset);
        }
    }

    boolean scrollBy(int pixels) {
        return this.scroll.scrollBy(pixels);
    }

    int scrollOffset() {
        return this.scroll.scrollOffset();
    }

    int viewportHeight() {
        return this.scroll.viewportHeight();
    }

    /** Labels, titles and text of the rows in view, then the scrollbar beside the list. */
    void paint(UiPaint paint) {
        int offset = this.scroll.scrollOffset();
        for (Painted row : this.painted) {
            int top = row.y() - offset;
            if (top >= ScreenFrame.listTop() && top + row.height() <= this.bottom + 2) {
                row.painter().paint(paint, this.left, top, this.width);
            }
        }
        this.scroll.paintScrollbar(paint, this.left + this.width + SCROLLBAR_GAP);
    }

    private void place(AbstractWidget widget, int x, int widgetWidth) {
        widget.setX(x);
        widget.setY(this.y + (ROW_HEIGHT - WIDGET_HEIGHT) / 2);
        widget.setWidth(widgetWidth);
        this.scroll.add(widget);
        this.addWidget.accept(widget);
    }
}
