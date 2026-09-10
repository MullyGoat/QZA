package com.qza;

import com.qza.command.QZACommand;
import com.qza.command.ShitterCommand;
import com.qza.config.ConfigManager;
import com.qza.music.MusicLibrary;
import com.qza.music.MusicManager;
import com.qza.shitter.ShitterAutoKick;
import com.qza.shitter.ShitterList;
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

    /**
     * Last seen ClientLevel, by identity. Held as Object so we do not depend on
     * the level class name. Hypixel swaps the level instance when you leave a
     * dungeon for the hub, which is how we notice you left the run.
     */
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

        // Single chat hook feeding both features. Action bar messages (overlay) are
        // ignored -- Hypixel boss lines and party messages are always real chat.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            String plain = message.getString();
            if (plain == null || plain.isEmpty()) return;

            ShitterAutoKick.onChatMessage(plain);
            MusicManager.get().onChatMessage(plain);
        });

        // Leaving the run mid-terminals (or dying out to the hub) changes the
        // client level instance -- cut the music when that happens.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Scheduler.tick();

            Object level = client.level;
            if (level != lastLevel) {
                lastLevel = level;
                MusicManager.get().stopNow();
            }
        });

        // Never leave music running -- or fire a queued kick into a party that
        // no longer exists -- after a disconnect.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MusicManager.get().stopNow();
            Scheduler.clear();
            ShitterAutoKick.reset();
        });

        LOGGER.info("QZA initialised - {} shitter(s) loaded", ShitterList.size());
    }
}
