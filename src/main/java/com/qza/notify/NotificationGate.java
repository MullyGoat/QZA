package com.qza.notify;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.DungeonState;

public final class NotificationGate {
    public static final String SCOPE_BOTH = "both";
    public static final String SCOPE_MESSAGES = "messages";
    public static final String SCOPE_PARTY = "party";

    private NotificationGate() {
    }

    public static boolean allowsParty() {
        return allows(SCOPE_PARTY);
    }

    public static boolean allowsMessages() {
        return allows(SCOPE_MESSAGES);
    }

    private static boolean allows(String kind) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.dungeonOnlyNotifications) {
            return true;
        }
        if (!DungeonState.inDungeon()) {
            return false;
        }
        return SCOPE_BOTH.equals(cfg.dungeonOnlyScope) || kind.equals(cfg.dungeonOnlyScope);
    }
}
