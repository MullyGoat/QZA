package com.qza.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import com.qza.keybind.CommandKeybinds;
import com.qza.tweaks.HotbarScrollLock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseInputMixin {

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void qzaCommandKeybind(long handle, MouseButtonInfo button, int action, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.getWindow() == null || handle != client.getWindow().handle()) {
            return;
        }
        if (CommandKeybinds.onInput(InputConstants.Type.MOUSE.getOrCreate(button.button()), action, button)) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "onScroll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/ScrollWheelHandler;getNextScrollWheelSelection(DII)I"))
    private int qzaHotbarScrollLock(double wheel, int selected, int slots, Operation<Integer> original) {
        return HotbarScrollLock.nextSlot(wheel, selected, original.call(wheel, selected, slots), slots);
    }
}
