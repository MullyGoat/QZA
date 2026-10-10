package com.qza.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {

    @Accessor("leftPos")
    int qzaGuiLeft();

    @Accessor("topPos")
    int qzaGuiTop();

    @Accessor("imageWidth")
    int qzaGuiWidth();

    @Accessor("hoveredSlot")
    Slot qzaHoveredSlot();
}
