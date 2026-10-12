package com.qza.cheat.movement;

import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;

import java.util.regex.Pattern;

public final class DungeonBoss {
    private static final Pattern BOSS_LINE = Pattern.compile("^\\[BOSS] (?!The Watcher:)[^:]{1,40}: .+$");

    private static boolean inBoss;

    private DungeonBoss() {
    }

    public static void onChatMessage(String raw) {
        if (raw == null || inBoss) {
            return;
        }
        if (BOSS_LINE.matcher(IgnUtil.stripCodes(raw).trim()).matches() && DungeonState.inDungeon()) {
            inBoss = true;
        }
    }

    public static boolean inBoss() {
        return inBoss && DungeonState.inDungeon();
    }

    public static void reset() {
        inBoss = false;
    }
}
