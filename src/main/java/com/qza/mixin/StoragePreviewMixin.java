package com.qza.mixin;

import com.qza.inventory.StoragePreview;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class StoragePreviewMixin {

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void qzaStoragePreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (StoragePreview.draw((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY)) {
            ci.cancel();
        }
    }
}
