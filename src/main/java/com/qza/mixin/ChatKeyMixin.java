package com.qza.mixin;

import com.qza.chat.ChatKeybind;
import com.qza.gui.QZAChatScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class ChatKeyMixin {

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
            at = @At("HEAD"), cancellable = true)
    private void qzaOpenChat(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) {
            return;
        }

        int bind = ChatKeybind.key();
        if (bind == ChatKeybind.NONE || event.key() != bind) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.screen != null || client.player == null) {
            return;
        }
        if (client.getWindow() == null || window != client.getWindow().handle()) {
            return;
        }

        client.setScreen(new QZAChatScreen());
        ChatKeybind.swallowNextChar();
        ci.cancel();
    }

    @Inject(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V",
            at = @At("HEAD"), cancellable = true)
    private void qzaSwallowChar(long window, CharacterEvent event, CallbackInfo ci) {
        if (ChatKeybind.consumeSwallow()) {
            ci.cancel();
        }
    }
}
