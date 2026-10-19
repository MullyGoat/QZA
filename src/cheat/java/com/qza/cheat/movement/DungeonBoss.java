package com.qza.cheat.movement;

import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;

import java.util.Set;
import java.util.regex.Pattern;

public final class DungeonBoss {
    private static final Pattern BOSS_LINE = Pattern.compile("^\\[BOSS] (?!The Watcher:)[^:]{1,40}: .+$");
    private static final Set<String> BLOOD_LINES = Set.of(
            "[BOSS] Scarf: If you win, you live. If you lose, you die. If you don't fight, you can't win.",
            "[BOSS] Scarf: If I had spent more time studying and less time watching anime, maybe mother would be here with me!",
            "[BOSS] Scarf: How can you move forward when you keep regretting the past?");

    private static boolean inBoss;

    private DungeonBoss() {
    }

    public static void onChatMessage(String raw) {
        if (raw == null || inBoss) {
            return;
        }
        String line = IgnUtil.stripCodes(raw).trim();
        if (BOSS_LINE.matcher(line).matches() && !BLOOD_LINES.contains(line) && DungeonState.inDungeon()) {
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
