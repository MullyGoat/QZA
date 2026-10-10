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
    public static final String SPAWNED_TEXT = "Crystals Spawned";
    public static final int SPAWNED_COLOUR = 0xFFFF55FF;
    public static final int SPAWNING_COLOUR = 0xFFFF5555;

    public static final long SPAWN_TICKS = 160L;

    private static final double SECONDS_PER_TICK = 0.05;
    private static final double MILLIS_PER_TICK = 50.0;
    private static final int PAIR = 2;

    private static final Pattern MAXOR_START =
            Pattern.compile("^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$");
    private static final Pattern STORM_START =
            Pattern.compile("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$");
    private static final Pattern CRYSTAL_ACTIVE = Pattern.compile("^\\d+/2 Energy Crystals are now active!$");
    private static final Pattern LASER_CHARGING = Pattern.compile("^The Energy Laser is charging up!$");

    private static boolean active;
    private static long startTicks = -1L;
    private static long startMillis = -1L;
    private static int pairs;
    private static int placedInPair;
    private static boolean pairCounted;

    private CrystalTimer() {
    }

    public static void onChatMessage(String raw) {
        if (!ConfigManager.get().crystalTimerEnabled || raw == null) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();

        if (MAXOR_START.matcher(message).matches()) {
            reset();
            active = true;
            startTicks = ServerTickClock.isAvailable() ? ServerTickClock.ticks() : -1L;
            startMillis = System.currentTimeMillis();
            return;
        }
        if (!active) {
            return;
        }
        if (STORM_START.matcher(message).matches()) {
            reset();
            return;
        }
        if (CRYSTAL_ACTIVE.matcher(message).matches()) {
            placedInPair++;
            if (placedInPair >= PAIR && !pairCounted) {
                pairCounted = true;
                pairPlaced();
            }
            return;
        }
        if (LASER_CHARGING.matcher(message).matches()) {
            if (!pairCounted) {
                pairPlaced();
            }
            placedInPair = 0;
            pairCounted = false;
        }
    }

    private static void pairPlaced() {
        pairs++;
        if (pairs >= 2) {
            reset();
        }
    }

    public static void tick() {
        if (active && !ConfigManager.get().crystalTimerEnabled) {
            reset();
        }
    }

    private static long elapsedTicks() {
        if (startMillis < 0L) {
            return 0L;
        }
        if (startTicks >= 0L && ServerTickClock.isAvailable()) {
            return ServerTickClock.ticks() - startTicks;
        }
        return Math.round((System.currentTimeMillis() - startMillis) / MILLIS_PER_TICK);
    }

    public static String spawningText(double seconds) {
        return "Crystals Spawning in: " + String.format(Locale.ROOT, "%.2f", seconds) + "s";
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.crystalTimerEnabled || !active || !DungeonState.inDungeon()) {
            return;
        }
        long left = SPAWN_TICKS - elapsedTicks();
        boolean counting = pairs >= 1 && left > 0L;
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
        active = false;
        startTicks = -1L;
        startMillis = -1L;
        pairs = 0;
        placedInPair = 0;
        pairCounted = false;
    }
}
