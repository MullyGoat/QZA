package com.qza.mixin;

import com.qza.tweaks.ItemInspect;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class ItemInspectArmMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void qzaInspectArm(AvatarRenderState state, CallbackInfo ci) {
        float raise = ItemInspect.thirdPersonArm(state.id);
        if (raise <= 0f) {
            return;
        }
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        ItemInspect.poseArm(state.mainArm == HumanoidArm.LEFT ? model.leftArm : model.rightArm,
                state.mainArm == HumanoidArm.LEFT, raise);
    }
}
