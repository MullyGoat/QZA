package com.qza.tweaks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.IgnUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SwordBlock {
    private static final Pattern SKYBLOCK_SWORD = Pattern.compile(
            "^(?:A )?(?:VERY SPECIAL|SPECIAL|ULTIMATE|ADMIN|DIVINE|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON)\\b.*\\bSWORD\\b");
    private static final int TYPE_LINES = 3;

    private SwordBlock() {
    }

    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(ItemTags.SWORDS)) {
            return true;
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return false;
        }
        List<Component> lines = lore.lines();
        int checked = 0;
        for (int i = lines.size() - 1; i >= 0 && checked < TYPE_LINES; i--) {
            String line = IgnUtil.stripCodes(lines.get(i).getString()).trim().toUpperCase(Locale.ROOT);
            if (line.isEmpty()) {
                continue;
            }
            checked++;
            if (SKYBLOCK_SWORD.matcher(line).find()) {
                return true;
            }
        }
        return false;
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
