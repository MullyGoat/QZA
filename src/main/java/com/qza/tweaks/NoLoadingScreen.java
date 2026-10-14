package com.qza.tweaks;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;

public final class NoLoadingScreen {
    private NoLoadingScreen() {
    }

    public static boolean skip(Screen screen) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.noLoadingScreen || !(screen instanceof LevelLoadingScreen)) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.player.isDeadOrDying()) {
            return false;
        }
        if (cfg.noLoadingScreenServersOnly) {
            ServerData server = client.getCurrentServer();
            return server != null && !server.isLan();
        }
        return true;
    }
}
