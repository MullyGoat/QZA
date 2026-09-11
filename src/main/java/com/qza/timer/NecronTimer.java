package com.qza.timer;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.ChatUtil;
import com.qza.util.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class NecronTimer {
    private static final int ANNOUNCE_DELAY_TICKS = 10;
    private static final double SECONDS_PER_TICK = 0.05;

    private static boolean running;
    private static boolean awaitingEnd;
    private static long startNanos;
    private static long startServerTicks = -1L;
    private static long deathServerTicks = -1L;
    private static double predictedTickSeconds;
    private static int deathHits;

    private NecronTimer() {
    }

    public static void onChatMessage(String raw) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.necronTimerEnabled) {
            return;
        }

        String message = raw.replace("§", "");

        if (matches(message, cfg.necronStartTrigger)) {
            start();
            return;
        }

        if (running && matches(message, cfg.necronDeathTrigger)) {
            deathHits++;
            if (deathHits >= Math.max(1, cfg.necronDeathTriggerCount)) {
                announce();
            }
            return;
        }

        if (awaitingEnd && matches(message, cfg.necronEndTrigger)) {
            verify();
        }
    }

    private static void start() {
        running = true;
        awaitingEnd = false;
        deathHits = 0;
        startNanos = System.nanoTime();
        startServerTicks = ServerTickClock.isAvailable() ? ServerTickClock.ticks() : -1L;
        deathServerTicks = -1L;
    }

    private static void announce() {
        QZAConfig cfg = ConfigManager.get();
        long animationTicks = Math.round(cfg.necronAnimationTicks);
        double animationSeconds = animationTicks * SECONDS_PER_TICK;

        double rawReal = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        double realSeconds = rawReal + animationSeconds;

        long elapsedTicks = -1L;
        double tickSeconds = realSeconds;
        boolean serverTime = false;
        if (startServerTicks >= 0L && ServerTickClock.isAvailable()) {
            deathServerTicks = ServerTickClock.ticks();
            elapsedTicks = deathServerTicks - startServerTicks;
            if (elapsedTicks >= 0L) {
                tickSeconds = (elapsedTicks + animationTicks) * SECONDS_PER_TICK;
                serverTime = true;
            }
        }

        running = false;
        deathHits = 0;
        awaitingEnd = serverTime;
        predictedTickSeconds = tickSeconds;

        if (cfg.necronTimerDebug) {
            ChatUtil.send(Component.literal(String.format(Locale.ROOT,
                    "debug: at kill real %.3fs | ticks %d | +%d anim | server %s",
                    rawReal, elapsedTicks, animationTicks, serverTime))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        if (!serverTime) {
            ChatUtil.error("Server tick time unavailable - reported tick time is real time.");
        }

        String real = String.format(Locale.ROOT, "%.2f", realSeconds);
        String tick = String.format(Locale.ROOT, "%.2f", tickSeconds);

        if ("client".equals(cfg.necronAnnounceMode)) {
            Scheduler.schedule(ANNOUNCE_DELAY_TICKS, () -> ChatUtil.send(
                    Component.literal("Necron was killed in ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(real + " seconds").withStyle(ChatFormatting.GREEN))
                            .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(tick + " tick time").withStyle(ChatFormatting.AQUA))
                            .append(Component.literal(")").withStyle(ChatFormatting.GRAY))));
        } else {
            String text = "pc [QZA] Necron was killed in " + real
                    + " seconds (" + tick + " tick time)";
            Scheduler.schedule(ANNOUNCE_DELAY_TICKS, () -> ChatUtil.sendCommand(text));
        }
    }

    private static void verify() {
        awaitingEnd = false;
        if (!ConfigManager.get().necronTimerDebug || deathServerTicks < 0L || startServerTicks < 0L) {
            deathServerTicks = -1L;
            return;
        }

        long actualAnimation = ServerTickClock.ticks() - deathServerTicks;
        long actualTotal = ServerTickClock.ticks() - startServerTicks;
        long configured = Math.round(ConfigManager.get().necronAnimationTicks);

        ChatUtil.send(Component.literal(String.format(Locale.ROOT,
                "debug: actual anim %d ticks (%.2fs) | configured %d | true total %.2fs | predicted %.2fs | off by %.2fs",
                actualAnimation, actualAnimation * SECONDS_PER_TICK, configured,
                actualTotal * SECONDS_PER_TICK, predictedTickSeconds,
                predictedTickSeconds - (actualTotal * SECONDS_PER_TICK)))
                .withStyle(ChatFormatting.DARK_GRAY));

        if (actualAnimation != configured) {
            ChatUtil.send(Component.literal("Set Animation Ticks to ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(actualAnimation)).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" to match Odin exactly.").withStyle(ChatFormatting.GRAY)));
        }

        deathServerTicks = -1L;
    }

    private static boolean matches(String message, String trigger) {
        return trigger != null && !trigger.isBlank() && message.contains(trigger);
    }

    public static void reset() {
        running = false;
        awaitingEnd = false;
        startServerTicks = -1L;
        deathServerTicks = -1L;
        deathHits = 0;
    }

    public static boolean isRunning() {
        return running;
    }
}
