package org.sawiq.voicetranslate.client.ui;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider over whole numbers, snapped to a step, reporting each change. */
final class IntSlider extends AbstractSliderButton {
    private final int min;
    private final int max;
    private final int step;
    private final IntFunction<Component> label;
    private final IntConsumer onChange;

    IntSlider(int x, int y, int width, int height, int min, int max, int step, int initial,
              IntFunction<Component> label, IntConsumer onChange) {
        super(x, y, width, height, Component.empty(), (initial - min) / (double) (max - min));
        this.min = min;
        this.max = max;
        this.step = step;
        this.label = label;
        this.onChange = onChange;
        updateMessage();
    }

    int current() {
        int raw = (int) Math.round(this.min + this.value * (this.max - this.min));
        return Math.clamp(Math.round(raw / (float) this.step) * this.step, this.min, this.max);
    }

    @Override
    protected void updateMessage() {
        setMessage(this.label.apply(current()));
    }

    @Override
    protected void applyValue() {
        this.onChange.accept(current());
    }
}
