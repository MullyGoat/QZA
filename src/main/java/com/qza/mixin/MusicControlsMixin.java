package com.qza.mixin;

import com.qza.media.MusicOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class MusicControlsMixin {

    @Inject(method = "extractCarriedItem", at = @At("HEAD"))
    private void qzaMusicControls(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        MusicOverlay.renderScreen((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY);
    }
}
