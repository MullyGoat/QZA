package com.qza.cheat.click;

import com.qza.cheat.CheatConfig;
import com.qza.cheat.CheatConfigManager;
import com.qza.cheat.mixin.KeyMappingAccessor;
import com.qza.cheat.movement.MovementCopier;
import com.qza.compat.Mc;
import com.qza.util.ChatUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class AutoClicker {
    public static final double MIN_CPS = 1.0;
    public static final double MAX_CPS = 20.0;

    private static final int TICKS_PER_SECOND = 20;

    private static double rate;
    private static int cooldown;

    private AutoClicker() {
    }

    public static boolean enabled() {
        return CheatConfigManager.get().autoClickerEnabled;
    }

    public static void reset() {
        rate = 0.0;
        cooldown = 0;
    }

    public static void onStartTick(Minecraft client) {
        cooldown = Math.max(cooldown - 1, 0);
        rate = 0.0;

        LocalPlayer player = client.player;
        if (!enabled() || player == null || MovementCopier.playing()) {
            return;
        }
        ClickItem bound = boundTo(player.getMainHandItem());
        if (bound == null) {
            return;
        }
        rate = clamp(bound.cps);

        if (cooldown > 0 || Mc.screen() != null) {
            return;
        }
        KeyMappingAccessor attack = (KeyMappingAccessor) client.options.keyAttack;
        if (!attack.qzaIsDown()) {
            return;
        }
        attack.qzaSetClickCount(attack.qzaClickCount() + 1);
        cooldown = gapTicks(rate);
    }

    public static boolean holdingBack(KeyMapping key) {
        if (rate <= 0.0) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null || key != client.options.keyAttack) {
            return false;
        }
        return Mc.screen() == null;
    }

    static int gapTicks(double cps) {
        double gap = TICKS_PER_SECOND / cps;
        int shortest = Math.max(1, (int) Math.round(gap * 0.5));
        int longest = Math.max(shortest, (int) Math.round(gap * 1.5));
        return shortest + ThreadLocalRandom.current().nextInt((longest - shortest) + 1);
    }

    public static Map<String, ClickItem> items() {
        return CheatConfigManager.get().autoClickerItems;
    }

    public static List<String> keys() {
        return new ArrayList<>(items().keySet());
    }

    public static ClickItem boundTo(ItemStack stack) {
        String uuid = uuidOf(stack);
        return uuid == null ? null : items().get(uuid);
    }

    public static String uuidOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        String uuid = data.copyTag().getStringOr("uuid", "");
        return uuid.isEmpty() ? null : uuid;
    }

    public static ItemStack held() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getMainHandItem();
    }

    public static String heldLabel() {
        ItemStack stack = held();
        if (stack.isEmpty()) {
            return "Nothing held";
        }
        String uuid = uuidOf(stack);
        if (uuid == null) {
            return "No UUID";
        }
        return items().containsKey(uuid) ? "Unbind" : "Bind";
    }

    public static void toggleHeld() {
        ItemStack stack = held();
        if (boundTo(stack) != null) {
            unbindHeld();
        } else {
            bindHeld(CheatConfigManager.get().autoClickerCps);
        }
    }

    public static boolean bindHeld(double cps) {
        ItemStack stack = held();
        String uuid = identify(stack);
        if (uuid == null) {
            return false;
        }
        CheatConfig cfg = CheatConfigManager.get();
        ClickItem item = cfg.autoClickerItems.get(uuid);
        if (item == null) {
            item = new ClickItem();
            cfg.autoClickerItems.put(uuid, item);
        }
        item.name = stack.getHoverName().getString();
        item.cps = clamp(cps);
        cfg.autoClickerCps = item.cps;
        CheatConfigManager.save();
        ChatUtil.success("Auto Clicker set to " + number(item.cps) + " clicks a second on "
                + item.name + ".");
        return true;
    }

    public static boolean unbindHeld() {
        ItemStack stack = held();
        String uuid = identify(stack);
        if (uuid == null) {
            return false;
        }
        ClickItem removed = CheatConfigManager.get().autoClickerItems.remove(uuid);
        if (removed == null) {
            ChatUtil.info("Auto Clicker was not set up on " + stack.getHoverName().getString() + ".");
            return false;
        }
        CheatConfigManager.save();
        ChatUtil.info("Auto Clicker taken off " + removed.name + ".");
        return true;
    }

    public static void forget(String uuid) {
        if (CheatConfigManager.get().autoClickerItems.remove(uuid) != null) {
            CheatConfigManager.save();
        }
    }

    public static void setCps(String uuid, double cps) {
        ClickItem item = items().get(uuid);
        if (item == null) {
            return;
        }
        item.cps = clamp(cps);
        CheatConfigManager.save();
    }

    public static void setEnabled(boolean on) {
        CheatConfigManager.get().autoClickerEnabled = on;
        if (!on) {
            reset();
        }
        CheatConfigManager.save();
    }

    public static String status() {
        int count = items().size();
        return "Auto Clicker is " + (enabled() ? "on" : "off") + ", set up on " + count + " item"
                + (count == 1 ? "" : "s") + ".";
    }

    public static String number(double cps) {
        return cps == Math.rint(cps) ? String.valueOf((long) cps)
                : String.format(Locale.ROOT, "%.1f", cps);
    }

    public static String identify(ItemStack stack) {
        if (stack.isEmpty()) {
            ChatUtil.error("You aren't holding anything.");
            return null;
        }
        String uuid = uuidOf(stack);
        if (uuid == null) {
            ChatUtil.error(stack.getHoverName().getString() + " has no SkyBlock UUID, so it can't be "
                    + "told apart from any other one.");
        }
        return uuid;
    }

    private static double clamp(double cps) {
        return Math.max(MIN_CPS, Math.min(MAX_CPS, cps));
    }
}
