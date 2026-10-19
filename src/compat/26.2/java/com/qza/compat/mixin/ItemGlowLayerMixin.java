package com.qza.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qza.render.ItemGlow;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class ItemGlowLayerMixin {
    @Shadow
    @Final
    private List<BakedQuad> quads;

    @Shadow
    private SpecialModelRenderer<Object> specialRenderer;

    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void qzaGlowLayer(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                              int outline, CallbackInfo ci) {
        if (specialRenderer == null && ItemGlow.collecting()) {
            ItemGlow.layer(poseStack.last().pose(), quads);
        }
    }
}
