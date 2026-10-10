package com.qza.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qza.tweaks.TooltipScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public class TooltipScaleMixin {
    @Unique
    private float qzaTooltipScale = 1f;
    @Unique
    private int qzaTooltipX;
    @Unique
    private int qzaTooltipY;

    @WrapOperation(method = "tooltip", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"))
    private Vector2ic qzaPlaceScaledTooltip(ClientTooltipPositioner positioner, int screenW, int screenH,
                                            int x, int y, int w, int h, Operation<Vector2ic> original) {
        float scale = TooltipScale.scaleFor(screenW, screenH, w, h);
        qzaTooltipScale = scale;
        if (scale == 1f) {
            return original.call(positioner, screenW, screenH, x, y, w, h);
        }
        Vector2ic at = original.call(positioner, screenW, screenH, x, y,
                Math.round(w * scale), Math.round(h * scale));
        qzaTooltipX = at.x();
        qzaTooltipY = at.y();
        return at;
    }

    @Inject(method = "tooltip", at = @At(value = "INVOKE",
            target = "Lorg/joml/Matrix3x2fStack;pushMatrix()Lorg/joml/Matrix3x2fStack;",
            shift = At.Shift.AFTER))
    private void qzaScaleTooltip(CallbackInfo ci) {
        if (qzaTooltipScale == 1f) {
            return;
        }
        ((GuiGraphicsExtractor) (Object) this).pose()
                .translate(qzaTooltipX, qzaTooltipY)
                .scale(qzaTooltipScale)
                .translate(-qzaTooltipX, -qzaTooltipY);
        qzaTooltipScale = 1f;
    }
}
