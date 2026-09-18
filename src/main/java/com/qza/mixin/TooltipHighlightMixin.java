package com.qza.mixin;

import com.qza.shitter.ShitterHighlight;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Marks shitter list names in tooltips at the moment they are handed to the
 * renderer.
 *
 * The tooltip event fires too early: other party finder mods wrap this call and
 * rebuild the member lines on the way in to add secrets and personal bests, so
 * anything coloured earlier is discarded. Sitting on the method itself means
 * every one of those wrappers has already finished.
 *
 * Replaces the list rather than editing it, since there is no promise the one
 * passed in can be modified.
 */
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
