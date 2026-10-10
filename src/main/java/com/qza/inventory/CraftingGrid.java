package com.qza.inventory;

import com.qza.config.ConfigManager;
import com.qza.util.SkyBlockArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

public final class CraftingGrid {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 166;
    private static final int LEFT = 96;
    private static final int TOP = 16;
    private static final int RIGHT = 173;
    private static final int BOTTOM = 54;
    private static final int BLANK_U = 95;

    private CraftingGrid() {
    }

    public static boolean hidden() {
        return ConfigManager.get().hideCrafting && SkyBlockArea.onSkyBlock();
    }

    public static boolean hidesSlot(Slot slot) {
        if (!ConfigManager.get().hideCrafting) {
            return false;
        }
        int index = slot.index;
        if (index < InventoryMenu.RESULT_SLOT || index >= InventoryMenu.CRAFT_SLOT_END) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.inventoryMenu.getSlot(index) == slot && SkyBlockArea.onSkyBlock();
    }

    public static void drawBackground(GuiGraphicsExtractor graphics, int left, int top) {
        piece(graphics, left, top, 0, 0, WIDTH, TOP);
        piece(graphics, left, top, 0, TOP, LEFT, BOTTOM - TOP);
        piece(graphics, left, top, RIGHT, TOP, WIDTH - RIGHT, BOTTOM - TOP);
        piece(graphics, left, top, 0, BOTTOM, WIDTH, HEIGHT - BOTTOM);
        graphics.blit(RenderPipelines.GUI_TEXTURED, AbstractContainerScreen.INVENTORY_LOCATION,
                left + LEFT, top + TOP, BLANK_U, TOP, RIGHT - LEFT, BOTTOM - TOP, 1, BOTTOM - TOP, 256, 256);
    }

    private static void piece(GuiGraphicsExtractor graphics, int left, int top, int x, int y, int w, int h) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, AbstractContainerScreen.INVENTORY_LOCATION,
                left + x, top + y, x, y, w, h, 256, 256);
    }
}
