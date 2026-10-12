package com.qza.mixin;

import com.qza.dungeon.SplitTimers;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LerpingBossEvent.class)
public class BossProgressMixin {

    @Inject(method = "setProgress", at = @At("HEAD"))
    private void qzaBossProgress(float progress, CallbackInfo ci) {
        SplitTimers.onBossProgress(((LerpingBossEvent) (Object) this).getName().getString(), progress);
    }
}
