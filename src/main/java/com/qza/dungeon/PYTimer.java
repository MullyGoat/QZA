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

public final class PYTimer {
    public static final int TEXT_COLOUR = 0xFFFF55FF;

    public static final String LABEL = "PY";

    public static final long STRIKE_TICKS = 520L;

    public static final long STEP_TICKS = 580L;

    private static final double SECONDS_PER_TICK = 0.05;

    private static final double MILLIS_PER_TICK = 50.0;

    private static final Pattern STORM_SPAWN =
            Pattern.compile("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$");

    private static final Pattern STORM_DEATH =
            Pattern.compile("^\\[BOSS] Storm: I should have known that I stood no chance\\.$");

    private static long startTicks = -1L;

    private static long startMillis = -1L;

    private PYTimer() {
    }

    public static void onChatMessage(String raw) {
        if (!ConfigManager.get().pyTimerEnabled || raw == null) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();

        if (STORM_SPAWN.matcher(message).matches()) {
            startTicks = ServerTickClock.isAvailable() ? ServerTickClock.ticks() : -1L;
            startMillis = System.currentTimeMillis();
            return;
        }
        if (STORM_DEATH.matcher(message).matches()) {
            reset();
        }
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

    public static boolean counting() {
        long elapsed = elapsedTicks();
        return elapsed >= STRIKE_TICKS && elapsed <= STEP_TICKS;
    }

    public static double secondsLeft() {
        long elapsed = elapsedTicks();
        if (elapsed < 0L) {
            return 0.0;
        }
        return Math.max(0.0, (STEP_TICKS - elapsed) * SECONDS_PER_TICK);
    }

    public static String text(double seconds) {
        return LABEL + " " + String.format(Locale.ROOT, "%.2f", seconds);
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.pyTimerEnabled || !counting() || !DungeonState.inDungeon()) {
            return;
        }

        String shown = text(secondsLeft());
        float scale = NotificationBox.clampScale(cfg.pyTimerScale);
        int w = Math.round(font.width(shown) * scale);
        int h = Math.round(font.lineHeight * scale);
        int[] pos = NotificationBox.topLeft(cfg.pyTimerX, cfg.pyTimerY,
                graphics.guiWidth(), graphics.guiHeight(), w, h);

        NotificationBox.drawPlain(graphics, font, shown, pos[0], pos[1], scale, TEXT_COLOUR);
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.pyTimerX = defaults.pyTimerX;
        cfg.pyTimerY = defaults.pyTimerY;
        cfg.pyTimerScale = defaults.pyTimerScale;
    }

    public static void reset() {
        startTicks = -1L;
        startMillis = -1L;
    }
}
