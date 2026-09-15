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
    private static long startTicks = -1L;
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
        }
    }

    private static void start() {
        running = true;
        deathHits = 0;
        startTicks = ServerTickClock.isAvailable() ? ServerTickClock.ticks() : -1L;
    }

    private static void announce() {
        running = false;
        deathHits = 0;

        if (startTicks < 0L || !ServerTickClock.isAvailable()) {
            ChatUtil.error("Server tick time unavailable - no Necron time to report.");
            return;
        }

        long totalTicks = (ServerTickClock.ticks() - startTicks) + ANIMATION_TICKS;
        String seconds = String.format(Locale.ROOT, "%.2f", totalTicks * SECONDS_PER_TICK);

        if ("client".equals(ConfigManager.get().necronAnnounceMode)) {
            Scheduler.schedule(ANNOUNCE_DELAY_TICKS, () -> ChatUtil.send(
                    Component.literal("Necron was killed in ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(seconds + " seconds").withStyle(ChatFormatting.GREEN))));
        } else {
            String text = "pc [QZA] Necron was killed in " + seconds + " seconds";
            Scheduler.schedule(ANNOUNCE_DELAY_TICKS, () -> ChatUtil.sendCommand(text));
        }
    }

    private static boolean matches(String message, String trigger) {
        return trigger != null && !trigger.isBlank() && message.contains(trigger);
    }

    public static void reset() {
        running = false;
        startTicks = -1L;
        deathHits = 0;
    }

    public static boolean isRunning() {
        return running;
    }
}
