package com.qza.mixin;

import com.qza.tweaks.CommandShortcuts;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ChatScreen.class)
public class CommandRewriteMixin {

    @ModifyArg(method = "handleChatInput", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendCommand(Ljava/lang/String;)V"))
    private String qzaCommandShortcut(String command) {
        return CommandShortcuts.rewrite(command);
    }
}
