package com.qza.mixin;

import com.qza.inventory.CraftingGrid;
import com.qza.inventory.Equipment;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public class ShieldSlotMixin {

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void qzaHideSlot(CallbackInfoReturnable<Boolean> cir) {
        Slot slot = (Slot) (Object) this;
        if (Equipment.hidesSlot(slot) || CraftingGrid.hidesSlot(slot)) {
            cir.setReturnValue(false);
        }
    }
}
