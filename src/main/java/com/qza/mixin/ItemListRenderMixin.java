package com.qza.mixin;

import com.qza.itemlist.ItemList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class ItemListRenderMixin {

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractCarriedItem(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"))
    private void qzaItemList(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                             CallbackInfo ci) {
        ItemList.render((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY, delta);
    }
}
