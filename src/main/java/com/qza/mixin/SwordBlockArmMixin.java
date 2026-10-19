package com.qza.mixin;

import com.qza.tweaks.SwordBlock;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public class SwordBlockArmMixin {

    @Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At("HEAD"), cancellable = true)
    private static void qzaSwordBlockArm(Avatar avatar, ItemStack itemInHand, InteractionHand hand,
                                         CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        if (SwordBlock.armBlocking(avatar, itemInHand, hand)) {
            cir.setReturnValue(HumanoidModel.ArmPose.BLOCK);
        }
    }
}
