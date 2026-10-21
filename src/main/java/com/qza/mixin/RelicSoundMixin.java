package com.qza.mixin;

import com.qza.dungeon.RelicTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class RelicSoundMixin {

    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    private void qzaRelicSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            RelicTimer.onSound(packet);
        }
    }
}
