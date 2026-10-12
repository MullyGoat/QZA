package com.qza.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qza.chat.ChatStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChatScreen.class)
public class ChatInputStyleMixin {

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void qzaStyleInput(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int colour,
                               Operation<Void> original) {
        if (!ChatStyle.enabled()) {
            original.call(graphics, x0, y0, x1, y1, colour);
            return;
        }
        ChatStyle.inputBox(graphics, x0, y0, x1, y1);
    }
}
