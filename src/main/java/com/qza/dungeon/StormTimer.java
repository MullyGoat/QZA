package com.qza.dungeon;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import com.qza.timer.ServerTickClock;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import com.qza.waypoint.WaypointColour;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;
import java.util.regex.Pattern;

public final class StormTimer {
    public static final String LABEL = "Storm";

    public static final long CRUSH_TICKS = 580L;

    public static final long COUNTDOWN_TICKS = 100L;

    private static final double SECONDS_PER_TICK = 0.05;

    private static final double MILLIS_PER_TICK = 50.0;

    private static final Pattern STORM_START =
            Pattern.compile("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$");

    private static final Pattern STORM_DEATH =
            Pattern.compile("^\\[BOSS] Storm: I should have known that I stood no chance\\.$");

    private static final Pattern GOLDOR_START =
            Pattern.compile("^\\[BOSS] Goldor: Who dares trespass into my domain\\?$");

    private static long startTicks = -1L;

    private static long startMillis = -1L;

    private StormTimer() {
    }

    public static void onChatMessage(String raw) {
        if (!ConfigManager.get().stormTimerEnabled || raw == null) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();

        if (STORM_START.matcher(message).matches()) {
            startTicks = ServerTickClock.isAvailable() ? ServerTickClock.ticks() : -1L;
            startMillis = System.currentTimeMillis();
            return;
        }
        if (STORM_DEATH.matcher(message).matches() || GOLDOR_START.matcher(message).matches()) {
            reset();
        }
    }

    public static void tick() {
        if (startMillis >= 0L && !ConfigManager.get().stormTimerEnabled) {
            reset();
        }
    }

    public static boolean running() {
        return startMillis >= 0L;
    }

    public static long elapsedTicks() {
        if (startMillis < 0L) {
            return -1L;
        }
        if (startTicks >= 0L && ServerTickClock.isAvailable()) {
            return ServerTickClock.ticks() - startTicks;
        }
        return Math.round((System.currentTimeMillis() - startMillis) / MILLIS_PER_TICK);
    }

    public static boolean showing() {
        long elapsed = elapsedTicks();
        if (elapsed < 0L) {
            return false;
        }
        if (!ConfigManager.get().stormTimerCountDown) {
            return true;
        }
        long left = CRUSH_TICKS - elapsed;
        return left >= 0L && left <= COUNTDOWN_TICKS;
    }

    public static double seconds() {
        long elapsed = Math.max(0L, elapsedTicks());
        long shown = ConfigManager.get().stormTimerCountDown ? Math.max(0L, CRUSH_TICKS - elapsed) : elapsed;
        return shown * SECONDS_PER_TICK;
    }

    public static String text(double seconds) {
        return LABEL + " " + String.format(Locale.ROOT, "%.2f", seconds);
    }

    public static int colour() {
        return WaypointColour.argb(ConfigManager.get().stormTimerColour);
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.stormTimerEnabled || !showing() || !DungeonState.inDungeon()) {
            return;
        }

        String shown = text(seconds());
        float scale = NotificationBox.clampScale(cfg.stormTimerScale);
        int w = Math.round(font.width(shown) * scale);
        int h = Math.round(font.lineHeight * scale);
        int[] pos = NotificationBox.topLeft(cfg.stormTimerX, cfg.stormTimerY,
                graphics.guiWidth(), graphics.guiHeight(), w, h);

        NotificationBox.drawPlain(graphics, font, shown, pos[0], pos[1], scale, colour());
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.stormTimerX = defaults.stormTimerX;
        cfg.stormTimerY = defaults.stormTimerY;
        cfg.stormTimerScale = defaults.stormTimerScale;
    }

    public static void reset() {
        startTicks = -1L;
        startMillis = -1L;
    }
}
