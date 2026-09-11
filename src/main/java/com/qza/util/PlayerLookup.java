package com.qza.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.UUID;

public final class PlayerLookup {

    private PlayerLookup() {
    }

    public static String uuidFor(String name) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || name == null || name.isBlank()) {
            return null;
        }
        PlayerInfo info = connection.getPlayerInfoIgnoreCase(name);
        if (info == null) {
            return null;
        }
        UUID id = info.getProfile().id();
        return id == null ? null : id.toString();
    }

    public static String nameFor(String uuid) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || uuid == null || uuid.isBlank()) {
            return null;
        }
        try {
            PlayerInfo info = connection.getPlayerInfo(UUID.fromString(uuid));
            return info == null ? null : info.getProfile().name();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
