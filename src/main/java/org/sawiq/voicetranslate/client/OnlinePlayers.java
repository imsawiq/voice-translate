package org.sawiq.voicetranslate.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Who is on the server, copied from the tab list once a tick.
 *
 * <p>The voice mods ask on their audio threads whether a UUID is a player
 * worth translating. Reading Minecraft's own player map from there would race
 * with the game thread updating it, so they read this snapshot instead. It
 * also tells a player's voice apart from other mods' audio channels, whose
 * UUIDs belong to nobody.</p>
 */
final class OnlinePlayers {
    private volatile Map<UUID, String> names = Map.of();
    private volatile UUID self;
    /** The loaded player entities, for placing spoken translations at the speaker. */
    private volatile Map<UUID, Object> entities = Map.of();

    /** Game thread only. */
    void refresh(Minecraft client) {
        ClientPacketListener connection = client.getConnection();
        this.self = client.player == null ? null : client.player.getUUID();
        if (connection == null) {
            this.names = Map.of();
            return;
        }
        Map<UUID, String> current = new HashMap<>();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            current.put(info.getProfile().id(), info.getProfile().name());
        }
        this.names = Map.copyOf(current);

        Map<UUID, Object> loaded = new HashMap<>();
        if (client.level != null) {
            client.level.players().forEach(player -> loaded.put(player.getUUID(), player));
        }
        this.entities = Map.copyOf(loaded);
    }

    Object entityOf(UUID id) {
        return this.entities.get(id);
    }

    UUID self() {
        return this.self;
    }

    /** Another player who is on the server. */
    boolean isOtherPlayer(UUID id) {
        return !id.equals(this.self) && this.names.containsKey(id);
    }

    boolean isSelf(UUID id) {
        return id.equals(this.self);
    }

    String nameOf(UUID id) {
        return this.names.getOrDefault(id, "");
    }
}
