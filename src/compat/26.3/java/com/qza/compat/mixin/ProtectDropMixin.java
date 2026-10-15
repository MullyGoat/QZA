package com.qza.compat.mixin;

import com.qza.inventory.ItemProtection;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class ProtectDropMixin {

    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void qzaProtectDrop(LocalPlayer player, boolean all, CallbackInfo ci) {
        if (ItemProtection.blockDrop(player.getMainHandItem())) {
            ci.cancel();
        }
    }
}
