package com.qza.compat;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qza.QZA;
import com.qza.compat.mixin.GlowPipelinesAccessor;
import com.qza.compat.mixin.GlowRenderTypeInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class GlowRender {
    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();
    private static final PoseStack IDENTITY = new PoseStack();
    private static RenderPipeline pipeline;

    private GlowRender() {
    }

    public static void submit(SubmitNodeCollector collector, Identifier atlas, List<RenderType> under,
                              Consumer<VertexConsumer> geometry) {
        collector.submitCustomGeometry(IDENTITY, glow(atlas), (pose, out) -> {
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            for (RenderType type : under) {
                buffers.endBatch(type);
            }
            geometry.accept(out);
        });
    }

    private static RenderType glow(Identifier atlas) {
        return TYPES.computeIfAbsent(atlas, id -> GlowRenderTypeInvoker.qzaGlowCreate("qza_item_glow",
                RenderSetup.builder(pipeline())
                        .withTexture("Sampler0", id)
                        .createRenderSetup()));
    }

    private static RenderPipeline pipeline() {
        if (pipeline == null) {
            pipeline = GlowPipelinesAccessor.qzaGlowRegister(
                    RenderPipeline.builder(GlowPipelinesAccessor.qzaGlowOutlineSnippet())
                            .withLocation(Identifier.fromNamespaceAndPath(QZA.MOD_ID, "pipeline/item_glow"))
                            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                            .withCull(false)
                            .build());
        }
        return pipeline;
    }
}
