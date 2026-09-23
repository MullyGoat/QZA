package com.qza;

import com.qza.chat.ChannelHistory;
import com.qza.chat.ChatHistory;
import com.qza.chat.ChatNotification;
import com.qza.command.QZACommand;
import com.qza.command.ShitterCommand;
import com.qza.config.ConfigManager;
import com.qza.discord.PartyFullAlert;
import com.qza.dungeon.WitherKey;
import com.qza.music.MusicLibrary;
import com.qza.music.MusicManager;
import com.qza.notify.NotificationHud;
import com.qza.party.PartyNotification;
import com.qza.party.PartyState;
import com.qza.shitter.ShitterAutoKick;
import com.qza.shitter.ShitterList;
import com.qza.timer.NecronTimer;
import com.qza.timer.ServerTickClock;
import com.qza.util.DungeonState;
import com.qza.util.PlayerFaces;
import com.qza.util.PlayerLookup;
import com.qza.util.Scheduler;
import com.qza.waypoint.WaypointEditHud;
import com.qza.waypoint.WaypointEditor;
import com.qza.waypoint.WaypointList;
import com.qza.waypoint.WaypointRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
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
        ChatHistory.load();
        MusicLibrary.ensureDir();
        MusicManager.get().applySettings();
        PartyState.init();
        WaypointList.load();
        WaypointRenderer.init();
        WaypointEditor.init();

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "notifications"),
                new NotificationHud());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "waypointedit"),
                new WaypointEditHud());

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
            WitherKey.onChatMessage(plain);
            PartyNotification.onChatMessage(plain);
            PartyFullAlert.onChatMessage(plain);
            ChatHistory.onChatMessage(message, plain);
            ChannelHistory.onChatMessage(message, plain);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Scheduler.tick();
            WitherKey.tick();

            Object level = client.level;
            if (level != lastLevel) {
                lastLevel = level;
                MusicManager.get().stopNow();
                NecronTimer.reset();
                WitherKey.reset();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MusicManager.get().stopNow();
            Scheduler.clear();
            ShitterAutoKick.reset();
            NecronTimer.reset();
            WitherKey.reset();
            ServerTickClock.reset();
            PartyNotification.clear();
            PartyFullAlert.reset();
            WaypointEditor.reset();
            ChatNotification.clear();
            PlayerFaces.clearCache();
            DungeonState.reset();
        });

        LOGGER.info("QZA initialised - {} shitter(s) loaded", ShitterList.size());
    }
}
