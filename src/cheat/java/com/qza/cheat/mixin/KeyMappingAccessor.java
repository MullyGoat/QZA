package com.qza.cheat.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("isDown")
    boolean qzaIsDown();

    @Accessor("isDown")
    void qzaSetDown(boolean down);

    @Accessor("clickCount")
    int qzaClickCount();

    @Accessor("clickCount")
    void qzaSetClickCount(int count);

    @Accessor("key")
    InputConstants.Key qzaKey();
}
