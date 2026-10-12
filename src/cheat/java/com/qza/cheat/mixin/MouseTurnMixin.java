package com.qza.cheat.mixin;

import com.qza.cheat.movement.MovementCopier;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseTurnMixin {

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void qzaPlaybackLook(CallbackInfo ci) {
        MovementCopier.onFrame();
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void qzaHoldLook(double movementTime, CallbackInfo ci) {
        if (MovementCopier.playing()) {
            ci.cancel();
        }
    }
}
