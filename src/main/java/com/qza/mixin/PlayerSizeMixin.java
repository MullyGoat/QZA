package com.qza.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qza.tweaks.PlayerSize;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class PlayerSizeMixin {

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"))
    private void qzaPlayerSize(AvatarRenderState state, PoseStack pose, CallbackInfo ci) {
        PlayerSize.apply(state, pose);
    }
}
