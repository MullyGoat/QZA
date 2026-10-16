package com.qza.mixin;

import com.qza.tweaks.FullBright;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class FullBrightMixin {

    @Inject(method = "extract", at = @At("TAIL"))
    private void qzaFullBright(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        FullBright.apply(renderState);
    }
}
