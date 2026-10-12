package com.qza.cheat.mixin;

import com.qza.cheat.movement.MovementCopier;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class KeybindFreezeMixin {

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void qzaFreezeKeybinds(CallbackInfo ci) {
        if (MovementCopier.frozen()) {
            ci.cancel();
        }
    }
}
