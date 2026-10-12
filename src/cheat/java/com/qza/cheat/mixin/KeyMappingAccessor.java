package com.qza.cheat.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("isDown")
    void qzaSetDown(boolean down);

    @Accessor("clickCount")
    int qzaClickCount();

    @Accessor("clickCount")
    void qzaSetClickCount(int count);
}
