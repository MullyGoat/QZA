package com.qza.mixin;

import com.qza.tweaks.DungeonWarp;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientPacketListener.class)
public class CommandRewriteMixin {

    @ModifyVariable(method = "sendCommand(Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true)
    private String qzaDungeonWarp(String command) {
        return DungeonWarp.rewrite(command);
    }
}
