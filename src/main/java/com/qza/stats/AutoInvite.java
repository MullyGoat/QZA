package com.qza.stats;

import com.qza.chat.ChatHistory;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.party.PartyInvite;
import com.qza.party.PartyState;
import com.qza.util.ChatUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
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

    /**
     * Spelled with or without the space, which is how people actually type it.
     * Anchored to a word boundary so something like "golf inv" does not count.
     */
    private static final Pattern REQUEST = Pattern.compile("\\blf\\s*inv");

    private static final Map<String, Long> lastHandled = new HashMap<>();

    private AutoInvite() {
    }

    public static boolean isRequest(String text) {
        return text != null && REQUEST.matcher(text.toLowerCase(Locale.ROOT)).find();
    }

    /**
     * The class they are offering to play, as in "lf inv healer". Only what
     * follows the request is read, so a floor like "m7" is not mistaken for a
     * class. Null when they did not name one.
     */
    public static String requestedRole(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher matcher = REQUEST.matcher(lower);
        if (!matcher.find()) {
            return null;
        }
        for (String word : lower.substring(matcher.end()).split("[^a-z]+")) {
            String role = DungeonClass.of(word);
            if (role != null) {
                return role;
            }
        }
        return null;
    }

    public static void onWhisper(String ign, String text) {
        if (!ConfigManager.get().autoInviteEnabled || !isRequest(text)) {
            return;
        }
        if (onCooldown(ign)) {
            return;
        }
        run(ign, true, true, requestedRole(text));
    }

    /**
     * The right-click and kebab menu entry: reports, never replies. The result
     * also lands in that player's DM thread, since that is where the check was
     * started from.
     */
    public static void check(String ign) {
        ChatUtil.info("Checking " + ign + "...");
        run(ign, false, true, null);
    }

    /**
     * Looks the player up against their own name, which both proves the proxy
     * works and shows what a report reads like before anything is aimed at
     * someone else.
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
        run(self, false, false, null);
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

    private static void run(String ign, boolean mayReply, boolean intoDm, String requested) {
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

            // What they offered to play is what they will be, so a tank asking
            // "lf inv healer" is reported as the healer.
            String shown = requested != null ? requested : stats.dungeonClass();

            ChatUtil.raw(report(stats, cfg, floor, shown));
            if (failure != null) {
                ChatUtil.raw(failLine(failure));
            }

            if (intoDm) {
                ChatHistory.note(ign, "[QZA] " + plainReport(stats, floor, shown));
                if (failure != null) {
                    ChatHistory.note(ign, "[QZA] Fails: " + failure);
                }
            }

            if (mayReply && cfg.autoInviteRespond) {
                decide(stats, requested, failure);
            }
        });
    }

    /**
     * A full party and a duplicate class both block an invite whatever the
     * stats say. Requirements are judged before the class so someone who is
     * simply not good enough is not told to switch class instead.
     */
    private static void decide(PlayerStats stats, String requested, String failure) {
        PartyState.request().thenAccept(snapshot -> {
            if (snapshot != null && snapshot.full()) {
                decline(stats.name(), "No, party full",
                        "party is " + snapshot.size() + "/" + PartyState.MAX_SIZE);
                return;
            }
            if (failure != null) {
                decline(stats.name(), "No", failure);
                return;
            }

            // What they say they will play wins over what they have selected,
            // so "lf inv healer" from a tank fills the empty healer slot.
            String role = requested != null ? requested : stats.role();
            if (role == null || snapshot == null || !snapshot.inParty()) {
                PartyInvite.send(stats.name());
                ChatUtil.success("Inviting " + stats.name() + ".");
                return;
            }

            takenRoles(snapshot).thenAccept(taken -> {
                if (taken.contains(role)) {
                    decline(stats.name(), "No, dupe class",
                            DungeonClass.label(role) + " is already taken");
                } else {
                    PartyInvite.send(stats.name());
                    ChatUtil.success("Inviting " + stats.name()
                            + " as " + DungeonClass.label(role) + ".");
                }
            });
        });
    }

    /** Which classes the party already covers, this client included. */
    private static CompletableFuture<Set<String>> takenRoles(PartyState.Snapshot snapshot) {
        List<CompletableFuture<StatsApi.Result>> lookups = new ArrayList<>();
        for (UUID member : snapshot.members()) {
            lookups.add(StatsApi.fetchByUuid(member));
        }

        return CompletableFuture
                .allOf(lookups.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    Set<String> taken = new HashSet<>();
                    for (CompletableFuture<StatsApi.Result> lookup : lookups) {
                        StatsApi.Result result = lookup.getNow(null);
                        if (result != null && result.ok()) {
                            String role = result.stats().role();
                            if (role != null) {
                                taken.add(role);
                            }
                        }
                    }
                    return taken;
                });
    }

    private static void decline(String name, String reply, String because) {
        ChatUtil.info("Turning down " + name + " - " + because + ".");
        ChatUtil.sendCommand("w " + name + " " + reply);
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

    /** The same numbers as the chat report, without the colours. */
    static String plainReport(PlayerStats stats, String floor, String shownClass) {
        long pb = stats.pbMillis(floor);
        return "Cata " + stats.cataLevel()
                + " | " + DungeonFloor.label(floor) + " "
                + (pb > 0 ? DungeonFloor.time(pb) : "no S+")
                + " | " + DungeonClass.label(shownClass)
                + " | Secrets " + String.format(Locale.ROOT, "%.2f", stats.secretAverage()) + "/run"
                + " | MP " + stats.magicalPowerLabel();
    }

    /**
     * The unmet requirement, sent under the stats rather than tacked onto the
     * end of them. The stats line is already long enough to wrap, and wrapping
     * is what hid the numbers behind it.
     */
    private static MutableComponent failLine(String failure) {
        return ChatUtil.prefix()
                .append(Component.literal("Fails: ").withStyle(ChatFormatting.RED))
                .append(Component.literal(failure).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent report(PlayerStats stats, QZAConfig cfg,
                                           String floor, String shownClass) {
        int requiredCata = (int) Math.round(cfg.autoInviteCataReq);
        long requiredSeconds = Math.round(cfg.autoInvitePbSeconds);
        long pb = stats.pbMillis(floor);

        boolean cataOk = requiredCata <= 0 || stats.cataLevel() >= requiredCata;
        boolean pbOk = requiredSeconds <= 0
                || (pb > 0 && pb <= requiredSeconds * 1000L);

        return ChatUtil.prefix()
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
                .append(Component.literal(DungeonClass.label(shownClass))
                        .withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Secrets ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format(Locale.ROOT, "%.2f", stats.secretAverage()))
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("/run").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("MP ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(stats.magicalPowerLabel())
                        .withStyle(stats.magicalPower() == null
                                ? ChatFormatting.RED : ChatFormatting.AQUA));
    }
}
