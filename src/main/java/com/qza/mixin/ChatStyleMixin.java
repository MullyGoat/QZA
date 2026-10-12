package com.qza.mixin;

import com.qza.chat.ChatStyle;
import com.qza.compat.Mc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatStyleMixin {
    @Shadow
    @Final
    private static Style QUEUE_EXPAND_TEXT_STYLE;

    @Shadow
    @Final
    private static Component RESTRICTED_CHAT_MESSAGE;

    @Shadow
    @Final
    private static Component RESTRICTED_CHAT_MESSAGE_WITH_HOVER;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    private int chatScrollbarPos;

    @Shadow
    private boolean newMessageSinceScroll;

    @Shadow
    public abstract int getLinesPerPage();

    @Shadow
    private int getWidth() {
        throw new AssertionError();
    }

    @Shadow
    private double getScale() {
        throw new AssertionError();
    }

    @Shadow
    private int getLineHeight() {
        throw new AssertionError();
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;"
            + "IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At("HEAD"), cancellable = true)
    private void qzaStyleChat(ChatComponent.ChatGraphicsAccess graphics, int screenHeight, int ticks,
                              ChatComponent.DisplayMode mode, CallbackInfo ci) {
        if (!ChatStyle.enabled()) {
            return;
        }
        ci.cancel();

        float scale = (float) getScale();
        long queued = Mc.chatQueueSize();
        Component queue = queued > 0L
                ? Component.translatable("chat.queue", queued).setStyle(QUEUE_EXPAND_TEXT_STYLE)
                : null;
        float textOpacity = minecraft.options.chatOpacity().get().floatValue() * 0.9F + 0.1F;

        ChatStyle.render(graphics, new ChatStyle.Frame(minecraft.font, trimmedMessages, chatScrollbarPos,
                newMessageSinceScroll, screenHeight, ticks, mode, scale, Mth.ceil(getWidth() / scale),
                getLineHeight(), getLinesPerPage() * getLineHeight(), textOpacity, queue,
                RESTRICTED_CHAT_MESSAGE, RESTRICTED_CHAT_MESSAGE_WITH_HOVER));
    }

    @Inject(method = "scrollChat", at = @At("HEAD"), cancellable = true)
    private void qzaScrollChat(int dir, CallbackInfo ci) {
        if (!ChatStyle.enabled()) {
            return;
        }
        ci.cancel();

        int max = ChatStyle.maxScroll(trimmedMessages, getLinesPerPage() * getLineHeight(), getLineHeight());
        chatScrollbarPos = Mth.clamp(chatScrollbarPos + dir, 0, max);
        if (chatScrollbarPos == 0) {
            newMessageSinceScroll = false;
        }
    }
}
