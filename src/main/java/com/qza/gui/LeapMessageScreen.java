package com.qza.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.dungeon.LeapMenu;
import com.qza.stats.DungeonClass;
import com.qza.util.ChatUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class LeapMessageScreen extends Screen {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_FAINT = 0xFFAAAAAA;
    private static final int PINK = 0xFFFF55FF;
    private static final int PARTY = 0xFF5555FF;
    private static final float TITLE_SCALE = 1.5f;
    private static final int BUTTON_W = 64;
    private static final int BUTTON_H = 16;
    private static final int FIELD_H = 18;
    private static final int MAX_W = 300;

    private EditBox field;

    public LeapMessageScreen() {
        super(Component.literal("QZA"));
    }

    @Override
    protected void init() {
        int w = Math.min(MAX_W, this.width - 40);
        field = new EditBox(this.font, (this.width - w) / 2, this.height / 2 - 24, w, FIELD_H, Component.empty());
        field.setMaxLength(100);
        field.setHint(Component.literal(LeapMenu.ANNOUNCE_MESSAGE).withStyle(ChatFormatting.DARK_GRAY));
        field.setValue(ConfigManager.get().leapMenuAnnounceMessage == null ? ""
                : ConfigManager.get().leapMenuAnnounceMessage);
        field.setResponder(value -> ConfigManager.get().leapMenuAnnounceMessage = value);
        addRenderableWidget(field);
        setInitialFocus(field);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(graphics);
    }

    private int[] doneRect() {
        return new int[]{this.width - BUTTON_W - 8, this.height - BUTTON_H - 11, BUTTON_W, BUTTON_H};
    }

    private int[] resetRect() {
        return new int[]{this.width - (BUTTON_W * 2) - 14, this.height - BUTTON_H - 11, BUTTON_W, BUTTON_H};
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(this.width / 2f, 8);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, "LEAP MESSAGE", 0, 0, PINK);
        graphics.pose().popMatrix();
        boolean byClass = LeapMenu.ANNOUNCE_CLASS.equals(ConfigManager.get().leapMenuAnnounceAs);
        boolean party = !LeapMenu.ANNOUNCE_SYSTEM.equals(ConfigManager.get().leapMenuAnnounceIn);
        graphics.centeredText(this.font, (party ? "Said in party chat" : "Shown only to you") + " after you leap. "
                + "{name} becomes the player's " + (byClass ? "class" : "name"), this.width / 2, 24, TEXT_FAINT);

        int previewY = field.getY() + FIELD_H + 12;
        String said = LeapMenu.announcement("Player", DungeonClass.BERSERK);
        if (party) {
            String prefix = "Party > You: ";
            int x = (this.width - this.font.width(prefix + said)) / 2;
            graphics.text(this.font, prefix, x, previewY, PARTY);
            graphics.text(this.font, said, x + this.font.width(prefix), previewY, TEXT);
        } else {
            graphics.centeredText(this.font, ChatUtil.prefix().append(Component.literal(said)
                    .withStyle(ChatFormatting.WHITE)), this.width / 2, previewY, TEXT);
        }
        graphics.centeredText(this.font, "Announce As picks name or class, Announce In picks party or system chat",
                this.width / 2, previewY + 16, TEXT_FAINT);

        int[] done = doneRect();
        button(graphics, done, inside(mouseX, mouseY, done));
        graphics.centeredText(this.font, "Done", done[0] + (done[2] / 2), done[1] + 4, TEXT);
        int[] reset = resetRect();
        button(graphics, reset, inside(mouseX, mouseY, reset));
        graphics.centeredText(this.font, "Reset", reset[0] + (reset[2] / 2), reset[1] + 4, TEXT);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (inside(event.x(), event.y(), doneRect())) {
                onClose();
                return true;
            }
            if (inside(event.x(), event.y(), resetRect())) {
                field.setValue(LeapMenu.ANNOUNCE_MESSAGE);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static void button(GuiGraphicsExtractor graphics, int[] r, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xAA3C5A70 : 0x66223140);
        int colour = hovered ? 0xFFAFD4EC : 0xFF6A8CA8;
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
