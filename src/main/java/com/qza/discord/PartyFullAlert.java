package com.qza.discord;

import com.qza.config.QZAConfig;
import com.qza.config.ConfigManager;
import com.qza.party.PartyState;
import com.qza.util.IgnUtil;
import com.qza.util.Scheduler;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.UUID;

/**
 * Pings Discord the moment a party the player is running fills up behind their
 * back.
 *
 * Hypixel's mod API answers questions about the party but never volunteers that
 * it changed, so something has to prompt the asking. Chat is that prompt: every
 * join and leave is announced, and a line saying so is the cue to go and get a
 * fresh count. Polling on a timer would ask constantly and still be late.
 *
 * The party reaching five is the event. It is edge triggered, so a party sitting
 * at full does not re-announce itself on every join and leave around it.
 *
 * Two further things decide whether that event is worth interrupting for, and
 * either one on its own is enough:
 *
 * <ul>
 *   <li>the player leads the party, so a group they are recruiting for is worth
 *       telling them about whether or not they are watching it;</li>
 *   <li>the game window is not focused, so a party filling while they are
 *       looking at something else reaches them whoever leads it.</li>
 * </ul>
 *
 * Which leaves exactly one case quiet: somebody else's party filling up while
 * the player is sat watching it happen, where the alert would tell them nothing
 * the screen has not already.
 */
public final class PartyFullAlert {
    /**
     * Long enough for a burst of joins to settle into one count, short enough
     * that the ping still beats the player back to the window.
     */
    private static final long SETTLE_TICKS = 20;

    /** A floor on how often this can fire, whatever the party does. */
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

    /** Nothing carries across a reconnect, least of all who was in the party. */
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

        // One count per burst. Five people joining at once is still one party.
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

    /**
     * Runs on the client thread, so the window state it reads is the one the
     * player is actually looking at.
     */
    private static void evaluate(Minecraft client, PartyState.Snapshot snapshot) {
        // A stale snapshot means Hypixel did not answer in time. Counting off an
        // old one could announce a party that emptied a minute ago, so this
        // waits for the next join instead.
        if (snapshot == null || !snapshot.fresh()) {
            return;
        }

        boolean full = snapshot.full();
        boolean filledJustNow = full && !wasFull;
        wasFull = full;

        if (!filledJustNow || client.player == null) {
            return;
        }

        // Either reason is reason enough. Requiring both meant a leader sitting
        // on the party finder screen, who is the most likely person to want
        // this, was the one person who never got it.
        UUID self = client.player.getUUID();
        boolean leads = snapshot.ledBy(self);
        boolean away = !client.isWindowActive();
        if (!leads && !away) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastSentAt < COOLDOWN_MILLIS) {
            return;
        }
        lastSentAt = now;

        DiscordAlert.partyFull(snapshot.size());
    }

    /**
     * Why the last party to fill up went unannounced, for {@code /qza discord}.
     * Null when nothing is standing in the way.
     */
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
