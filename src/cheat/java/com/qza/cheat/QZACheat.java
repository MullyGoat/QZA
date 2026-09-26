package com.qza.cheat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class QZACheat implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CheatConfigManager.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> DeathBow.tick());
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> DeathBow.reset());
    }
}
