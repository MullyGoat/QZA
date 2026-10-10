package com.qza.tweaks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public final class PlayerSize {
    public static final double MIN = -1.0;
    public static final double MAX = 3.0;

    private PlayerSize() {
    }

    public static void apply(AvatarRenderState state, PoseStack pose) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.playerSizeEnabled) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || state.id != player.getId()) {
            return;
        }
        float x = clamp(cfg.playerSizeX);
        float y = clamp(cfg.playerSizeY);
        float z = clamp(cfg.playerSizeZ);
        if (y < 0) {
            pose.translate(0f, y * 2f, 0f);
        }
        pose.scale(x, y, z);
    }

    private static float clamp(double value) {
        return (float) Math.max(MIN, Math.min(MAX, value));
    }
}
