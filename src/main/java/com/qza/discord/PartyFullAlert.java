package com.qza.discord;

import com.qza.config.QZAConfig;
import com.qza.config.ConfigManager;
import com.qza.party.PartyState;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import com.qza.util.Scheduler;
import net.minecraft.client.Minecraft;

import java.util.UUID;
import java.util.regex.Pattern;

public final class PartyFullAlert {

    private static final long SETTLE_TICKS = 20;

    private static final long COOLDOWN_MILLIS = 60_000L;

    private static final String RANK = "(?:\\[[^\\]]{1,20}\\]\\s*)?";

    private static final Pattern[] JOINED = {
            Pattern.compile("^" + RANK + "\\w{1,16} joined the party\\.$"),
            Pattern.compile("^Party Finder > " + RANK + "\\w{1,16} joined the dungeon group!(?: \\(.+\\))?$"),
    };

    private static boolean checkPending;
    private static long joinedAt;
    private static long lastSentAt;

    private PartyFullAlert() {
    }

    public static void reset() {
        checkPending = false;
        joinedAt = 0L;
        lastSentAt = 0L;
    }

    public static void onChatMessage(String raw) {
        if (raw == null || raw.isEmpty() || !DiscordAlert.active() || !isJoin(raw)) {
            return;
        }

        joinedAt = System.currentTimeMillis();
        if (checkPending) {
            return;
        }

        checkPending = true;
        Scheduler.schedule(SETTLE_TICKS, PartyFullAlert::check);
    }

    private static boolean isJoin(String raw) {
        for (String line : IgnUtil.stripCodes(raw).split("\n")) {
            String message = line.trim();
            for (Pattern joined : JOINED) {
                if (joined.matcher(message).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void check() {
        checkPending = false;
        if (!DiscordAlert.active()) {
            return;
        }

        long since = joinedAt;
        PartyState.refresh().thenAccept(snapshot -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> evaluate(client, snapshot, since));
        });
    }

    private static void evaluate(Minecraft client, PartyState.Snapshot snapshot, long since) {

        if (snapshot == null || snapshot.at() < since || !snapshot.full() || client.player == null) {
            return;
        }
        if (DungeonState.inDungeon()) {
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
