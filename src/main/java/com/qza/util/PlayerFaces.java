package com.qza.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PlayerFaces {
    private static final long REFRESH_MS = 5000L;

    private static final Map<String, PlayerSkin> cache = new HashMap<>();
    private static long cachedAt;

    private PlayerFaces() {
    }

    public static PlayerSkin skinFor(String name) {
        if (name == null || name.isBlank()) {
            return DefaultPlayerSkin.getDefaultSkin();
        }

        long now = System.currentTimeMillis();
        if (now - cachedAt > REFRESH_MS) {
            cache.clear();
            cachedAt = now;
        }

        return cache.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> resolve(name));
    }

    public static void clearCache() {
        cache.clear();
    }

    private static PlayerSkin resolve(String name) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            PlayerInfo info = connection.getPlayerInfoIgnoreCase(name);
            if (info != null) {
                return info.getSkin();
            }
        }
        return DefaultPlayerSkin.get(offlineId(name));
    }

    private static UUID offlineId(String name) {
        String seed = "OfflinePlayer:" + name;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
