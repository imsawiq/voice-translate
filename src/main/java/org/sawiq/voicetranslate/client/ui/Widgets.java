package org.sawiq.voicetranslate.client.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * The controls of the settings pages, made the same way everywhere. They are
 * created at the origin; {@link SettingsList} places them.
 *
 * <p>The label sits in the list to the left of the control, so buttons show
 * only their value, as in Plasmo Voice's settings.</p>
 */
final class Widgets {
    private static final int MAX_TEXT_LENGTH = 512;
    private static final int VISIBLE_KEY_CHARACTERS = 4;

    private Widgets() {
    }

    static <T> CycleButton<T> cycle(Component label, List<T> values, T initial,
                                    Function<T, Component> names, Consumer<T> onChange) {
        return CycleButton.builder(names, initial)
                .withValues(values)
                .displayOnlyValue()
                .create(0, 0, SettingsList.CONTROL_WIDTH, SettingsList.WIDGET_HEIGHT, label,
                        (button, value) -> onChange.accept(value));
    }

    static CycleButton<Boolean> toggle(Component label, boolean initial, Consumer<Boolean> onChange) {
        return CycleButton.onOffBuilder(initial)
                .displayOnlyValue()
                .create(0, 0, SettingsList.CONTROL_WIDTH, SettingsList.WIDGET_HEIGHT, label,
                        (button, value) -> onChange.accept(value));
    }

    static Button button(Component label, Button.OnPress onPress) {
        return Button.builder(label, onPress).bounds(0, 0, SettingsList.CONTROL_WIDTH, SettingsList.WIDGET_HEIGHT).build();
    }

    static EditBox text(Font font, Component label, String initial, Consumer<String> onChange) {
        EditBox box = new EditBox(font, 0, 0, SettingsList.CONTROL_WIDTH, SettingsList.WIDGET_HEIGHT, label);
        box.setMaxLength(MAX_TEXT_LENGTH);
        box.setValue(initial);
        box.moveCursorToStart(false);
        box.setResponder(onChange);
        return box;
    }

    /**
     * A field for an API key that shows only its first characters, so the
     * key is not readable over the player's shoulder or on a stream.
     */
    static EditBox secret(Font font, Component label, String initial, Consumer<String> onChange) {
        EditBox box = text(font, label, initial, onChange);
        box.setHint(Component.translatable("voice-translate.settings.key_hint"));
        box.addFormatter(Widgets::mask);
        return box;
    }

    static IntSlider slider(int min, int max, int step, int initial, Function<Integer, Component> label, Consumer<Integer> onChange) {
        return new IntSlider(0, 0, SettingsList.CONTROL_WIDTH, SettingsList.WIDGET_HEIGHT, min, max, step, initial,
                label::apply, onChange::accept);
    }

    /** The formatter is handed the slice on screen and where it starts in the whole value. */
    private static FormattedCharSequence mask(String visible, int offset) {
        StringBuilder shown = new StringBuilder(visible.length());
        for (int i = 0; i < visible.length(); i++) {
            shown.append(offset + i < VISIBLE_KEY_CHARACTERS ? visible.charAt(i) : '*');
        }
        return FormattedCharSequence.forward(shown.toString(), Style.EMPTY);
    }
}
