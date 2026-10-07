package com.qza.timer;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ClockDisplay {
    public static final String TWELVE_HOUR = "12";

    public static final String TWENTY_FOUR_HOUR = "24";

    public static final int TEXT_COLOUR = 0xFFFFFFFF;

    private static final DateTimeFormatter TWELVE =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private static final DateTimeFormatter TWENTY_FOUR =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private ClockDisplay() {
    }

    public static String label(String format) {
        return TWELVE_HOUR.equals(format) ? "12 Hour" : "24 Hour";
    }

    public static String format(LocalTime time, String format) {
        return time.format(TWELVE_HOUR.equals(format) ? TWELVE : TWENTY_FOUR);
    }

    public static String text() {
        return format(LocalTime.now(), ConfigManager.get().clockFormat);
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.clockEnabled) {
            return;
        }

        String shown = text();
        float scale = NotificationBox.clampScale(cfg.clockScale);
        int w = Math.round(font.width(shown) * scale);
        int h = Math.round(font.lineHeight * scale);
        int[] pos = NotificationBox.topLeft(cfg.clockX, cfg.clockY,
                graphics.guiWidth(), graphics.guiHeight(), w, h);

        NotificationBox.drawPlain(graphics, font, shown, pos[0], pos[1], scale, TEXT_COLOUR);
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.clockX = defaults.clockX;
        cfg.clockY = defaults.clockY;
        cfg.clockScale = defaults.clockScale;
    }
}
