package com.qza.stats;

import com.qza.chat.ChatHistory;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.party.PartyInvite;
import com.qza.util.ChatUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Answers "lf inv" whispers by looking the sender up and, if asked to, either
 * inviting them or turning them down.
 *
 * Both the lookup and the reply are off by default, and the reply is gated
 * behind its own toggle, so nothing is sent on anyone's behalf until it has
 * been switched on deliberately.
 */
public final class AutoInvite {
    private static final long COOLDOWN_MILLIS = 30_000L;

    private static final Map<String, Long> lastHandled = new HashMap<>();

    private AutoInvite() {
    }

    /**
     * Spelled with or without the space, which is how people actually type it.
     * Anchored to a word boundary so something like "golf inv" does not count.
     */
    private static final Pattern REQUEST = Pattern.compile("\\blf\\s*inv");

    public static boolean isRequest(String text) {
        if (text == null) {
            return false;
        }
        return REQUEST.matcher(text.toLowerCase(Locale.ROOT)).find();
    }

    public static void onWhisper(String ign, String text) {
        if (!ConfigManager.get().autoInviteEnabled || !isRequest(text)) {
            return;
        }
        if (onCooldown(ign)) {
            return;
        }
        run(ign, true, true);
    }

    /**
     * The right-click and kebab menu entry: reports, never replies. The result
     * also lands in that player's DM thread, since that is where the check was
     * started from.
     */
    public static void check(String ign) {
        ChatUtil.info("Checking " + ign + "...");
        run(ign, false, true);
    }

    /**
     * The Check button in the settings. Looks the player up against their own
     * name, which both proves the proxy works and shows what a report reads
     * like before anything is aimed at someone else.
     */
    public static void reportSource() {
        if (!StatsApi.configured()) {
            ChatUtil.error("No stats proxy set up yet.");
            ChatUtil.info("Deploy worker/ then put its URL in statsProxyUrl "
                    + "in config/qza/config.json - see worker/README.md.");
            return;
        }

        User user = Minecraft.getInstance().getUser();
        String self = user == null ? null : user.getName();
        if (self == null || self.isBlank()) {
            ChatUtil.error("Could not work out your own name to test with.");
            return;
        }

        ChatUtil.info("Testing the stats proxy against your own profile...");
        run(self, false, false);
    }

    private static synchronized boolean onCooldown(String ign) {
        String key = ign.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Long previous = lastHandled.get(key);
        if (previous != null && now - previous < COOLDOWN_MILLIS) {
            return true;
        }
        lastHandled.put(key, now);
        return false;
    }

    private static void run(String ign, boolean mayReply, boolean intoDm) {
        StatsApi.fetch(ign).thenAccept(result -> {
            if (!result.ok()) {
                ChatUtil.error(ign + " - " + result.error());
                if (intoDm) {
                    ChatHistory.note(ign, "[QZA] Stats check failed - " + result.error());
                }
                return;
            }

            PlayerStats stats = result.stats();
            QZAConfig cfg = ConfigManager.get();
            String floor = DungeonFloor.normalise(cfg.autoInviteFloor);
            String failure = firstFailure(stats, cfg, floor);

            ChatUtil.raw(report(stats, cfg, floor, failure));
            if (intoDm) {
                ChatHistory.note(ign, "[QZA] " + plainReport(stats, cfg, floor, failure));
            }

            if (!mayReply || !cfg.autoInviteRespond) {
                return;
            }

            if (failure == null) {
                ChatUtil.success("Inviting " + stats.name() + ".");
                PartyInvite.send(stats.name());
            } else {
                ChatUtil.info("Turning down " + stats.name() + " - " + failure + ".");
                ChatUtil.sendCommand("w " + stats.name() + " No");
            }
        });
    }

    /** The same numbers as the chat report, without the colours. */
    private static String plainReport(PlayerStats stats, QZAConfig cfg,
                                      String floor, String failure) {
        long pb = stats.pbMillis(floor);
        String line = "Cata " + stats.cataLevel()
                + " | " + DungeonFloor.label(floor) + " "
                + (pb > 0 ? DungeonFloor.time(pb) : "no S+")
                + " | Secrets " + String.format(Locale.ROOT, "%.2f", stats.secretAverage()) + "/run";
        return failure == null ? line : line + " | FAILS: " + failure;
    }

    /** The first unmet requirement, or null when they pass everything. */
    public static String firstFailure(PlayerStats stats, QZAConfig cfg, String floor) {
        int requiredCata = (int) Math.round(cfg.autoInviteCataReq);
        if (requiredCata > 0 && stats.cataLevel() < requiredCata) {
            return "Cata " + stats.cataLevel() + " is under " + requiredCata;
        }

        long requiredSeconds = Math.round(cfg.autoInvitePbSeconds);
        if (requiredSeconds > 0) {
            long pb = stats.pbMillis(floor);
            if (pb <= 0) {
                return "no " + DungeonFloor.label(floor) + " S+ time";
            }
            if (pb > requiredSeconds * 1000L) {
                return DungeonFloor.label(floor) + " PB " + DungeonFloor.time(pb)
                        + " is over " + DungeonFloor.timeFromSeconds(requiredSeconds);
            }
        }
        return null;
    }

    private static MutableComponent report(PlayerStats stats, QZAConfig cfg,
                                           String floor, String failure) {
        int requiredCata = (int) Math.round(cfg.autoInviteCataReq);
        long requiredSeconds = Math.round(cfg.autoInvitePbSeconds);
        long pb = stats.pbMillis(floor);

        boolean cataOk = requiredCata <= 0 || stats.cataLevel() >= requiredCata;
        boolean pbOk = requiredSeconds <= 0
                || (pb > 0 && pb <= requiredSeconds * 1000L);

        MutableComponent line = ChatUtil.prefix()
                .append(Component.literal(stats.name())
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" Cata ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(stats.cataLevel()))
                        .withStyle(cataOk ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(DungeonFloor.label(floor) + " ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(pb > 0 ? DungeonFloor.time(pb) : "no S+")
                        .withStyle(pbOk ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Secrets ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format(Locale.ROOT, "%.2f", stats.secretAverage()))
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("/run").withStyle(ChatFormatting.DARK_GRAY));

        if (failure != null) {
            line.append(Component.literal("  FAILS: ").withStyle(ChatFormatting.RED))
                    .append(Component.literal(failure).withStyle(ChatFormatting.GRAY));
        }
        return line;
    }
}
