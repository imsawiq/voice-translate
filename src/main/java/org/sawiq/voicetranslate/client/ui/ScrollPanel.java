package org.sawiq.voicetranslate.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Vertical scrolling for widgets placed at fixed positions.
 *
 * <p>Widgets keep the position they were laid out at; the panel offsets them
 * and hides the ones that fall outside the viewport. Hidden widgets are also
 * deactivated, or a click near the edge of the list would land on a control
 * scrolled out of view.</p>
 */
final class ScrollPanel {
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int MINIMUM_THUMB_HEIGHT = 18;
    private static final int TRACK_COLOR = 0x60000000;
    private static final int THUMB_COLOR = 0xFFE0E0E0;

    private final List<Entry> entries = new ArrayList<>();
    private int top;
    private int bottom;
    private int contentEnd;
    private int scrollOffset;
    private int maxScroll;

    void add(AbstractWidget widget) {
        this.entries.add(new Entry(widget, widget.getY()));
    }

    /**
     * @param contentEnd where the list's own rows end, which may be below the
     *                   last widget when it ends in text
     */
    void layout(int top, int bottom, int contentEnd) {
        this.top = top;
        this.bottom = bottom;
        this.contentEnd = contentEnd;
        int end = contentEnd;
        for (Entry entry : this.entries) {
            end = Math.max(end, entry.baseY + entry.widget.getHeight());
        }
        this.maxScroll = Math.max(0, end - bottom);
        this.scrollOffset = Math.clamp(this.scrollOffset, 0, this.maxScroll);
        apply();
    }

    boolean isScrollable() {
        return this.maxScroll > 0;
    }

    /** @return true when the panel took the scroll */
    boolean scrollBy(int pixels) {
        if (this.maxScroll <= 0) {
            return false;
        }
        int updated = Math.clamp(this.scrollOffset + pixels, 0, this.maxScroll);
        if (updated != this.scrollOffset) {
            this.scrollOffset = updated;
            apply();
        }
        return true;
    }

    int scrollOffset() {
        return this.scrollOffset;
    }

    void setScrollOffset(int offset) {
        this.scrollOffset = offset;
        layout(this.top, this.bottom, this.contentEnd);
    }

    int viewportHeight() {
        return Math.max(1, this.bottom - this.top);
    }

    void paintScrollbar(UiPaint paint, int x) {
        if (this.maxScroll <= 0) {
            return;
        }
        int track = viewportHeight();
        int thumb = Math.max(MINIMUM_THUMB_HEIGHT, track * track / (track + this.maxScroll));
        int thumbY = this.top + this.scrollOffset * Math.max(1, track - thumb) / this.maxScroll;
        paint.fill(x, this.top, x + SCROLLBAR_WIDTH, this.bottom, TRACK_COLOR);
        paint.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumb, THUMB_COLOR);
    }

    /**
     * Hides what scrolled out of view and brings back only what this panel
     * hid itself: a control the screen greyed out on purpose stays greyed
     * when it scrolls back in.
     */
    private void apply() {
        for (Entry entry : this.entries) {
            AbstractWidget widget = entry.widget;
            int y = entry.baseY - this.scrollOffset;
            widget.setY(y);
            boolean isInView = y >= this.top && y + widget.getHeight() <= this.bottom;
            if (!isInView && !entry.isHiddenByScroll) {
                entry.isHiddenByScroll = true;
                entry.wasVisible = widget.visible;
                entry.wasActive = widget.active;
                widget.visible = false;
                widget.active = false;
            } else if (isInView && entry.isHiddenByScroll) {
                entry.isHiddenByScroll = false;
                widget.visible = entry.wasVisible;
                widget.active = entry.wasActive;
            }
        }
    }

    private static final class Entry {
        private final AbstractWidget widget;
        private final int baseY;
        private boolean isHiddenByScroll;
        private boolean wasVisible;
        private boolean wasActive;

        private Entry(AbstractWidget widget, int baseY) {
            this.widget = widget;
            this.baseY = baseY;
        }
    }
}
