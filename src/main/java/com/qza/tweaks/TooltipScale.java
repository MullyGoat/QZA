package com.qza.tweaks;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;

import java.util.List;

public final class TooltipScale {
    public static final String DYNAMIC = "dynamic";
    public static final String CUSTOM = "custom";
    public static final List<String> MODES = List.of(DYNAMIC, CUSTOM);

    public static final double MIN_SCALE = 0.25;
    public static final double MAX_SCALE = 3.0;

    private static final float EDGE = 16f;

    private TooltipScale() {
    }

    public static float scaleFor(int screenW, int screenH, int tooltipW, int tooltipH) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.tooltipScaleEnabled) {
            return 1f;
        }
        if (CUSTOM.equals(cfg.tooltipScaleMode)) {
            return (float) Math.max(MIN_SCALE, Math.min(MAX_SCALE, cfg.tooltipScale));
        }
        float fitW = (screenW - EDGE) / (tooltipW + EDGE);
        float fitH = (screenH - EDGE) / (tooltipH + EDGE);
        return Math.max(0.1f, Math.min(1f, Math.min(fitW, fitH)));
    }

    public static String label(String mode) {
        return CUSTOM.equals(mode) ? "Custom" : "Dynamic";
    }
}
