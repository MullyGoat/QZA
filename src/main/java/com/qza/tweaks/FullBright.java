package com.qza.tweaks;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class FullBright {
    public static final String AMBIENT = "ambient";
    public static final String BRIGHTNESS = "brightness";
    public static final String NIGHT_VISION = "night_vision";
    public static final List<String> MODES = List.of(AMBIENT, BRIGHTNESS, NIGHT_VISION);

    private static final float BOOSTED_BRIGHTNESS = 15.0f;

    private FullBright() {
    }

    public static void apply(LightmapRenderState state) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.fullBright || !state.needsUpdate) {
            return;
        }
        switch (mode(cfg)) {
            case BRIGHTNESS -> state.brightness = Math.max(state.brightness, BOOSTED_BRIGHTNESS);
            case NIGHT_VISION -> state.nightVisionEffectIntensity = 1.0f;
            default -> state.ambientColor = LightmapRenderStateExtractor.WHITE;
        }
    }

    public static boolean hidesNightVision(LivingEntity entity, Holder<MobEffect> effect) {
        if (effect != MobEffects.NIGHT_VISION) {
            return false;
        }
        QZAConfig cfg = ConfigManager.get();
        return cfg.fullBright && cfg.fullBrightHideNightVision && entity == Minecraft.getInstance().player;
    }

    public static String modeLabel(String mode) {
        return switch (mode == null ? AMBIENT : mode) {
            case BRIGHTNESS -> "Brightness";
            case NIGHT_VISION -> "Night Vision";
            default -> "Ambient Light";
        };
    }

    private static String mode(QZAConfig cfg) {
        return MODES.contains(cfg.fullBrightMode) ? cfg.fullBrightMode : AMBIENT;
    }
}
