package org.sawiq.voicetranslate.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.client.compat.ClientAccess;

/** Turns translation off for single players, such as a friend who speaks your language anyway. */
public final class PlayersScreen extends Screen {
    private static final int TOP = 34;
    private static final int ROW = 24;
    private static final int BUTTON_WIDTH = 220;

    private record Entry(UUID id, String name) {
    }

    private final Screen parent;
    private int page;

    public PlayersScreen(Screen parent) {
        super(Component.translatable("voice-translate.players.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<Entry> players = onlinePlayers();
        int perPage = Math.max(1, (this.height - TOP - 40) / ROW);
        int pages = Math.max(1, (players.size() + perPage - 1) / perPage);
        this.page = Math.clamp(this.page, 0, pages - 1);

        VoiceTranslateClient translator = VoiceTranslateClient.INSTANCE;
        int y = TOP;
        for (Entry player : players.subList(this.page * perPage, Math.min(players.size(), (this.page + 1) * perPage))) {
            boolean isMuted = translator.isPlayerMuted(player.id);
            Component label = Component.literal(player.name + ": ").append(isMuted
                    ? Component.translatable("voice-translate.players.muted").withStyle(ChatFormatting.RED)
                    : Component.translatable("voice-translate.players.translated").withStyle(ChatFormatting.GREEN));
            addRenderableWidget(Button.builder(label, pressed -> {
                translator.setPlayerMuted(player.id, !translator.isPlayerMuted(player.id));
                rebuildWidgets();
            }).bounds(this.width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, 20).build());
            y += ROW;
        }

        int footerY = this.height - 28;
        Button previous = Button.builder(Component.literal("<"), pressed -> {
            this.page--;
            rebuildWidgets();
        }).bounds(this.width / 2 - 104, footerY, 20, 20).build();
        previous.active = this.page > 0;
        addRenderableWidget(previous);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), pressed -> onClose())
                .bounds(this.width / 2 - 80, footerY, 160, 20).build());
        Button next = Button.builder(Component.literal(">"), pressed -> {
            this.page++;
            rebuildWidgets();
        }).bounds(this.width / 2 + 84, footerY, 20, 20).build();
        next.active = this.page < pages - 1;
        addRenderableWidget(next);
    }

    private List<Entry> onlinePlayers() {
        List<Entry> players = new ArrayList<>();
        ClientPacketListener connection = this.minecraft.getConnection();
        UUID self = this.minecraft.player == null ? null : this.minecraft.player.getUUID();
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                UUID id = info.getProfile().id();
                if (!id.equals(self)) {
                    players.add(new Entry(id, info.getProfile().name()));
                }
            }
        }
        players.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return players;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        if (onlinePlayers().isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("voice-translate.players.empty"),
                    this.width / 2, this.height / 2 - 10, 0xFFA0A0A0);
        }
    }

    @Override
    public void onClose() {
        ClientAccess.openScreen(this.parent);
    }
}
