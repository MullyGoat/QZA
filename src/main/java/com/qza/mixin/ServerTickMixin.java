package com.qza.mixin;

import com.qza.timer.ServerTickClock;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class ServerTickMixin {

    @Inject(method = "handlePing", at = @At("HEAD"))
    private void qzaCountServerTick(ClientboundPingPacket packet, CallbackInfo ci) {
        if (packet.getId() != 0) {
            ServerTickClock.onServerTick();
        }
    }
}
