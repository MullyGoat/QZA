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
    private static int arghCount;

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

        if (matches(message, cfg.necronDeathTrigger)) {
            arghCount++;
            if (cfg.necronTimerDebug && startTicks >= 0L && ServerTickClock.isAvailable()) {
                ChatUtil.send(Component.literal(String.format(Locale.ROOT,
                        "debug: ARGH #%d at %d ticks", arghCount, ServerTickClock.ticks() - startTicks))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            if (running) {
                deathHits++;
                if (deathHits >= Math.max(1, cfg.necronDeathTriggerCount)) {
                    announce();
                }
            }
            return;
        }

        if (awaitingEnd && matches(message, cfg.necronEndTrigger)) {
            measure();
        }
    }

    private static void start() {
        running = true;
        awaitingEnd = false;
        deathHits = 0;
        arghCount = 0;
        deathTicks = -1L;
        startTicks = ServerTickClock.isAvailable() ? ServerTickClock.ticks() : -1L;
    }

    private static void announce() {
        running = false;
        deathHits = 0;

        if (startTicks < 0L || !ServerTickClock.isAvailable()) {
            ChatUtil.error("Server tick time unavailable - no Necron time to report.");
            return;
        }

        deathTicks = ServerTickClock.ticks();
        awaitingEnd = true;
        long totalTicks = (deathTicks - startTicks) + ANIMATION_TICKS;
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

    private static void measure() {
        awaitingEnd = false;
        if (!ConfigManager.get().necronTimerDebug
                || deathTicks < 0L || startTicks < 0L || !ServerTickClock.isAvailable()) {
            deathTicks = -1L;
            return;
        }

        long animation = ServerTickClock.ticks() - deathTicks;
        long total = ServerTickClock.ticks() - startTicks;

        ChatUtil.send(Component.literal(String.format(Locale.ROOT,
                "debug: animation %d ticks (%.2fs) | true total %d ticks (%.2fs) | announced %d | ARGH count %d",
                animation, animation * SECONDS_PER_TICK,
                total, total * SECONDS_PER_TICK,
                (deathTicks - startTicks) + ANIMATION_TICKS, arghCount))
                .withStyle(ChatFormatting.DARK_GRAY));

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
        arghCount = 0;
    }

    public static boolean isRunning() {
        return running;
    }
}
