package com.qza.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
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
public class SwordBlockItemMixin {
    @Shadow
    ItemDisplayContext displayContext;

    @Unique
    private boolean qzaBlockPushed;

    @Inject(method = "submit", at = @At("HEAD"))
    private void qzaBlockStart(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                               int outline, CallbackInfo ci) {
        qzaBlockPushed = SwordBlock.pushFirstPerson(displayContext, poseStack);
    }

    @Inject(method = "submit", at = @At("TAIL"))
    private void qzaBlockEnd(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                             int outline, CallbackInfo ci) {
        if (qzaBlockPushed) {
            qzaBlockPushed = false;
            poseStack.popPose();
        }
    }
}
