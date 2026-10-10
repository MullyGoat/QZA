package com.qza.timer;

import com.qza.config.ConfigManager;
import com.qza.util.ChatUtil;
import com.qza.util.IgnUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Pattern;

public final class NecronDebugTimer {
    private static final double SECONDS_PER_TICK = 0.05;
    private static final float ARMED_PROGRESS = 0.5f;
    private static final float KILL_PROGRESS = 0.0505f;
    private static final float ZERO_PROGRESS = 0.001f;
    private static final float FULL_PROGRESS = 0.99f;
    private static final int GONE_TICKS = 2;

    private static final Pattern START = Pattern.compile(
            "^\\[BOSS] Necron: You went further than any human before, congratulations\\.$");

    private static boolean running;
    private static boolean armed;
    private static long startTicks = -1L;
    private static long killTicks = -1L;
    private static long zeroTicks = -1L;
    private static long goneSince = -1L;
    private static int goneFor;

    private NecronDebugTimer() {
    }

    public static void onChatMessage(String raw) {
        if (!ConfigManager.get().necronDebugTimer || raw == null) {
            return;
        }
        if (!START.matcher(IgnUtil.stripCodes(raw).trim()).matches()) {
            return;
        }
        reset();
        if (!ServerTickClock.isAvailable()) {
            ChatUtil.error("Necron debug: server tick time unavailable, nothing to time.");
            return;
        }
        running = true;
        startTicks = ServerTickClock.ticks();
        ChatUtil.info("Necron debug: timing Necron in tick time.");
    }

    public static void tick() {
        if (!running) {
            return;
        }
        if (!ConfigManager.get().necronDebugTimer) {
            reset();
            return;
        }
        long now = ServerTickClock.ticks();

        if (zeroTicks < 0L) {
            Float necron = NecronTimer.necronProgress();
            if (necron != null) {
                goneFor = 0;
                if (necron >= ARMED_PROGRESS) {
                    armed = true;
                } else if (armed) {
                    if (killTicks < 0L && necron <= KILL_PROGRESS) {
                        killTicks = now;
                        report("Necron hit 5%", now, " (what Necron Kill Time uses)");
                    }
                    if (necron <= ZERO_PROGRESS) {
                        zeroTicks = now;
                        report("Necron hit 0 HP", now, "");
                    }
                }
            } else if (armed) {
                if (goneFor++ == 0) {
                    goneSince = now;
                }
                if (goneFor >= GONE_TICKS) {
                    zeroTicks = goneSince;
                    report("Necron's bar went away", goneSince, ", counting that as 0 HP");
                }
            }
        }

        if (zeroTicks < 0L) {
            return;
        }
        Float king = NecronTimer.bossProgress("Wither King");
        if (king != null && king >= FULL_PROGRESS) {
            running = false;
            long gap = now - zeroTicks;
            report("Wither King hit 100", now, String.format(Locale.ROOT, ", %.2fs (%d ticks) after Necron hit 0 HP",
                    gap * SECONDS_PER_TICK, gap));
        }
    }

    private static void report(String what, long tick, String extra) {
        long ticks = tick - startTicks;
        ChatUtil.send(Component.literal("Necron debug: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(what).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" at ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format(Locale.ROOT, "%.2fs", ticks * SECONDS_PER_TICK))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" (" + ticks + " ticks)" + extra).withStyle(ChatFormatting.GRAY)));
    }

    public static void reset() {
        running = false;
        armed = false;
        startTicks = -1L;
        killTicks = -1L;
        zeroTicks = -1L;
        goneSince = -1L;
        goneFor = 0;
    }
}
