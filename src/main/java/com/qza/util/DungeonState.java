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
    private static final String[] MARKERS = {"The Catacombs", "Kuudra"};
    private static final long CACHE_MS = 500L;

    private static boolean inDungeon;
    private static int catacombsFloor = -1;
    private static boolean catacombsMaster;
    private static long checkedAt;

    private DungeonState() {
    }

    public static boolean inDungeon() {
        long now = System.currentTimeMillis();
        if (checkedAt != 0 && now - checkedAt < CACHE_MS) {
            return inDungeon;
        }
        checkedAt = now;
        catacombsFloor = -1;
        catacombsMaster = false;
        inDungeon = detect();
        return inDungeon;
    }

    public static int catacombsFloor() {
        inDungeon();
        return catacombsFloor;
    }

    public static boolean catacombsMaster() {
        inDungeon();
        return catacombsMaster;
    }

    public static void reset() {
        inDungeon = false;
        catacombsFloor = -1;
        catacombsMaster = false;
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
                readFloor(IgnUtil.stripCodes(team.getPlayerPrefix().getString()
                        + team.getPlayerSuffix().getString()));
                append(sidebar, team.getPlayerPrefix());
                sidebar.append(entry.owner());
                append(sidebar, team.getPlayerSuffix());
            } else {
                sidebar.append(entry.owner());
            }
            append(sidebar, entry.display());
            sidebar.append('\n');
        }

        String text = IgnUtil.stripCodes(sidebar.toString());
        for (String marker : MARKERS) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static void readFloor(String line) {
        if (catacombsFloor >= 0 || !line.contains("The Catacombs (") || line.contains("Queue")) {
            return;
        }
        int open = line.indexOf('(');
        int close = line.indexOf(')', open);
        String floor = close < 0 ? line.substring(open + 1) : line.substring(open + 1, close);
        char last = floor.isEmpty() ? ' ' : floor.charAt(floor.length() - 1);
        catacombsFloor = Character.isDigit(last) ? last - '0' : 0;
        catacombsMaster = floor.startsWith("M");
    }

    private static void append(StringBuilder builder, Component component) {
        if (component != null) {
            builder.append(component.getString());
        }
    }
}
