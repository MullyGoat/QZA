package com.qza.mixin;

import com.qza.dungeon.LeapMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerScreen.class)
public class LeapMenuBackgroundMixin {

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void qzaLeapMenuBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                       CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (LeapMenu.active(screen)) {
            screen.extractTransparentBackground(graphics);
            ci.cancel();
        }
    }
}
