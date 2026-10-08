package com.qza.cheat;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.qza.QZA;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class EspLines {
    private static RenderType type;

    private EspLines() {
    }

    public static RenderType get() {
        if (type == null) {
            RenderPipeline pipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                            .withLocation(Identifier.fromNamespaceAndPath(QZA.MOD_ID,
                                    "pipeline/lines_through_walls"))
                            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                            .build());
            type = RenderType.create("qza_lines_through_walls",
                    RenderSetup.builder(pipeline)
                            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .createRenderSetup());
        }
        return type;
    }
}
