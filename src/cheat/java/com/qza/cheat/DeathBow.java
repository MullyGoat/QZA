package com.qza.cheat;

import com.qza.cheat.mixin.CarriedItemInvoker;
import com.qza.util.IgnUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.regex.Pattern;

// Ported from JD's Death Bow (ArcherCrit): the same hooks, packets, slots and tick timing.
public final class DeathBow {
    private static final Pattern ARMOR_SETS_TITLE = Pattern.compile("^\\(\\d+/\\d+\\) Armor Sets$");
    private static final int WARDROBE_DYE_BASE = 35;
    private static final int MENU_TIMEOUT_TICKS = 40;

    private static final int IDLE = 0;
    private static final int WAITING_MENU = 1;
    private static final int WAITING_CLOSE = 2;

    private static volatile boolean pendingHotbarSwap;
    private static volatile boolean pendingRaiderFromBow;
    private static int raiderState = IDLE;
    private static int raiderTicks;
    private static int closeTicksRemaining = -1;

    private DeathBow() {
    }

    public static void onReleaseHead(LivingEntity entity) {
        pendingHotbarSwap = false;
        pendingRaiderFromBow = false;
        CheatConfig cfg = CheatConfigManager.get();
        if (!cfg.deathBowEnabled) {
            return;
        }
        if (!(entity instanceof LocalPlayer player) || player != Minecraft.getInstance().player) {
            return;
        }
        ItemStack used = player.getUseItem();
        if (used.isEmpty()) {
            return;
        }
        String name = IgnUtil.stripCodes(used.getHoverName().getString());
        if (name == null || !name.toLowerCase(Locale.ROOT).contains("death bow")) {
            return;
        }
        if (cfg.deathBowHotbarSwap) {
            pendingHotbarSwap = true;
        }
        if (cfg.deathBowRaiderSwap) {
            pendingRaiderFromBow = true;
        }
    }

    public static void onReleaseReturn(LivingEntity entity) {
        if (entity != Minecraft.getInstance().player) {
            pendingHotbarSwap = false;
            pendingRaiderFromBow = false;
            return;
        }
        if (pendingHotbarSwap) {
            pendingHotbarSwap = false;
            swapSlot(clamp((int) CheatConfigManager.get().deathBowHotbarSlot, 1, 8) - 1);
        }
        if (pendingRaiderFromBow) {
            pendingRaiderFromBow = false;
            beginRaiderSwap();
        }
    }

    public static void tick() {
        if (!CheatConfigManager.get().deathBowEnabled) {
            return;
        }
        switch (raiderState) {
            case WAITING_MENU -> tickWaitingMenu();
            case WAITING_CLOSE -> tickWaitingClose();
            default -> {
            }
        }
    }

    private static void tickWaitingMenu() {
        raiderTicks++;
        if (!wardrobeOpen()) {
            if (raiderTicks >= MENU_TIMEOUT_TICKS) {
                reset();
            }
            return;
        }

        CheatConfig cfg = CheatConfigManager.get();
        int slot = WARDROBE_DYE_BASE + clamp((int) cfg.deathBowRaiderSlot, 1, 9);
        if (!clickContainerSlot(slot)) {
            reset();
            return;
        }

        int delay = Math.max((int) cfg.deathBowCloseDelay, 0);
        if (delay == 0) {
            closeWardrobe();
            reset();
        } else {
            raiderState = WAITING_CLOSE;
            closeTicksRemaining = delay;
        }
    }

    private static void tickWaitingClose() {
        if (closeTicksRemaining < 0) {
            reset();
            return;
        }
        closeTicksRemaining--;
        if (closeTicksRemaining > 0) {
            return;
        }
        closeTicksRemaining = -1;
        closeWardrobe();
        reset();
    }

    private static boolean swapSlot(int slot) {
        if (slot < 0 || slot >= 9) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        if (inventory.getSelectedSlot() == slot) {
            return true;
        }
        inventory.setSelectedSlot(slot);
        MultiPlayerGameMode gameMode = client.gameMode;
        if (gameMode == null) {
            return false;
        }
        ((CarriedItemInvoker) gameMode).qzaEnsureHasSentCarriedItem();
        return true;
    }

    private static void beginRaiderSwap() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        raiderState = WAITING_MENU;
        raiderTicks = 0;
        connection.sendCommand("wd");
    }

    private static boolean wardrobeOpen() {
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }
        String title = IgnUtil.stripCodes(screen.getTitle().getString());
        title = title == null ? "" : title.trim();
        return ARMOR_SETS_TITLE.matcher(title).matches()
                || title.toLowerCase(Locale.ROOT).contains("wardrobe");
    }

    private static boolean clickContainerSlot(int slot) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            return false;
        }
        MultiPlayerGameMode gameMode = client.gameMode;
        if (gameMode == null) {
            return false;
        }
        Screen current = client.screen;
        if (!(current instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }
        int containerId = screen.getMenu().containerId;
        if (player.containerMenu.containerId != containerId) {
            return false;
        }
        gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP, player);
        return true;
    }

    private static void closeWardrobe() {
        if (!wardrobeOpen()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.closeContainer();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static void reset() {
        raiderState = IDLE;
        raiderTicks = 0;
        closeTicksRemaining = -1;
        pendingRaiderFromBow = false;
    }
}
