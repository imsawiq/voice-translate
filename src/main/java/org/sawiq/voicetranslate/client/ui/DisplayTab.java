package org.sawiq.voicetranslate.client.ui;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.config.DisplaySettings;

/** Where and how subtitles appear. */
final class DisplayTab implements SettingsTab {
    @Override
    public String titleKey() {
        return "voice-translate.tab.display";
    }

    @Override
    public void build(FormLayout form, Context context) {
        DisplaySettings display = context.config().display;
        form.half(Widgets.toggle(Component.translatable("voice-translate.settings.bubbles"), display.bubbles,
                value -> display.bubbles = value));
        form.half(Widgets.cycle(Component.translatable("voice-translate.settings.panel"),
                List.of(DisplaySettings.PanelMode.values()), display.panelMode,
                mode -> Component.translatable("voice-translate.settings.panel." + mode.name().toLowerCase(Locale.ROOT)),
                mode -> display.panelMode = mode));
        form.half(Widgets.cycle(Component.translatable("voice-translate.settings.panel_position"),
                List.of(DisplaySettings.PanelPosition.values()), display.panelPosition,
                position -> Component.translatable("voice-translate.settings.panel_position." + position.name().toLowerCase(Locale.ROOT)),
                position -> display.panelPosition = position));
        form.half(Widgets.toggle(Component.translatable("voice-translate.settings.show_original"), display.showOriginal,
                value -> display.showOriginal = value));
        form.half(Widgets.toggle(Component.translatable("voice-translate.settings.drafts"), display.showDrafts, value -> {
            display.showDrafts = value;
            context.apply().run();
        }));
        form.half(Widgets.toggle(Component.translatable("voice-translate.settings.chat_log"), display.chatLog,
                value -> display.chatLog = value));
        form.half(Widgets.slider(50, 200, 10, display.textScale,
                value -> Component.translatable("voice-translate.settings.text_scale", value),
                value -> display.textScale = value));
        form.half(Widgets.slider(0, 100, 5, display.backgroundOpacity,
                value -> Component.translatable("voice-translate.settings.background", value),
                value -> display.backgroundOpacity = value));
        form.full(Widgets.slider(4, 128, 4, display.bubbleDistance,
                value -> Component.translatable("voice-translate.settings.bubble_distance", value),
                value -> display.bubbleDistance = value));
    }
}
