package com.qza.cheat.mixin;

import com.qza.cheat.movement.MovementCopier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class TeleportMixin {

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void qzaBeforeTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        MovementCopier.beforeTeleport();
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void qzaTeleported(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        MovementCopier.onTeleport();
    }

    @Inject(method = "handleSetEntityMotion", at = @At("RETURN"))
    private void qzaPushed(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            MovementCopier.onMotion(packet.id(), packet.movement());
        }
    }
}
