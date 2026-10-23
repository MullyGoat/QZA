package com.qza.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qza.custom.CustomItems;
import com.qza.tweaks.ItemInspect;
import com.qza.tweaks.SwordBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public class ItemInspectMixin {
    @Shadow
    ItemDisplayContext displayContext;

    @Unique
    private int qzaHeldPushes;

    @Inject(method = "submit", at = @At("HEAD"))
    private void qzaHeldStart(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                              int outline, CallbackInfo ci) {
        qzaHeldPushes = 0;
        if (CustomItems.drawingPart()) {
            return;
        }
        ItemStackRenderState state = (ItemStackRenderState) (Object) this;
        if (CustomItems.pushThirdPerson(state, displayContext, poseStack)) {
            qzaHeldPushes++;
        }
        if (ItemInspect.pushThirdPerson(state, displayContext, poseStack)) {
            qzaHeldPushes++;
        }
        if (CustomItems.pushSwing(state, displayContext, poseStack)) {
            qzaHeldPushes++;
        }
        if (SwordBlock.pushFirstPerson(displayContext, poseStack)) {
            qzaHeldPushes++;
        }
        if (ItemInspect.pushHand(state, displayContext, poseStack, collector, light)) {
            qzaHeldPushes++;
        }
        if (CustomItems.pushViewmodel(state, displayContext, poseStack)) {
            qzaHeldPushes++;
        }
        if (ItemInspect.pushHeld(poseStack)) {
            qzaHeldPushes++;
        }
        if (CustomItems.pushHook(state, displayContext, poseStack)) {
            qzaHeldPushes++;
        }
        if (CustomItems.push(state, displayContext, poseStack, collector, light, overlay, outline)) {
            qzaHeldPushes++;
        }
    }

    @Inject(method = "submit", at = @At("TAIL"))
    private void qzaHeldEnd(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                            int outline, CallbackInfo ci) {
        for (; qzaHeldPushes > 0; qzaHeldPushes--) {
            poseStack.popPose();
        }
        if (!CustomItems.drawingPart()) {
            CustomItems.end();
        }
    }
}
