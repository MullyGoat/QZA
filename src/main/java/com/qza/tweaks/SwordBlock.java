package com.qza.tweaks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.CustomItemEntry;
import com.qza.config.QZAConfig;
import com.qza.custom.CustomItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public final class SwordBlock {
    private SwordBlock() {
    }

    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomItemEntry custom = CustomItems.active(stack);
        if (custom != null) {
            return custom.block;
        }
        return stack.is(ItemTags.SWORDS);
    }

    public static boolean localBlocking() {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.swordBlockEnabled) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || Mc.screen() != null || !client.options.keyUse.isDown()) {
            return false;
        }
        if (player.isUsingItem() && player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
            return false;
        }
        return isSword(player.getMainHandItem());
    }

    public static boolean armBlocking(Avatar avatar, ItemStack stack, InteractionHand hand) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.swordBlockEnabled || !isSword(stack)) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && avatar == player) {
            return cfg.swordBlockThirdPerson && hand == InteractionHand.MAIN_HAND && localBlocking();
        }
        return cfg.swordBlockOthers && avatar.isUsingItem() && avatar.getUsedItemHand() == hand;
    }

    public static boolean pushFirstPerson(ItemDisplayContext context, PoseStack pose) {
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !localBlocking()) {
            return false;
        }
        boolean right = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        if ((player.getMainArm() == HumanoidArm.RIGHT) != right) {
            return false;
        }
        float side = right ? 1f : -1f;
        pose.pushPose();
        pose.mulPose(new Matrix4f()
                .rotateY((float) Math.toRadians(45f * side))
                .translate(-0.4f * side, 0.16f, 0f)
                .rotateY((float) Math.toRadians(30f * side))
                .rotateX((float) Math.toRadians(-80f))
                .rotateY((float) Math.toRadians(15f * side)));
        return true;
    }
}
