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
    private static final long ANIMATION_TICKS = 62L;

    private static boolean running;
    private static boolean awaitingEnd;
    private static long startTicks = -1L;
    private static long deathTicks = -1L;

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
            measureAnimation();
        }
    }

    private static void start() {
        running = true;
        awaitingEnd = false;
        deathHits = 0;
        deathTicks = -1L;
        startTicks = ServerTickClock.isAvailable() ? ServerTickClock.ticks() : -1L;
    }

    private static void announce() {
        QZAConfig cfg = ConfigManager.get();
        running = false;
        deathHits = 0;

        if (startTicks < 0L || !ServerTickClock.isAvailable()) {
            ChatUtil.error("Server tick time unavailable - no Necron time to report.");
            awaitingEnd = false;
            return;
        }

        deathTicks = ServerTickClock.ticks();
        long totalTicks = (deathTicks - startTicks) + ANIMATION_TICKS;
        awaitingEnd = true;

        if (cfg.necronTimerDebug) {
            ChatUtil.send(Component.literal(String.format(Locale.ROOT,
                    "debug: at kill %d ticks | +%d animation | total %d ticks",
                    deathTicks - startTicks, ANIMATION_TICKS, totalTicks))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        String seconds = String.format(Locale.ROOT, "%.2f", totalTicks * SECONDS_PER_TICK);

        if ("client".equals(cfg.necronAnnounceMode)) {
            Scheduler.schedule(ANNOUNCE_DELAY_TICKS, () -> ChatUtil.send(
                    Component.literal("Necron was killed in ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(seconds + " seconds").withStyle(ChatFormatting.GREEN))));
        } else {
            String text = "pc [QZA] Necron was killed in " + seconds + " seconds";
            Scheduler.schedule(ANNOUNCE_DELAY_TICKS, () -> ChatUtil.sendCommand(text));
        }
    }

    private static void measureAnimation() {
        awaitingEnd = false;
        if (deathTicks < 0L || startTicks < 0L || !ServerTickClock.isAvailable()) {
            deathTicks = -1L;
            return;
        }

        long actualAnimation = ServerTickClock.ticks() - deathTicks;
        long actualTotal = ServerTickClock.ticks() - startTicks;

        if (ConfigManager.get().necronTimerDebug) {
            ChatUtil.send(Component.literal(String.format(Locale.ROOT,
                    "debug: animation %d ticks (%.2fs) | true total %d ticks (%.2fs) | error %d ticks",
                    actualAnimation, actualAnimation * SECONDS_PER_TICK,
                    actualTotal, actualTotal * SECONDS_PER_TICK,
                    ANIMATION_TICKS - actualAnimation))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        deathTicks = -1L;
    }

    private static boolean matches(String message, String trigger) {
        return trigger != null && !trigger.isBlank() && message.contains(trigger);
    }

    public static void reset() {
        running = false;
        awaitingEnd = false;
        startTicks = -1L;
        deathTicks = -1L;
        deathHits = 0;
    }

    public static boolean isRunning() {
        return running;
    }
}
