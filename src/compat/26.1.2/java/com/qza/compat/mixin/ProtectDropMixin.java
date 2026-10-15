package com.qza.compat.mixin;

import com.qza.inventory.ItemProtection;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class ProtectDropMixin {

    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void qzaProtectDrop(boolean all, CallbackInfoReturnable<Boolean> cir) {
        if (ItemProtection.blockDrop(((LocalPlayer) (Object) this).getMainHandItem())) {
            cir.setReturnValue(false);
        }
    }
}
