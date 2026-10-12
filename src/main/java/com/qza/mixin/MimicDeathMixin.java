package com.qza.mixin;

import com.qza.dungeon.DungeonScore;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class MimicDeathMixin {
    private static final byte DEATH_EVENT = 3;

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void qzaMimicDeath(byte id, CallbackInfo ci) {
        if (id == DEATH_EVENT && (Object) this instanceof Zombie zombie && zombie.level().isClientSide()) {
            DungeonScore.onZombieDeath(zombie);
        }
    }
}
