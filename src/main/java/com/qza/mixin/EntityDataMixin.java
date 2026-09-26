package com.qza.mixin;

import com.qza.dungeon.StarredMobs;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class EntityDataMixin {

    @Inject(method = "handleSetEntityData", at = @At("RETURN"))
    private void qzaStarredMobData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        StarredMobs.onEntityData(packet.id());
    }
}
