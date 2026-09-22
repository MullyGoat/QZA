package com.qza.waypoint;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class WaypointEditHud implements HudElement {
    private static final String TITLE = "QZA WAYPOINT EDIT MODE";
    private static final String HINT = "Right click blocks to mark them, Esc to finish";

    private static final int TITLE_COLOUR = 0xFFFF55FF;
    private static final int HINT_COLOUR = 0xFFAAAAAA;
    private static final int BACKDROP = 0x99000000;
    private static final float TITLE_SCALE = 1.4f;

    private static final int TOP = 8;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!WaypointEditor.active()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        if (client.options.hideGui || font == null) {
            return;
        }

        int middle = client.getWindow().getGuiScaledWidth() / 2;
        int titleWidth = (int) (font.width(TITLE) * TITLE_SCALE);
        int hintWidth = font.width(HINT);
        int widest = Math.max(titleWidth, hintWidth);

        graphics.fill(middle - (widest / 2) - 6, TOP - 4,
                middle + (widest / 2) + 6, TOP + 26, BACKDROP);

        graphics.pose().pushMatrix();
        graphics.pose().translate(middle, TOP);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(font, TITLE, 0, 0, TITLE_COLOUR);
        graphics.pose().popMatrix();

        graphics.centeredText(font, HINT, middle, TOP + 16, HINT_COLOUR);
    }
}
