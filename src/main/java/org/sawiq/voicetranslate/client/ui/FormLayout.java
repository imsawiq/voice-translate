package org.sawiq.voicetranslate.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Places a settings tab's widgets in the two-column grid vanilla's options
 * screens use, and keeps the text lines between them, which the screen draws.
 */
final class FormLayout {
    static final int ROW_HEIGHT = 20;
    static final int ROW_SPACING = 4;
    static final int COLUMN_WIDTH = 150;
    static final int COLUMN_GAP = 10;
    static final int FULL_WIDTH = COLUMN_WIDTH * 2 + COLUMN_GAP;

    /** A line of text placed in the form. */
    private record TextLine(FormattedCharSequence text, int x, int y, int colour) {
    }

    /** Text worked out again every frame, such as the engine's progress. */
    private record LiveLine(Supplier<Component> text, int x, int y, int colour) {
    }

    private final Consumer<AbstractWidget> addWidget;
    private final Font font;
    private final int left;
    private final List<TextLine> lines = new ArrayList<>();
    private final List<LiveLine> liveLines = new ArrayList<>();
    private final List<Runnable> tickers = new ArrayList<>();
    private int y;
    private boolean isRightColumnFree;

    FormLayout(Consumer<AbstractWidget> addWidget, Font font, int centerX, int top) {
        this.addWidget = addWidget;
        this.font = font;
        this.left = centerX - FULL_WIDTH / 2;
        this.y = top;
    }

    /** Builds a widget across both columns. */
    void full(WidgetFactory factory) {
        endHalfRow();
        this.addWidget.accept(factory.create(this.left, this.y, FULL_WIDTH, ROW_HEIGHT));
        this.y += ROW_HEIGHT + ROW_SPACING;
    }

    /** Builds a widget in the next free column. */
    void half(WidgetFactory factory) {
        if (this.isRightColumnFree) {
            this.addWidget.accept(factory.create(this.left + COLUMN_WIDTH + COLUMN_GAP, this.y, COLUMN_WIDTH, ROW_HEIGHT));
            this.isRightColumnFree = false;
            this.y += ROW_HEIGHT + ROW_SPACING;
        } else {
            this.addWidget.accept(factory.create(this.left, this.y, COLUMN_WIDTH, ROW_HEIGHT));
            this.isRightColumnFree = true;
        }
    }

    /** A label in the left column with a widget beside it, for text fields. */
    void labelled(Component label, WidgetFactory factory) {
        endHalfRow();
        int textY = this.y + (ROW_HEIGHT - this.font.lineHeight) / 2 + 1;
        this.lines.add(new TextLine(label.getVisualOrderText(), this.left, textY, 0xFFE0E0E0));
        this.addWidget.accept(factory.create(this.left + COLUMN_WIDTH + COLUMN_GAP, this.y, COLUMN_WIDTH, ROW_HEIGHT));
        this.y += ROW_HEIGHT + ROW_SPACING;
    }

    /** Wrapped text across the whole width. */
    void text(Component text, int colour) {
        endHalfRow();
        for (FormattedCharSequence line : this.font.split(text, FULL_WIDTH)) {
            this.lines.add(new TextLine(line, this.left, this.y, colour));
            this.y += this.font.lineHeight + 2;
        }
        this.y += ROW_SPACING;
    }

    /** One line of text that may change while the screen is open; it is cut, not wrapped. */
    void liveText(Supplier<Component> text, int colour) {
        endHalfRow();
        this.liveLines.add(new LiveLine(text, this.left, this.y, colour));
        this.y += this.font.lineHeight + 2 + ROW_SPACING;
    }

    /** Something to refresh every tick, such as a button whose label follows the engine. */
    void everyTick(Runnable ticker) {
        this.tickers.add(ticker);
        ticker.run();
    }

    void tick() {
        this.tickers.forEach(Runnable::run);
    }

    void draw(GuiGraphicsExtractor graphics) {
        for (TextLine line : this.lines) {
            graphics.text(this.font, line.text, line.x, line.y, line.colour, true);
        }
        for (LiveLine line : this.liveLines) {
            // Styled, so a failure keeps its red; only the first line is shown.
            List<FormattedCharSequence> wrapped = this.font.split(line.text.get(), FULL_WIDTH);
            if (!wrapped.isEmpty()) {
                graphics.text(this.font, wrapped.getFirst(), line.x, line.y, line.colour, true);
            }
        }
    }

    private void endHalfRow() {
        if (this.isRightColumnFree) {
            this.isRightColumnFree = false;
            this.y += ROW_HEIGHT + ROW_SPACING;
        }
    }

    @FunctionalInterface
    interface WidgetFactory {
        AbstractWidget create(int x, int y, int width, int height);
    }
}
