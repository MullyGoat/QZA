package com.qza.compat.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface GlowPipelinesAccessor {

    @Accessor("OUTLINE_SNIPPET")
    static RenderPipeline.Snippet qzaGlowOutlineSnippet() {
        throw new AssertionError();
    }

    @Invoker("register")
    static RenderPipeline qzaGlowRegister(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
