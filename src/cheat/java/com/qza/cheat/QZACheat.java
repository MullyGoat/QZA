package com.qza.cheat;

import com.qza.QZA;
import com.qza.cheat.movement.DungeonBoss;
import com.qza.cheat.movement.MovementCommand;
import com.qza.cheat.movement.MovementCopier;
import com.qza.cheat.movement.MovementHud;
import com.qza.cheat.movement.MovementRenderer;
import com.qza.compat.Mc;
import com.qza.dungeon.StarredMobs;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public class QZACheat implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CheatConfigManager.load();

        ClientTickEvents.START_CLIENT_TICK.register(MovementCopier::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DeathBow.tick();
            MelodyAim.tick(Mc.screen());
        });
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
            DeathBow.reset();
            DungeonBoss.reset();
            MovementCopier.onLevelChange();
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                DungeonBoss.onChatMessage(message.getString());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MelodyAim.reset();
            DungeonBoss.reset();
            MovementCopier.reset();
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                MovementCommand.register(dispatcher));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(QZA.MOD_ID, "movement_copier"),
                new MovementHud());
        MovementRenderer.init();

        StarredMobs.useLines(() -> CheatConfigManager.get().starredMobEsp
                ? EspLines.get() : RenderTypes.lines());
    }
}
