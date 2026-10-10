package com.qza.tweaks;

import com.qza.config.ConfigManager;
import com.qza.waypoint.WaypointColour;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ItemStars {
    public static final double MIN_SCALE = 50.0;
    public static final double MAX_SCALE = 150.0;

    private static final char STAR = '✪';
    private static final char FIRST_MASTER = '➊';
    private static final char LAST_MASTER = '➎';

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
        float scale = (float) (Math.max(MIN_SCALE, Math.min(MAX_SCALE, ConfigManager.get().itemStarScale)) / 100.0);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 17, y + 17);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, -font.width(text), -8,
                WaypointColour.argb(ConfigManager.get().itemStarColour), true);
        graphics.pose().popMatrix();
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
