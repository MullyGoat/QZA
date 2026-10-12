package com.qza.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.qza.QZA;
import com.qza.chat.ChatCleaner;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChatComponent.class)
public class ChatCaptureMixin {

    @WrapMethod(method = "addMessage(Lnet/minecraft/network/chat/Component;"
            + "Lnet/minecraft/network/chat/MessageSignature;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V")
    private void qzaCleanChatLine(Component message, MessageSignature signature, GuiMessageSource source,
                                  GuiMessageTag tag, Operation<Void> original) {
        if (message == null || ChatCleaner.emitting()) {
            original.call(message, signature, source, tag);
            return;
        }
        try {
            ChatCleaner.add((ChatComponent) (Object) this, message, signature, source, tag);
        } catch (RuntimeException e) {
            QZA.LOGGER.error("QZA could not clean a chat line", e);
            original.call(message, signature, source, tag);
        }
    }
}
