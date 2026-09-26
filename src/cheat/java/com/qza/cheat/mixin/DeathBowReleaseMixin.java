package com.qza.cheat.mixin;

import com.qza.cheat.DeathBow;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class DeathBowReleaseMixin {

    @Inject(method = "releaseUsingItem", at = @At("HEAD"))
    private void qzaDeathBowReleaseHead(CallbackInfo ci) {
        DeathBow.onReleaseHead((LivingEntity) (Object) this);
    }

    @Inject(method = "releaseUsingItem", at = @At("RETURN"))
    private void qzaDeathBowReleaseReturn(CallbackInfo ci) {
        DeathBow.onReleaseReturn((LivingEntity) (Object) this);
    }
}
