package com.qza.mixin;

import com.qza.timer.ServerTickClock;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleSetTime", at = @At("HEAD"))
    private void qzaCaptureServerGameTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        ServerTickClock.onServerGameTime(packet.gameTime());
    }
}
