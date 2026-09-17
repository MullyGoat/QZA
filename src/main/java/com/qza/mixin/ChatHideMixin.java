package com.qza.mixin;

import com.qza.config.ConfigManager;
import com.qza.gui.QZAChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatHideMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/client/gui/Font;III"
            + "Lnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void qzaHideVanillaChat(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                    int width, ChatComponent.DisplayMode mode, boolean focused,
                                    CallbackInfo ci) {
        if (!ConfigManager.get().hideVanillaChat) {
            return;
        }
        if (Minecraft.getInstance().screen instanceof QZAChatScreen) {
            ci.cancel();
        }
    }
}
