package com.qza.cheat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class QZACheat implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CheatConfigManager.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DeathBow.tick();
            MelodyAim.tick(client.screen);
        });
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> DeathBow.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MelodyAim.reset());
    }
}
