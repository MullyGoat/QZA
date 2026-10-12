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

public final class EspFill {
    private static RenderType type;

    private EspFill() {
    }

    public static RenderType get() {
        if (type == null) {
            RenderPipeline pipeline = RenderPipelinesAccessor.qzaRegister(
                    RenderPipeline.builder(RenderPipelinesAccessor.qzaDebugFilledSnippet())
                            .withLocation(Identifier.fromNamespaceAndPath(QZA.MOD_ID,
                                    "pipeline/filled_through_walls"))
                            .withCull(true)
                            .withDepthStencilState(Optional.empty())
                            .build());
            type = RenderTypeInvoker.qzaCreate("qza_filled_through_walls",
                    RenderSetup.builder(pipeline)
                            .sortOnUpload()
                            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                            .createRenderSetup());
        }
        return type;
    }
}
