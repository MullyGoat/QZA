package com.qza.mixin;

import com.qza.tweaks.FullBright;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class NightVisionHideMixin {

    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void qzaHideNightVision(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        if (FullBright.hidesNightVision((LivingEntity) (Object) this, effect)) {
            cir.setReturnValue(false);
        }
    }
}
