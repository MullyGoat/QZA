package com.qza.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qza.compat.Mc;
import com.qza.tweaks.NoLoadingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public class NoLoadingScreenMixin {
    @WrapOperation(method = "startWaitingForNewLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreenAndShow(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void qzaSkipLoadingScreen(Minecraft minecraft, Screen screen, Operation<Void> original) {
        if (NoLoadingScreen.skip(screen)) {
            Mc.setScreen(null);
            return;
        }
        original.call(minecraft, screen);
    }
}
