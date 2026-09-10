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

public final class ShitterAutoKick {
    private static final String RANK = "(?:\\[[^\\]]{1,20}\\]\\s*)?";
    private static final String IGN = "(\\w{1,16})";

    private static final Pattern PARTY_JOIN =
            Pattern.compile("^" + RANK + IGN + " joined the party\\.$");

    private static final Pattern DUNGEON_JOIN =
            Pattern.compile("^(?:Party Finder > )?" + RANK + IGN
                    + " joined the dungeon group! \\(\\d+/\\d+\\)$");

    private static final String[] NOT_LEADER = {
            "not this partys leader",
            "not the partys leader",
            "not the party leader",
            "not the leader",
            "must be the party leader"
    };

    private static final int ANNOUNCE_TO_KICK_TICKS = 6;

    private static final long FALLBACK_WINDOW_MS = 5_000L;

    private static final long DEDUPE_MS = 3_000L;

    private static final Map<String, Long> LAST_KICK = new HashMap<>();

    private static int queued;

    private static String pendingTarget;
    private static long pendingSentAt;

    private ShitterAutoKick() {
    }

    public static void onChatMessage(String raw) {
        String message = stripFormatting(raw).trim();

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
        String lower = message.toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replace("’", "");
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

        ChatUtil.sendCommand("pc Shitter detected... Kicking " + entry.name + " (" + reason + ")");

        Scheduler.schedule(ANNOUNCE_TO_KICK_TICKS, () -> {
            pendingTarget = entry.name;
            pendingSentAt = System.currentTimeMillis();
            ChatUtil.sendCommand("party kick " + entry.name);
        });
    }

    public static void reset() {
        queued = 0;
        pendingTarget = null;
        LAST_KICK.clear();
    }

    private static String stripFormatting(String input) {
        if (input.indexOf('§') < 0) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '§' && i + 1 < input.length()) {
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
