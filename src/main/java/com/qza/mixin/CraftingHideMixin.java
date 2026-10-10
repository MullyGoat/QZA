package com.qza.mixin;

import com.qza.inventory.CraftingGrid;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public class CraftingHideMixin {

    @Inject(method = "extractBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit"))
    private void qzaCraftingBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                       CallbackInfo ci) {
        if (CraftingGrid.hidden()) {
            ContainerScreenAccessor accessor = (ContainerScreenAccessor) (Object) this;
            CraftingGrid.drawBackground(graphics, accessor.qzaGuiLeft(), accessor.qzaGuiTop());
        }
    }

    @ModifyArg(method = "extractBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit"), index = 6)
    private int qzaCraftingBackgroundWidth(int width) {
        return CraftingGrid.hidden() ? 0 : width;
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void qzaCraftingLabel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (CraftingGrid.hidden()) {
            ci.cancel();
        }
    }
}
