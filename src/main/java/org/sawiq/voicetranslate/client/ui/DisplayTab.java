package org.sawiq.voicetranslate.client.ui;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.config.DisplaySettings;

/** Where and how subtitles appear. */
final class DisplayTab implements SettingsTab {
    @Override
    public void build(SettingsList list, Context context) {
        DisplaySettings display = context.config().display;
        list.category(Component.translatable("voice-translate.category.placement"));
        list.option(Component.translatable("voice-translate.settings.bubbles"),
                Component.translatable("voice-translate.settings.bubbles.desc"),
                Widgets.toggle(Component.translatable("voice-translate.settings.bubbles"), display.bubbles,
                        value -> display.bubbles = value));
        list.option(Component.translatable("voice-translate.settings.bubble_distance"),
                Widgets.slider(4, 128, 4, display.bubbleDistance,
                        value -> Component.translatable("voice-translate.unit.blocks", value),
                        value -> display.bubbleDistance = value));
        list.option(Component.translatable("voice-translate.settings.panel"),
                Component.translatable("voice-translate.settings.panel.desc"),
                Widgets.cycle(Component.translatable("voice-translate.settings.panel"),
                        List.of(DisplaySettings.PanelMode.values()), display.panelMode,
                        mode -> Component.translatable("voice-translate.settings.panel." + mode.name().toLowerCase(Locale.ROOT)),
                        mode -> display.panelMode = mode));
        list.option(Component.translatable("voice-translate.settings.panel_position"),
                Widgets.cycle(Component.translatable("voice-translate.settings.panel_position"),
                        List.of(DisplaySettings.PanelPosition.values()), display.panelPosition,
                        position -> Component.translatable("voice-translate.settings.panel_position."
                                + position.name().toLowerCase(Locale.ROOT)),
                        position -> display.panelPosition = position));

        list.category(Component.translatable("voice-translate.category.look"));
        list.option(Component.translatable("voice-translate.settings.show_original"),
                Component.translatable("voice-translate.settings.show_original.desc"),
                Widgets.toggle(Component.translatable("voice-translate.settings.show_original"), display.showOriginal,
                        value -> display.showOriginal = value));
        list.option(Component.translatable("voice-translate.settings.drafts"),
                Component.translatable("voice-translate.settings.drafts.desc"),
                Widgets.toggle(Component.translatable("voice-translate.settings.drafts"), display.showDrafts, value -> {
                    display.showDrafts = value;
                    context.apply().run();
                }));
        list.option(Component.translatable("voice-translate.settings.text_scale"),
                Widgets.slider(50, 200, 10, display.textScale,
                        value -> Component.literal(value + "%"),
                        value -> display.textScale = value));
        list.option(Component.translatable("voice-translate.settings.background"),
                Widgets.slider(0, 100, 5, display.backgroundOpacity,
                        value -> Component.literal(value + "%"),
                        value -> display.backgroundOpacity = value));
        list.option(Component.translatable("voice-translate.settings.chat_log"),
                Component.translatable("voice-translate.settings.chat_log.desc"),
                Widgets.toggle(Component.translatable("voice-translate.settings.chat_log"), display.chatLog,
                        value -> display.chatLog = value));
    }
}
