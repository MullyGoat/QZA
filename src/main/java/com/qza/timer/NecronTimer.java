package com.qza.timer;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.ChatUtil;
import com.qza.util.Scheduler;

import java.util.Locale;

public final class NecronTimer {
    private static final int ANNOUNCE_DELAY_TICKS = 10;
    private static final double SECONDS_PER_TICK = 0.05;

    private static boolean running;
    private static long startNanos;
    private static long startTicks;

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
            announce();
        }
    }

    private static void start() {
        running = true;
        startNanos = System.nanoTime();
        startTicks = Scheduler.ticks();
    }

    private static void announce() {
        double offset = ConfigManager.get().necronDeathOffsetSeconds;
        double realSeconds = ((System.nanoTime() - startNanos) / 1_000_000_000.0) + offset;
        double tickSeconds = ((Scheduler.ticks() - startTicks) * SECONDS_PER_TICK) + offset;
        running = false;

        String text = String.format(Locale.ROOT,
                "[QZA] Necron was killed in %.2f seconds (%.2f tick time)",
                realSeconds, tickSeconds);

        Scheduler.schedule(ANNOUNCE_DELAY_TICKS, () -> ChatUtil.sendCommand("pc " + text));
    }

    private static boolean matches(String message, String trigger) {
        return trigger != null && !trigger.isBlank() && message.contains(trigger);
    }

    public static void reset() {
        running = false;
    }

    public static boolean isRunning() {
        return running;
    }
}
