package com.qza.cheat;

import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.qza.QZA;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class EspFill {
    private static RenderType type;

    private EspFill() {
    }

    public static RenderType get() {
        if (type == null) {
            RenderPipeline pipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                            .withLocation(Identifier.fromNamespaceAndPath(QZA.MOD_ID,
                                    "pipeline/filled_through_walls"))
                            .withCull(true)
                            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                            .build());
            OitPipelineSet oit = RenderPipelines.register(OitPipelineSet.builder("qza_filled_through_walls",
                            RenderPipeline.builder(RenderPipelines.OIT_DEBUG_FILLED_SNIPPET).withCull(true))
                    .withoutDepthTest()
                    .build());
            type = RenderType.create("qza_filled_through_walls",
                    RenderSetup.builder(pipeline)
                            .setOitPipelines(oit)
                            .sortOnUpload()
                            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .createRenderSetup());
        }
        return type;
    }
}
