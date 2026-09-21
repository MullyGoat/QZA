package com.qza.discord;

import com.qza.config.QZAConfig;
import com.qza.config.ConfigManager;
import com.qza.party.PartyState;
import com.qza.util.IgnUtil;
import com.qza.util.Scheduler;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.UUID;

public final class PartyFullAlert {

    private static final long SETTLE_TICKS = 20;

    private static final long COOLDOWN_MILLIS = 60_000L;

    private static final String[] MEMBERSHIP_CHANGED = {
            "joined the dungeon group",
            "joined the party",
            "left the dungeon group",
            "left the party",
            "removed from the party",
            "kicked from the party",
    };

    private static boolean wasFull;
    private static boolean checkPending;
    private static long lastSentAt;

    private PartyFullAlert() {
    }

    public static void reset() {
        wasFull = false;
        checkPending = false;
        lastSentAt = 0L;
    }

    public static void onChatMessage(String raw) {
        if (raw == null || raw.isEmpty() || !DiscordAlert.active()) {
            return;
        }

        String message = IgnUtil.stripCodes(raw).toLowerCase(Locale.ROOT);
        boolean relevant = false;
        for (String marker : MEMBERSHIP_CHANGED) {
            if (message.contains(marker)) {
                relevant = true;
                break;
            }
        }
        if (!relevant || checkPending) {
            return;
        }

        checkPending = true;
        Scheduler.schedule(SETTLE_TICKS, PartyFullAlert::check);
    }

    private static void check() {
        checkPending = false;
        if (!DiscordAlert.active()) {
            return;
        }

        PartyState.request().thenAccept(snapshot -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> evaluate(client, snapshot));
        });
    }

    private static void evaluate(Minecraft client, PartyState.Snapshot snapshot) {

        if (snapshot == null || !snapshot.fresh()) {
            return;
        }

        boolean full = snapshot.full();
        boolean filledJustNow = full && !wasFull;
        wasFull = full;

        if (!filledJustNow || client.player == null) {
            return;
        }

        if (!ConfigManager.get().discordAlertAlways) {
            UUID self = client.player.getUUID();
            boolean leads = snapshot.ledBy(self);
            boolean away = !client.isWindowActive();
            if (!leads && !away) {
                return;
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastSentAt < COOLDOWN_MILLIS) {
            return;
        }
        lastSentAt = now;

        DiscordAlert.partyFull(snapshot.size());
    }

    public static String blockedReason() {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.discordAlertEnabled) {
            return "Discord alerts are switched off in /qza.";
        }
        if (!DiscordAlert.linked()) {
            return "Not linked to Discord yet - run /qza discord link.";
        }
        if (!cfg.discordAlertDm && !cfg.discordAlertChannel) {
            return "Both the DM and the channel ping are switched off.";
        }
        return null;
    }
}
