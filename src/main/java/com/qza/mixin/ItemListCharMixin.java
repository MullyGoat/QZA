package com.qza.mixin;

import com.qza.itemlist.ItemList;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class ItemListCharMixin {

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void qzaItemListSearch(long window, CharacterEvent event, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (window == client.getWindow().handle() && client.getOverlay() == null
                && ItemList.charTyped(client.screen, event)) {
            ci.cancel();
        }
    }
}
