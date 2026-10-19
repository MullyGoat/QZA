package com.qza.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qza.render.ItemGlow;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public class ItemGlowMixin {
    @Shadow
    ItemDisplayContext displayContext;

    @Inject(method = "submit", at = @At("HEAD"))
    private void qzaGlowBegin(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                              int outline, CallbackInfo ci) {
        ItemGlow.begin(displayContext);
    }

    @Inject(method = "submit", at = @At("TAIL"))
    private void qzaGlowEnd(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                            int outline, CallbackInfo ci) {
        ItemGlow.end(collector);
    }
}
