package com.qza.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkyBlockArea {
    private static final Pattern AREA = Pattern.compile("^(?:Area|Dungeon): (.+)$");
    private static final long CACHE_MS = 1000L;

    private static String area = "";
    private static long checkedAt;
    private static boolean onSkyBlock;
    private static long skyBlockCheckedAt;

    private SkyBlockArea() {
    }

    public static boolean onSkyBlock() {
        long now = System.currentTimeMillis();
        if (skyBlockCheckedAt != 0 && now - skyBlockCheckedAt < CACHE_MS) {
            return onSkyBlock;
        }
        skyBlockCheckedAt = now;
        onSkyBlock = readSkyBlock();
        return onSkyBlock;
    }

    private static boolean readSkyBlock() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return false;
        }
        Objective objective = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return false;
        }
        return IgnUtil.stripCodes(objective.getDisplayName().getString())
                .toUpperCase(Locale.ROOT).contains("SKYBLOCK");
    }

    public static String current() {
        long now = System.currentTimeMillis();
        if (checkedAt != 0 && now - checkedAt < CACHE_MS) {
            return area;
        }
        checkedAt = now;
        area = read();
        return area;
    }

    private static String read() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return "";
        }
        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            Component display = info.getTabListDisplayName();
            if (display == null) {
                continue;
            }
            Matcher matcher = AREA.matcher(IgnUtil.stripCodes(display.getString()).trim());
            if (matcher.matches()) {
                return matcher.group(1).trim();
            }
        }
        return "";
    }
}
