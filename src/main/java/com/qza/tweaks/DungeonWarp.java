package com.qza.tweaks;

import com.qza.config.ConfigManager;

public final class DungeonWarp {
    public static final String SHORTCUT = "d";
    public static final String COMMAND = "warp dungeons";

    private DungeonWarp() {
    }

    public static String rewrite(String command) {
        if (command != null && ConfigManager.get().dungeonWarpShortcut && SHORTCUT.equals(command.trim())) {
            return COMMAND;
        }
        return command;
    }
}
