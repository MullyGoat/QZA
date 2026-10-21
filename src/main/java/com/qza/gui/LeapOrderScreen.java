package com.qza.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.dungeon.DungeonTeam;
import com.qza.dungeon.LeapMenu;
import com.qza.stats.DungeonClass;
import com.qza.util.PlayerFaces;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class LeapOrderScreen extends Screen {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_FAINT = 0xFFAAAAAA;
    private static final int PINK = 0xFFFF55FF;
    private static final int DROP = 0x80FF55FF;
    private static final int SPOT = 0x40FFFFFF;
    private static final float TITLE_SCALE = 1.5f;
    private static final int BUTTON_W = 64;
    private static final int BUTTON_H = 16;
    private static final int PLAYING_W = 150;
    private static final int PLAYING_H = 18;
    private static final int TOP = 38;
    private static final int BOTTOM = 50;

    private static String playing;

    private final List<String> slots = new ArrayList<>();
    private int dragging = -1;
    private double grabX;
    private double grabY;

    public LeapOrderScreen() {
        super(Component.literal("QZA"));
        String self = DungeonTeam.selfClass();
        if (self != null) {
            playing = self;
        } else if (playing == null) {
            playing = LeapMenu.CLASSES.get(0);
        }
        load();
    }

    private void load() {
        dragging = -1;
        slots.clear();
        slots.addAll(LeapMenu.preset(playing));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(graphics);
    }

    private float previewScale() {
        float fit = (this.height - TOP - BOTTOM) / (float) (LeapMenu.BOX_H * 2 + LeapMenu.GAP);
        return Math.max(0.2f, Math.min(LeapMenu.scale(this.width, this.height), fit));
    }

    private int[] rect(int index) {
        float s = previewScale();
        float top = TOP + Math.max(0f, (this.height - TOP - BOTTOM - LeapMenu.gridHeight(s)) / 2f);
        return LeapMenu.boxRect(index, (this.width - LeapMenu.gridWidth(s)) / 2f, top, s);
    }

    private int[] playingRect() {
        return new int[]{(this.width - PLAYING_W) / 2, this.height - PLAYING_H - 10, PLAYING_W, PLAYING_H};
    }

    private int[] doneRect() {
        return new int[]{this.width - BUTTON_W - 8, this.height - BUTTON_H - 11, BUTTON_W, BUTTON_H};
    }

    private int[] resetRect() {
        return new int[]{this.width - (BUTTON_W * 2) - 14, this.height - BUTTON_H - 11, BUTTON_W, BUTTON_H};
    }

    private LeapMenu.Tile tile(int index) {
        String dungeonClass = slots.get(index);
        String label = DungeonClass.label(dungeonClass);
        return new LeapMenu.Tile(PlayerFaces.skinFor(label), label, LeapMenu.colour(dungeonClass), "", TEXT_FAINT,
                false, false);
    }

    private int under(double x, double y) {
        for (int i = 0; i < 4; i++) {
            if (i != dragging && inside(x, y, rect(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(this.width / 2f, 8);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, "CUSTOM LEAP ORDER", 0, 0, PINK);
        graphics.pose().popMatrix();
        graphics.centeredText(this.font, "Drag a player onto another spot to swap them", this.width / 2, 24,
                TEXT_FAINT);

        float s = previewScale();
        int target = dragging >= 0 ? under(mouseX, mouseY) : -1;
        int hovered = dragging < 0 ? under(mouseX, mouseY) : -1;
        for (int i = 0; i < 4; i++) {
            int[] r = rect(i);
            if (i == dragging) {
                outline(graphics, r, SPOT);
                continue;
            }
            LeapMenu.drawTile(graphics, this.font, tile(i), r[0], r[1], s, i == hovered || i == target);
            if (i == target) {
                outline(graphics, r, DROP);
            }
        }

        int[] picker = playingRect();
        button(graphics, picker, inside(mouseX, mouseY, picker));
        String prefix = "Playing as ";
        String label = DungeonClass.label(playing);
        int textX = picker[0] + (picker[2] - this.font.width(prefix + label)) / 2;
        int textY = picker[1] + 5;
        graphics.text(this.font, prefix, textX, textY, TEXT);
        graphics.text(this.font, label, textX + this.font.width(prefix), textY, LeapMenu.colour(playing));
        graphics.text(this.font, "<", picker[0] + 6, textY, TEXT_FAINT);
        graphics.text(this.font, ">", picker[0] + picker[2] - 6 - this.font.width(">"), textY, TEXT_FAINT);
        graphics.centeredText(this.font, "Each class you play keeps its own order", this.width / 2,
                picker[1] - 12, TEXT_FAINT);

        int[] done = doneRect();
        button(graphics, done, inside(mouseX, mouseY, done));
        graphics.centeredText(this.font, "Done", done[0] + (done[2] / 2), done[1] + 4, TEXT);
        int[] reset = resetRect();
        button(graphics, reset, inside(mouseX, mouseY, reset));
        graphics.centeredText(this.font, "Reset", reset[0] + (reset[2] / 2), reset[1] + 4, TEXT);

        if (dragging >= 0) {
            LeapMenu.drawTile(graphics, this.font, tile(dragging), (float) (mouseX - grabX),
                    (float) (mouseY - grabY), s, true);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void cycle(int step) {
        int at = LeapMenu.CLASSES.indexOf(playing);
        playing = LeapMenu.CLASSES.get(Math.floorMod(at + step, LeapMenu.CLASSES.size()));
        load();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        int[] picker = playingRect();
        if ((left || right) && inside(event.x(), event.y(), picker)) {
            boolean back = right || (left && event.x() < picker[0] + 20);
            cycle(back ? -1 : 1);
            return true;
        }
        if (left) {
            if (inside(event.x(), event.y(), doneRect())) {
                onClose();
                return true;
            }
            if (inside(event.x(), event.y(), resetRect())) {
                LeapMenu.resetPreset(playing);
                load();
                return true;
            }
            int picked = under(event.x(), event.y());
            if (picked >= 0) {
                int[] r = rect(picked);
                dragging = picked;
                grabX = event.x() - r[0];
                grabY = event.y() - r[1];
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int target = under(event.x(), event.y());
            if (target >= 0) {
                String moved = slots.get(dragging);
                slots.set(dragging, slots.get(target));
                slots.set(target, moved);
                LeapMenu.savePreset(playing, slots);
            }
            dragging = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    private static void button(GuiGraphicsExtractor graphics, int[] r, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xAA3C5A70 : 0x66223140);
        outline(graphics, r, hovered ? 0xFFAFD4EC : 0xFF6A8CA8);
    }

    private static void outline(GuiGraphicsExtractor graphics, int[] r, int colour) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + 1, colour);
        graphics.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], colour);
        graphics.fill(r[0], r[1] + 1, r[0] + 1, r[1] + r[3] - 1, colour);
        graphics.fill(r[0] + r[2] - 1, r[1] + 1, r[0] + r[2], r[1] + r[3] - 1, colour);
    }

    private static boolean inside(double x, double y, int[] r) {
        return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        Mc.setScreen(new QZAScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
