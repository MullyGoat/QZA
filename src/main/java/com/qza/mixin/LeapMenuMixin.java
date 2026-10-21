package com.qza.mixin;

import com.qza.dungeon.LeapMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class LeapMenuMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void qzaLeapMenuRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (LeapMenu.active(screen)) {
            LeapMenu.render(screen, graphics, mouseX, mouseY);
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void qzaLeapMenuClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (!LeapMenu.active(screen)) {
            return;
        }
        if (LeapMenu.drawing(screen)) {
            cir.setReturnValue(LeapMenu.click(screen, event.x(), event.y(), event.button()));
        } else {
            LeapMenu.covered(screen);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void qzaLeapMenuRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (LeapMenu.active(screen) && LeapMenu.drawing(screen)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void qzaLeapMenuDrag(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (LeapMenu.active(screen) && LeapMenu.drawing(screen)) {
            cir.setReturnValue(true);
        }
    }
}
