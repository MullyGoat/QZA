package com.qza.tweaks;

import com.qza.config.ConfigManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ItemStars {
    private static final char STAR = '✪';
    private static final char FIRST_MASTER = '➊';
    private static final char LAST_MASTER = '➎';
    private static final int COLOUR = 0xFFFF55FF;

    private ItemStars() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y, String countText) {
        if (!ConfigManager.get().itemStarCount || stack.isEmpty() || stack.getCount() != 1 || countText != null) {
            return;
        }
        int stars = count(stack);
        if (stars <= 0) {
            return;
        }
        String text = String.valueOf(stars);
        graphics.text(font, text, x + 19 - 2 - font.width(text), y + 6 + 3, COLOUR, true);
    }

    public static int count(ItemStack stack) {
        String name = stack.getHoverName().getString();
        if (name.indexOf(STAR) < 0) {
            return 0;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            int level = data.copyTag().getIntOr("upgrade_level", 0);
            if (level > 0) {
                return level;
            }
        }
        int stars = 0;
        int master = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == STAR) {
                stars++;
            } else if (c >= FIRST_MASTER && c <= LAST_MASTER) {
                master = c - FIRST_MASTER + 1;
            }
        }
        return Math.min(stars, 5) + master;
    }
}
