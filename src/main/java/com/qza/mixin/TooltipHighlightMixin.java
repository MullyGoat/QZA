package com.qza.mixin;

import com.qza.shitter.ShitterHighlight;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public class TooltipHighlightMixin {

    @ModifyVariable(
            method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;"
                    + "Ljava/util/List;Ljava/util/Optional;II"
                    + "Lnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2)
    private List<Component> qzaMarkShitters(List<Component> lines) {
        return ShitterHighlight.highlight(lines);
    }
}
