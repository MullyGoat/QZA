package com.qza.cheat.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.compat.Mc;
import com.qza.util.ChatUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class RecordingNameScreen extends CopierScreen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 132;
    private static final int BUTTON_W = 110;
    private static final int BUTTON_H = 18;

    private final Recording recording;
    private final String fallback;
    private EditBox box;
    private boolean done;

    private int panelX;
    private int panelY;

    public RecordingNameScreen(Recording recording) {
        this.recording = recording;
        this.fallback = Recordings.nextName();
    }

    @Override
    protected void init() {
        int w = Math.min(PANEL_W, Math.round(this.width / scale()) - 20);
        panelX = (this.width - w) / 2;
        panelY = (this.height - PANEL_H) / 2;

        String typed = box == null ? fallback : box.getValue();
        box = new EditBox(this.font, panelX + 20, panelY + 66, w - 40, 12, Component.empty());
        box.setBordered(false);
        box.setMaxLength(Recordings.MAX_NAME);
        box.setHint(Component.literal(fallback).withStyle(ChatFormatting.DARK_GRAY));
        box.setValue(typed);
        box.moveCursorToEnd(false);
        box.setHighlightPos(0);
        addRenderableWidget(box);
        setInitialFocus(box);
    }

    private int panelW() {
        return Math.min(PANEL_W, Math.round(this.width / scale()) - 20);
    }

    private String chosenName() {
        String name = Recordings.tidy(box == null ? "" : box.getValue());
        return name.isEmpty() ? fallback : name;
    }

    private int[] saveRect() {
        int w = panelW();
        return new int[]{panelX + (w / 2) - BUTTON_W - 4, panelY + PANEL_H - BUTTON_H - 12, BUTTON_W, BUTTON_H};
    }

    private int[] discardRect() {
        int w = panelW();
        return new int[]{panelX + (w / 2) + 4, panelY + PANEL_H - BUTTON_H - 12, BUTTON_W, BUTTON_H};
    }

    @Override
    void draw(GuiGraphicsExtractor graphics, int mx, int my) {
        int w = panelW();
        panel(graphics, panelX, panelY, w, PANEL_H, "QZA MOVEMENT COPIER");

        int cx = panelX + (w / 2);
        graphics.centeredText(this.font, "What do you want to name this recording?", cx, panelY + 36, TEXT);
        graphics.centeredText(this.font, seconds(recording.seconds()) + " recorded", cx, panelY + 48, TEXT_FAINT);

        box(graphics, panelX + 16, panelY + 62, w - 32, true);

        Recording existing = Recordings.find(chosenName());
        boolean blank = Recordings.tidy(box.getValue()).isEmpty();
        String note;
        if (existing != null) {
            note = "Replaces the recording already called " + existing.name;
        } else if (blank) {
            note = "Leave it blank to call it " + fallback;
        } else {
            note = "Enter or esc saves it";
        }
        graphics.centeredText(this.font, trim(note, w - 24), cx, panelY + 84,
                existing != null ? 0xFFFFAA55 : TEXT_FAINT);

        button(graphics, saveRect(), existing != null ? "Replace" : "Save", mx, my);
        dangerButton(graphics, discardRect(), "Discard", mx, my);
    }

    @Override
    boolean clicked(MouseButtonEvent local) {
        if (local.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (inside(local.x(), local.y(), saveRect())) {
            save();
            Mc.setScreen(null);
            return true;
        }
        if (inside(local.x(), local.y(), discardRect())) {
            done = true;
            ChatUtil.info("Discarded the recording.");
            Mc.setScreen(null);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            save();
            Mc.setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    private void save() {
        if (done) {
            return;
        }
        done = true;
        recording.name = chosenName();
        Recordings.add(recording);
        ChatUtil.success("Saved " + recording.name + " (" + seconds(recording.seconds()) + "). Look at a block and use "
                + "/qza recorder place " + recording.name + " to put it on that block.");
    }

    @Override
    public void removed() {
        save();
        super.removed();
    }
}
