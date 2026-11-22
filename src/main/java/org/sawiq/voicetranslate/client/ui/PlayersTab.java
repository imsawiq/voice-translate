package org.sawiq.voicetranslate.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;

/** Turns translation off for single players, such as a friend who speaks your language anyway. */
final class PlayersTab implements SettingsTab {
    private record Entry(UUID id, String name) {
    }

    @Override
    public void build(SettingsList list, Context context) {
        List<Entry> players = onlinePlayers();
        list.category(Component.translatable("voice-translate.category.players"));
        if (players.isEmpty()) {
            list.text(Component.translatable("voice-translate.players.empty"), UiPaint.GREY);
            return;
        }
        VoiceTranslateClient translator = VoiceTranslateClient.INSTANCE;
        for (Entry player : players) {
            list.option(Component.literal(player.name()),
                    Widgets.cycle(Component.literal(player.name()), List.of(Boolean.TRUE, Boolean.FALSE),
                            !translator.isPlayerMuted(player.id()),
                            isTranslated -> isTranslated
                                    ? Component.translatable("voice-translate.players.translated").withColor(0x66DD66)
                                    : Component.translatable("voice-translate.players.muted").withColor(0xFF7777),
                            isTranslated -> translator.setPlayerMuted(player.id(), !isTranslated)));
        }
        list.text(Component.translatable("voice-translate.players.hint"), UiPaint.DIM);
    }

    private static List<Entry> onlinePlayers() {
        Minecraft client = Minecraft.getInstance();
        List<Entry> players = new ArrayList<>();
        ClientPacketListener connection = client.getConnection();
        UUID self = client.player == null ? null : client.player.getUUID();
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
}
