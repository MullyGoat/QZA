package com.qza.timer;

import com.qza.config.ConfigManager;
import com.qza.mixin.BossEventAccessor;
import com.qza.mixin.BossOverlayAccessor;
import com.qza.util.ChatUtil;
import com.qza.util.IgnUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Pattern;

public final class NecronTimer {
    private static final double SECONDS_PER_TICK = 0.05;
    private static final int GONE_TICKS = 2;
    private static final float ARMED_PROGRESS = 0.5f;
    private static final float KILL_PROGRESS = 0.0505f;

    private static final Pattern START = Pattern.compile(
            "^\\[BOSS] Necron: You went further than any human before, congratulations\\.$");

    private static boolean running;
    private static boolean seenBar;
    private static long startTicks = -1L;
    private static long goneSince = -1L;
    private static int goneFor;

    private NecronTimer() {
    }

    public static void onChatMessage(String raw) {
        if (!ConfigManager.get().necronTimerEnabled || raw == null) {
            return;
        }
        if (START.matcher(IgnUtil.stripCodes(raw).trim()).matches()) {
            running = true;
            seenBar = false;
            goneFor = 0;
            startTicks = ServerTickClock.isAvailable() ? ServerTickClock.ticks() : -1L;
        }
    }

    public static void tick() {
        if (!running) {
            return;
        }
        Float progress = necronProgress();
        if (progress != null) {
            goneFor = 0;
            if (progress >= ARMED_PROGRESS) {
                seenBar = true;
            } else if (seenBar && progress <= KILL_PROGRESS) {
                announce(ServerTickClock.ticks());
            }
            return;
        }
        if (!seenBar) {
            return;
        }
        if (goneFor++ == 0) {
            goneSince = ServerTickClock.ticks();
        }
        if (goneFor >= GONE_TICKS) {
            announce(goneSince);
        }
    }

    private static Float necronProgress() {
        Minecraft client = Minecraft.getInstance();
        if (client.gui == null || !(client.gui.getBossOverlay() instanceof BossOverlayAccessor overlay)) {
            return null;
        }
        for (LerpingBossEvent event : overlay.qzaEvents().values()) {
            if (IgnUtil.stripCodes(event.getName().getString()).contains("Necron")) {
                return event instanceof BossEventAccessor target ? target.qzaTargetPercent() : event.getProgress();
            }
        }
        return null;
    }

    private static void announce(long killTick) {
        running = false;

        if (startTicks < 0L || !ServerTickClock.isAvailable()) {
            ChatUtil.error("Server tick time unavailable - no Necron time to report.");
            return;
        }

        String seconds = String.format(Locale.ROOT, "%.2f", (killTick - startTicks) * SECONDS_PER_TICK);

        if ("client".equals(ConfigManager.get().necronAnnounceMode)) {
            ChatUtil.send(Component.literal("Necron was killed in ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(seconds + " seconds").withStyle(ChatFormatting.GREEN)));
        } else {
            ChatUtil.sendCommand("pc [QZA] Necron was killed in " + seconds + " seconds");
        }
    }

    public static void reset() {
        running = false;
        seenBar = false;
        startTicks = -1L;
        goneFor = 0;
    }

    public static boolean isRunning() {
        return running;
    }
}
