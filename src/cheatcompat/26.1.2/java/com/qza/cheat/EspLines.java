package com.qza.cheat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.qza.QZA;
import com.qza.cheat.mixin.RenderPipelinesAccessor;
import com.qza.cheat.mixin.RenderTypeInvoker;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class EspLines {
    private static RenderType type;

    private EspLines() {
    }

    public static RenderType get() {
        if (type == null) {
            RenderPipeline pipeline = RenderPipelinesAccessor.qzaRegister(
                    RenderPipeline.builder(RenderPipelinesAccessor.qzaLinesSnippet())
                            .withLocation(Identifier.fromNamespaceAndPath(QZA.MOD_ID,
                                    "pipeline/lines_through_walls"))
                            .withDepthStencilState(Optional.empty())
                            .build());
            type = RenderTypeInvoker.qzaCreate("qza_lines_through_walls",
                    RenderSetup.builder(pipeline)
                            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                            .createRenderSetup());
        }
        return type;
    }
}
