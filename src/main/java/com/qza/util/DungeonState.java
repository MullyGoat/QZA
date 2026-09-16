package com.qza.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public final class DungeonState {
    private static final String MARKER = "The Catacombs";
    private static final long CACHE_MS = 500L;

    private static boolean inDungeon;
    private static long checkedAt;

    private DungeonState() {
    }

    public static boolean inDungeon() {
        long now = System.currentTimeMillis();
        if (checkedAt != 0 && now - checkedAt < CACHE_MS) {
            return inDungeon;
        }
        checkedAt = now;
        inDungeon = detect();
        return inDungeon;
    }

    public static void reset() {
        inDungeon = false;
        checkedAt = 0;
    }

    private static boolean detect() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return false;
        }

        Scoreboard scoreboard = level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return false;
        }

        StringBuilder sidebar = new StringBuilder();
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            if (team != null) {
                append(sidebar, team.getPlayerPrefix());
                sidebar.append(entry.owner());
                append(sidebar, team.getPlayerSuffix());
            } else {
                sidebar.append(entry.owner());
            }
            append(sidebar, entry.display());
            sidebar.append('\n');
        }

        return IgnUtil.stripCodes(sidebar.toString()).contains(MARKER);
    }

    private static void append(StringBuilder builder, Component component) {
        if (component != null) {
            builder.append(component.getString());
        }
    }
}
