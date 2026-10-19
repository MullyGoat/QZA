package com.qza.compat.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface GlowRenderTypeInvoker {

    @Invoker("create")
    static RenderType qzaGlowCreate(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
