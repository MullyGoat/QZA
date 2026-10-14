package com.qza.cheat.mixin;

import com.qza.cheat.click.AutoClicker;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class AutoClickHoldMixin {

    @Inject(method = "isDown", at = @At("RETURN"), cancellable = true)
    private void qzaAutoClickHold(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && AutoClicker.holdingBack((KeyMapping) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
