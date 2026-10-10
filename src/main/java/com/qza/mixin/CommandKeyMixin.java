package com.qza.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.keybind.CommandKeybinds;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class CommandKeyMixin {

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
            at = @At("HEAD"), cancellable = true)
    private void qzaCommandKeybind(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.getWindow() == null || window != client.getWindow().handle()) {
            return;
        }
        if (CommandKeybinds.onInput(InputConstants.getKey(event), action, event)) {
            ci.cancel();
        }
    }
}
