package com.qza.cheat.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Accessor("rightClickDelay")
    int qzaRightClickDelay();

    @Accessor("rightClickDelay")
    void qzaSetRightClickDelay(int delay);
}
