package org.sawiq.voicetranslate.client.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.caption.Caption;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;

/** Everything translated this session, newest first, for the sentence that vanished before it was read. */
final class HistoryTab implements SettingsTab {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int NAME_COLOUR = 0x7FD3FF;
    private static final int ENTRY_GAP = 4;

    @Override
    public void build(SettingsList list, Context context) {
        VoiceTranslateClient translator = VoiceTranslateClient.INSTANCE;
        List<Caption> history = translator.captions().history();

        list.row(
                Widgets.button(Component.translatable("voice-translate.history.copy"), pressed -> copy(history)),
                Widgets.button(Component.translatable("voice-translate.history.clear"), pressed -> {
                    translator.captions().clearHistory();
                    context.rebuild().run();
                }));

        if (history.isEmpty()) {
            list.text(Component.translatable("voice-translate.history.empty"), UiPaint.GREY);
            return;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Caption caption = history.get(i);
            Component line = Component.literal(TIME.format(Instant.ofEpochMilli(caption.createdAt())) + "  ")
                    .withColor(0x808080)
                    .append(Component.literal(translator.displayName(caption)).withColor(NAME_COLOUR))
                    .append(Component.literal(": " + caption.translation()).withColor(0xFFFFFF));
            list.text(line, UiPaint.WHITE);
            if (caption.isTranslated()) {
                list.text(Component.literal(caption.original()), UiPaint.GREY);
            }
            list.gap(ENTRY_GAP);
        }
    }

    private static void copy(List<Caption> history) {
        VoiceTranslateClient translator = VoiceTranslateClient.INSTANCE;
        StringBuilder text = new StringBuilder();
        for (Caption caption : history) {
            text.append(TIME.format(Instant.ofEpochMilli(caption.createdAt()))).append(' ')
                    .append(translator.displayName(caption)).append(": ").append(caption.translation());
            if (caption.isTranslated()) {
                text.append("  (").append(caption.original()).append(')');
            }
            text.append(System.lineSeparator());
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(text.toString());
    }
}
