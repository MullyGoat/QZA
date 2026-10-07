package com.qza.mixin;

import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LerpingBossEvent.class)
public interface BossEventAccessor {

    @Accessor("targetPercent")
    float qzaTargetPercent();
}
