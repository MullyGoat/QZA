package com.qza.cheat.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Accessor("xpos")
    void qzaSetXpos(double x);

    @Accessor("ypos")
    void qzaSetYpos(double y);
}
