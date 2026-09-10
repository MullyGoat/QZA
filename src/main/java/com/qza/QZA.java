package com.qza;

import com.qza.command.QZACommand;
import com.qza.command.ShitterCommand;
import com.qza.config.ConfigManager;
import com.qza.music.MusicLibrary;
import com.qza.music.MusicManager;
import com.qza.shitter.ShitterAutoKick;
import com.qza.shitter.ShitterList;
import com.qza.timer.NecronTimer;
import com.qza.util.Scheduler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QZA implements ClientModInitializer {
    public static final String MOD_ID = "qza";
    public static final String MOD_NAME = "QZA";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static Object lastLevel;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        ShitterList.load();
        MusicLibrary.ensureDir();
        MusicManager.get().applySettings();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            QZACommand.register(dispatcher);
            ShitterCommand.register(dispatcher);
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            String plain = message.getString();
            if (plain == null || plain.isEmpty()) return;

            ShitterAutoKick.onChatMessage(plain);
            MusicManager.get().onChatMessage(plain);
            NecronTimer.onChatMessage(plain);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Scheduler.tick();

            Object level = client.level;
            if (level != lastLevel) {
                lastLevel = level;
                MusicManager.get().stopNow();
                NecronTimer.reset();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MusicManager.get().stopNow();
            Scheduler.clear();
            ShitterAutoKick.reset();
            NecronTimer.reset();
        });

        LOGGER.info("QZA initialised - {} shitter(s) loaded", ShitterList.size());
    }
}
