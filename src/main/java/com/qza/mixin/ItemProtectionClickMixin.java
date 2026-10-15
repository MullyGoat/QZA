package com.qza.mixin;

import com.qza.inventory.ItemProtection;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class ItemProtectionClickMixin {

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V"),
            cancellable = true)
    private void qzaProtectClick(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (ItemProtection.blockClick((AbstractContainerScreen<?>) (Object) this, slot, slotId, button, input)) {
            ci.cancel();
        }
    }
}
