package com.qza.cheat.mixin;

import com.qza.cheat.movement.MovementCopier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class MoveSpeedMixin {

    @Inject(method = "modifyInput", at = @At("RETURN"), cancellable = true)
    private void qzaMatchSpeed(Vec2 input, CallbackInfoReturnable<Vec2> cir) {
        float scale = MovementCopier.inputScale((LocalPlayer) (Object) this);
        if (scale < 1.0f) {
            cir.setReturnValue(cir.getReturnValue().scale(scale));
        }
    }
}
