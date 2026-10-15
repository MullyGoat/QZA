package com.qza.mixin;

import com.qza.inventory.ItemProtection;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.hysky.skyblocker.skyblock.item.ItemProtection")
public class SkyblockerStarMixin {

    @Inject(method = "drawSlotIcon", at = @At("HEAD"), cancellable = true)
    private static void qzaHideSkyblockerStar(GuiGraphicsExtractor graphics, int x, int y, CallbackInfo ci) {
        if (ItemProtection.enabled()) {
            ci.cancel();
        }
    }
}
