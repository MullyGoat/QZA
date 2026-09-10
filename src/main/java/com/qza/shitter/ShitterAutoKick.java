package com.qza.shitter;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.ChatUtil;
import com.qza.util.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches chat for someone joining your party / dungeon group and kicks them
 * if they are on the ShitterList. The kick and its reason are always announced
 * in party chat.
 *
 * Nothing happens on the same tick as the join. Each detected shitter is queued
 * one second further out than the last (1s, 2s, 3s ...), and the announcement
 * and the kick itself are also spaced apart, so the traffic never looks
 * machine-timed.
 *
 * If Hypixel replies that you are not the party leader, the kick is retried as
 * the party-command form "!k <ign>" in party chat, which a leader running a
 * party-command mod will action.
 *
 * Handled Hypixel lines (rank prefixes optional, e.g. "[MVP+] "):
 *   "Bob joined the party."
 *   "Party Finder > Bob joined the dungeon group! (2/5)"
 */
public final class ShitterAutoKick {

    private static final String RANK = "(?:\\[[^\\]]{1,20}\\]\\s*)?";
    private static final String IGN = "(\\w{1,16})";

    private static final Pattern PARTY_JOIN =
            Pattern.compile("^" + RANK + IGN + " joined the party\\.$");

    private static final Pattern DUNGEON_JOIN =
            Pattern.compile("^(?:Party Finder > )?" + RANK + IGN
                    + " joined the dungeon group! \\(\\d+/\\d+\\)$");

    /** Hypixel's refusals when you lack party permissions. */
    private static final String[] NOT_LEADER = {
            "you are not the leader",
            "you're not the leader",
            "must be the party leader",
            "you are not the party leader"
    };

    /** Ticks between the party-chat announcement and the kick command. */
    private static final int ANNOUNCE_TO_KICK_TICKS = 6;
    /** How long a kick stays eligible for the !k fallback. */
    private static final long FALLBACK_WINDOW_MS = 5_000L;
    /**
     * Window in which a repeat detection of the same player is ignored.
     * Hypixel can print both "joined the party." and "joined the dungeon
     * group!" for a single join, and this stops that becoming two kicks.
     */
    private static final long DEDUPE_MS = 3_000L;

    /** Guards against double-kicking when Hypixel prints two lines for one join. */
    private static final Map<String, Long> LAST_KICK = new HashMap<>();

    /** Number of kicks currently waiting, used to stagger them 1s apart. */
    private static int queued;

    /** The IGN of the kick we are waiting on a server reply for. */
    private static String pendingTarget;
    private static long pendingSentAt;

    private ShitterAutoKick() {
    }

    public static void onChatMessage(String raw) {
        String message = stripFormatting(raw).trim();

        // Check for "not the leader" first: it is a reply to a kick we sent, and
        // must be handled even if the list was emptied in the meantime.
        if (pendingTarget != null && isNotLeaderReply(message)) {
            String target = pendingTarget;
            pendingTarget = null;
            if (System.currentTimeMillis() - pendingSentAt <= FALLBACK_WINDOW_MS) {
                Scheduler.schedule(Scheduler.TICKS_PER_SECOND / 2, () -> {
                    ChatUtil.send(Component.literal("Not party leader - retrying with ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("!k " + target).withStyle(ChatFormatting.YELLOW)));
                    ChatUtil.sendCommand("pc !k " + target);
                });
            }
            return;
        }

        QZAConfig cfg = ConfigManager.get();
        if (!cfg.shitterListEnabled || ShitterList.size() == 0) {
            return;
        }
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }

        Matcher dungeon = DUNGEON_JOIN.matcher(message);
        if (dungeon.matches()) {
            considerKick(dungeon.group(1));
            return;
        }

        if (!cfg.restrictToDungeonGroups) {
            Matcher party = PARTY_JOIN.matcher(message);
            if (party.matches()) {
                considerKick(party.group(1));
            }
        }
    }

    private static boolean isNotLeaderReply(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        for (String needle : NOT_LEADER) {
            if (lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static void considerKick(String ign) {
        ShitterEntry entry = ShitterList.get(ign);
        if (entry == null) {
            return;
        }

        // Never try to kick yourself out of your own party.
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.getGameProfile().name().equalsIgnoreCase(ign)) {
            return;
        }

        String key = ign.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Long last = LAST_KICK.get(key);
        if (last != null && now - last < DEDUPE_MS) {
            return;
        }
        LAST_KICK.put(key, now);

        // First queued kick fires after 1s, second after 2s, and so on.
        queued++;
        long delayTicks = (long) queued * Scheduler.TICKS_PER_SECOND;
        Scheduler.schedule(delayTicks, () -> {
            if (queued > 0) {
                queued--;
            }
            performKick(entry);
        });
    }

    private static void performKick(ShitterEntry entry) {
        // Bail out if they were taken off the list while the kick was queued.
        if (!ShitterList.contains(entry.name) || !ConfigManager.get().shitterListEnabled) {
            return;
        }

        String reason = entry.reasonOrDefault();

        ChatUtil.raw(ChatUtil.prefix()
                .append(Component.literal("Shitter detected... ").withStyle(ChatFormatting.RED))
                .append(Component.literal("Kicking ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(entry.name).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(reason).withStyle(ChatFormatting.RED))
                .append(Component.literal(")").withStyle(ChatFormatting.GRAY)));

        // Always tell the party who is being kicked and why...
        ChatUtil.sendCommand("pc Shitter detected... Kicking " + entry.name + " (" + reason + ")");

        // ...then kick a few ticks later, so the two never land on one tick.
        Scheduler.schedule(ANNOUNCE_TO_KICK_TICKS, () -> {
            pendingTarget = entry.name;
            pendingSentAt = System.currentTimeMillis();
            ChatUtil.sendCommand("party kick " + entry.name);
        });
    }

    /** Called when the connection drops so queued kicks do not outlive the party. */
    public static void reset() {
        queued = 0;
        pendingTarget = null;
        LAST_KICK.clear();
    }

    /** Removes legacy section-sign colour codes so the regexes see clean text. */
    private static String stripFormatting(String input) {
        if (input.indexOf('§') < 0) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '§' && i + 1 < input.length()) {
                i++; // skip the code character too
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
