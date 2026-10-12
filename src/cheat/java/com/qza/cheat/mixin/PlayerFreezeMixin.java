package com.qza.cheat.mixin;

import com.qza.cheat.movement.MovementCopier;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class PlayerFreezeMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void qzaFreezePlayer(CallbackInfo ci) {
        if (MovementCopier.frozen()) {
            ci.cancel();
        }
    }
}
