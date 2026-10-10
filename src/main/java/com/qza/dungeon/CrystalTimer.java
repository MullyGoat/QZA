package com.qza.dungeon;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import com.qza.timer.ServerTickClock;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CrystalTimer {
    public static final String SPAWNED_TEXT = "Crystal Spawned";
    public static final int SPAWNED_COLOUR = 0xFFFF55FF;
    public static final int SPAWNING_COLOUR = 0xFFFF5555;

    public static final long SPAWN_TICKS = 34L;

    private static final double SECONDS_PER_TICK = 0.05;
    private static final double MILLIS_PER_TICK = 50.0;

    private static final Pattern MAXOR_START =
            Pattern.compile("^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$");
    private static final Pattern STORM_START =
            Pattern.compile("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$");
    private static final Pattern LASER_CHARGING = Pattern.compile("^The Energy Laser is charging up!$");
    private static final Pattern MAXOR_HIT =
            Pattern.compile("^\\[BOSS] Maxor: (?:THAT BEAM! IT HURTS! IT HURTS!!|YOU TRICKED ME!)$");

    private static final int HIDDEN = 0;
    private static final int SPAWNED = 1;
    private static final int CHARGING = 2;
    private static final int SPAWNING = 3;

    private static int state = HIDDEN;
    private static boolean charged;
    private static long hitTicks = -1L;
    private static long hitMillis = -1L;

    private CrystalTimer() {
    }

    public static void onChatMessage(String raw) {
        if (!ConfigManager.get().crystalTimerEnabled || raw == null) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();

        if (MAXOR_START.matcher(message).matches()) {
            reset();
            state = SPAWNED;
            return;
        }
        if (state == HIDDEN) {
            return;
        }
        if (STORM_START.matcher(message).matches()) {
            reset();
            return;
        }
        if (LASER_CHARGING.matcher(message).matches()) {
            if (charged) {
                state = HIDDEN;
                return;
            }
            charged = true;
            if (state == SPAWNED && hitMillis < 0L) {
                state = CHARGING;
            }
            return;
        }
        if (MAXOR_HIT.matcher(message).matches() && hitMillis < 0L) {
            state = SPAWNING;
            hitTicks = ServerTickClock.isAvailable() ? ServerTickClock.ticks() : -1L;
            hitMillis = System.currentTimeMillis();
        }
    }

    public static void tick() {
        if (state == HIDDEN) {
            return;
        }
        if (!ConfigManager.get().crystalTimerEnabled) {
            reset();
            return;
        }
        if (state == SPAWNING && sinceHit() >= SPAWN_TICKS) {
            state = SPAWNED;
        }
    }

    private static long sinceHit() {
        if (hitMillis < 0L) {
            return 0L;
        }
        if (hitTicks >= 0L && ServerTickClock.isAvailable()) {
            return ServerTickClock.ticks() - hitTicks;
        }
        return Math.round((System.currentTimeMillis() - hitMillis) / MILLIS_PER_TICK);
    }

    public static String spawningText(double seconds) {
        return "Crystal Spawning in: " + String.format(Locale.ROOT, "%.2f", seconds) + "s";
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.crystalTimerEnabled || state == HIDDEN || !DungeonState.inDungeon()) {
            return;
        }
        boolean counting = state == CHARGING || state == SPAWNING;
        long left = state == SPAWNING ? Math.max(0L, SPAWN_TICKS - sinceHit()) : SPAWN_TICKS;
        String text = counting ? spawningText(left * SECONDS_PER_TICK) : SPAWNED_TEXT;

        float scale = NotificationBox.clampScale(cfg.crystalTimerScale);
        int w = Math.round(font.width(text) * scale);
        int h = Math.round(font.lineHeight * scale);
        int[] pos = NotificationBox.topLeft(cfg.crystalTimerX, cfg.crystalTimerY,
                graphics.guiWidth(), graphics.guiHeight(), w, h);
        NotificationBox.drawPlain(graphics, font, text, pos[0], pos[1], scale,
                counting ? SPAWNING_COLOUR : SPAWNED_COLOUR);
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.crystalTimerX = defaults.crystalTimerX;
        cfg.crystalTimerY = defaults.crystalTimerY;
        cfg.crystalTimerScale = defaults.crystalTimerScale;
    }

    public static void reset() {
        state = HIDDEN;
        charged = false;
        hitTicks = -1L;
        hitMillis = -1L;
    }
}
