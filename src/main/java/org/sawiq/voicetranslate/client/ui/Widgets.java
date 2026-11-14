package org.sawiq.voicetranslate.client.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** The widgets the settings tabs build, made the same way everywhere. */
final class Widgets {
    private static final int MAX_TEXT_LENGTH = 512;

    private Widgets() {
    }

    static <T> FormLayout.WidgetFactory cycle(Component label, List<T> values, T initial,
                                              Function<T, Component> names, Consumer<T> onChange) {
        return (x, y, width, height) -> CycleButton.builder(names, initial)
                .withValues(values)
                .create(x, y, width, height, label, (button, value) -> onChange.accept(value));
    }

    static FormLayout.WidgetFactory toggle(Component label, boolean initial, Consumer<Boolean> onChange) {
        return (x, y, width, height) -> CycleButton.onOffBuilder(initial)
                .create(x, y, width, height, label, (button, value) -> onChange.accept(value));
    }

    static FormLayout.WidgetFactory text(Font font, Component label, String initial, Consumer<String> onChange) {
        return (x, y, width, height) -> {
            EditBox box = new EditBox(font, x, y, width, height, label);
            box.setMaxLength(MAX_TEXT_LENGTH);
            box.setValue(initial);
            box.setResponder(onChange);
            return box;
        };
    }

    /**
     * A field for an API key that shows only its first characters, so the
     * key is not readable over the player's shoulder or on a stream.
     */
    static FormLayout.WidgetFactory secret(Font font, Component label, String initial, Consumer<String> onChange) {
        return (x, y, width, height) -> {
            EditBox box = new EditBox(font, x, y, width, height, label);
            box.setMaxLength(MAX_TEXT_LENGTH);
            box.setValue(initial);
            box.setHint(Component.translatable("voice-translate.settings.key_hint"));
            box.addFormatter(Widgets::mask);
            box.setResponder(onChange);
            return box;
        };
    }

    static FormLayout.WidgetFactory slider(int min, int max, int step, int initial,
                                           Function<Integer, Component> label, Consumer<Integer> onChange) {
        return (x, y, width, height) -> new IntSlider(x, y, width, height, min, max, step, initial,
                label::apply, onChange::accept);
    }

    /**
     * Masks the visible part of the key. The formatter is handed the slice
     * on screen and where it starts in the whole value.
     */
    private static FormattedCharSequence mask(String visible, int offset) {
        StringBuilder shown = new StringBuilder(visible.length());
        for (int i = 0; i < visible.length(); i++) {
            shown.append(offset + i < 4 ? visible.charAt(i) : '*');
        }
        return FormattedCharSequence.forward(shown.toString(), Style.EMPTY);
    }
}
