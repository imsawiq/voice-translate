package org.sawiq.voicetranslate.client.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.sawiq.voicetranslate.caption.Caption;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.client.compat.ClientAccess;

/**
 * Everything translated this session, newest at the bottom, for the sentence
 * that disappeared before it could be read.
 */
public final class HistoryScreen extends Screen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int TOP = 30;
    private static final int BOTTOM_MARGIN = 36;
    private static final int TEXT_WIDTH = 320;
    private static final int LINE_SPACING = 2;

    private record Line(FormattedCharSequence text, int colour) {
    }

    private final Screen parent;
    private final List<Line> lines = new ArrayList<>();
    /** Lines scrolled up from the bottom. */
    private int scroll;

    public HistoryScreen(Screen parent) {
        super(Component.translatable("voice-translate.history.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layoutLines();
        int y = this.height - 28;
        addRenderableWidget(Button.builder(Component.translatable("voice-translate.history.copy"), pressed -> copyAll())
                .bounds(this.width / 2 - 154, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("voice-translate.history.clear"), pressed -> {
            VoiceTranslateClient.INSTANCE.captions().clearHistory();
            this.scroll = 0;
            layoutLines();
        }).bounds(this.width / 2 - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), pressed -> onClose())
                .bounds(this.width / 2 + 54, y, 100, 20).build());
    }

    private void layoutLines() {
        this.lines.clear();
        VoiceTranslateClient translator = VoiceTranslateClient.INSTANCE;
        int width = Math.min(TEXT_WIDTH, this.width - 20);
        for (Caption caption : translator.captions().history()) {
            Component header = Component.literal(TIME.format(Instant.ofEpochMilli(caption.createdAt())) + "  ")
                    .withColor(0x808080)
                    .append(Component.literal(translator.displayName(caption)).withColor(0x7FD3FF))
                    .append(Component.literal(": " + caption.translation()).withColor(0xFFFFFF));
            this.font.split(header, width).forEach(text -> this.lines.add(new Line(text, 0xFFFFFFFF)));
            if (caption.isTranslated()) {
                this.font.split(Component.literal(caption.original()), width - 10)
                        .forEach(text -> this.lines.add(new Line(text, 0xFFA0A0A0)));
            }
        }
    }

    private void copyAll() {
        StringBuilder text = new StringBuilder();
        VoiceTranslateClient translator = VoiceTranslateClient.INSTANCE;
        for (Caption caption : translator.captions().history()) {
            text.append(TIME.format(Instant.ofEpochMilli(caption.createdAt()))).append(' ')
                    .append(translator.displayName(caption)).append(": ").append(caption.translation());
            if (caption.isTranslated()) {
                text.append("  (").append(caption.original()).append(')');
            }
            text.append(System.lineSeparator());
        }
        this.minecraft.keyboardHandler.setClipboard(text.toString());
    }

    private int visibleLineCount() {
        return Math.max(1, (this.height - TOP - BOTTOM_MARGIN) / (this.font.lineHeight + LINE_SPACING));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int maxScroll = Math.max(0, this.lines.size() - visibleLineCount());
        this.scroll = Math.clamp(this.scroll + (int) Math.signum(vertical) * 3, 0, maxScroll);
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        if (this.lines.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("voice-translate.history.empty"),
                    this.width / 2, this.height / 2 - 10, 0xFFA0A0A0);
            return;
        }
        int count = visibleLineCount();
        int end = this.lines.size() - this.scroll;
        int start = Math.max(0, end - count);
        int left = this.width / 2 - Math.min(TEXT_WIDTH, this.width - 20) / 2;
        int y = TOP;
        for (int i = start; i < end; i++) {
            Line line = this.lines.get(i);
            graphics.text(this.font, line.text, left, y, line.colour, true);
            y += this.font.lineHeight + LINE_SPACING;
        }
    }

    @Override
    public void onClose() {
        ClientAccess.openScreen(this.parent);
    }
}
