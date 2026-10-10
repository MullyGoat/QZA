package com.qza.mixin;

import com.qza.inventory.Equipment;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public class ShieldSlotMixin {

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void qzaHideShieldSlot(CallbackInfoReturnable<Boolean> cir) {
        if (Equipment.hidesSlot((Slot) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
