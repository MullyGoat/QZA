package com.qza.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.chat.ChannelHistory;
import com.qza.chat.ChatKeybind;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.gui.QZAChatScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class ChatKeyMixin {

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
            at = @At("HEAD"), cancellable = true)
    private void qzaOpenChat(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action != InputConstants.PRESS) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (Mc.screen() != null || client.player == null) {
            return;
        }
        if (client.getWindow() == null || window != client.getWindow().handle()) {
            return;
        }

        int bind = ChatKeybind.key();
        if (bind != ChatKeybind.NONE && event.key() == bind) {
            Mc.setScreen(new QZAChatScreen());
            ChatKeybind.swallowNextChar(event);
            ci.cancel();
            return;
        }

        if (ConfigManager.get().qzaChatEnabled
                && ConfigManager.get().openChatWithT
                && client.options != null
                && client.options.keyCommand.matches(event)) {
            Mc.setScreen(new QZAChatScreen(ChannelHistory.EVERYTHING, "/"));
            ChatKeybind.swallowNextChar(event);
            ci.cancel();
        }
    }

    @Inject(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V",
            at = @At("HEAD"), cancellable = true)
    private void qzaSwallowChar(long window, CharacterEvent event, CallbackInfo ci) {
        if (ChatKeybind.consumeSwallow(event.codepoint())) {
            ci.cancel();
        }
    }
}
