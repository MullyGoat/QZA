package com.qza.cheat.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.compat.Mc;
import com.qza.util.ChatUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordingsScreen extends CopierScreen {
    private static final int HEADER_H = 52;
    private static final int COLUMNS_H = 16;
    private static final int ROW_H = 22;
    private static final int GAP = 6;
    private static final int SPEED_W = 44;
    private static final int LENGTH_W = 70;
    private static final int BLOCK_W = 110;
    private static final int EDIT_W = 36;
    private static final int PLAY_W = 36;
    private static final int DELETE_W = 44;
    private static final long CONFIRM_MS = 3000L;

    private final Screen parent;
    private final Map<Recording, Integer> speeds = new HashMap<>();
    private double scroll;
    private Recording confirming;
    private long confirmUntil;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listH;
    private int nameW;

    public RecordingsScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = 8;
        panelY = 6;
        panelW = this.width - 16;
        panelH = this.height - 12;
        listX = panelX + 12;
        listY = panelY + HEADER_H;
        listH = Math.max(ROW_H, (panelY + panelH - 10) - listY);
        int fixed = SPEED_W + LENGTH_W + BLOCK_W + EDIT_W + PLAY_W + DELETE_W + (GAP * 6);
        nameW = Math.max(60, (panelW - 24) - fixed);
        speeds.clear();
        clampScroll();
    }

    private List<Recording> rows() {
        return Recordings.all();
    }

    private int speedX() {
        return listX + nameW + GAP;
    }

    private int lengthX() {
        return speedX() + SPEED_W + GAP;
    }

    private int speed(Recording recording) {
        return speeds.computeIfAbsent(recording, Recording::shownSpeed);
    }

    private int blockX() {
        return lengthX() + LENGTH_W + GAP;
    }

    private int editX() {
        return blockX() + BLOCK_W + GAP;
    }

    private int playX() {
        return editX() + EDIT_W + GAP;
    }

    private int deleteX() {
        return playX() + PLAY_W + GAP;
    }

    private int rowY(int index) {
        return listY - (int) Math.round(scroll) + COLUMNS_H + (index * ROW_H);
    }

    private boolean rowShown(int y) {
        return y >= listY + COLUMNS_H && y + FIELD_H <= listY + listH;
    }

    private void clampScroll() {
        int content = COLUMNS_H + (rows().size() * ROW_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, content - listH)));
    }

    private int[] recordRect() {
        int w = 96;
        return new int[]{panelX + panelW - w - 12, panelY + HEADER_H - 24, w, FIELD_H};
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, int mx, int my) {
        if (confirming != null && System.currentTimeMillis() > confirmUntil) {
            confirming = null;
        }
        panel(graphics, panelX, panelY, panelW, panelH, "QZA RECORDINGS");

        int[] record = recordRect();
        button(graphics, record, "Record New", mx, my);

        int count = rows().size();
        String status = count + " recording" + (count == 1 ? "" : "s");
        if (!MovementCopier.enabled()) {
            status += " - turn on Movement Copier in /qza under Cheats to use them";
        } else if (MovementCopier.hasKey()) {
            status += " - press " + MovementCopier.keyLabel() + " in game to record";
        }
        graphics.text(this.font, trim(status, record[0] - listX - 8), listX, panelY + HEADER_H - 20, TEXT_FAINT);
        graphics.fill(panelX + 8, panelY + HEADER_H - 6, panelX + panelW - 8, panelY + HEADER_H - 5, DIVIDER);

        graphics.text(this.font, "NAME", listX, listY + 2, TEXT_FAINT);
        graphics.text(this.font, "SPEED", speedX(), listY + 2, TEXT_FAINT);
        graphics.text(this.font, "PLAYS FOR", lengthX(), listY + 2, TEXT_FAINT);
        graphics.text(this.font, "BLOCK", blockX(), listY + 2, TEXT_FAINT);

        if (rows().isEmpty()) {
            graphics.text(this.font, "No recordings yet.", listX, listY + COLUMNS_H + 6, TEXT_DIM);
            graphics.text(this.font, trim("Click Record New or use /qza movement copy start, move around, then stop "
                    + "and name it.", panelW - 24), listX, listY + COLUMNS_H + 18, TEXT_FAINT);
            return;
        }

        List<Recording> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i);
            if (!rowShown(y)) {
                continue;
            }
            Recording recording = rows.get(i);
            boolean live = MovementCopier.current() == recording;
            graphics.text(this.font, trim(recording.name, nameW - 4), listX, y + 4, live ? 0xFF55FF55 : TEXT);
            int speed = speed(recording);
            graphics.text(this.font, speed > 0 ? String.valueOf(speed) : "?", speedX(), y + 4,
                    speed > 0 ? TEXT_DIM : TEXT_FAINT);
            graphics.text(this.font, seconds(recording.playSeconds()), lengthX(), y + 4, TEXT_DIM);
            graphics.text(this.font, recording.placed() ? recording.blockText() : "Not placed", blockX(), y + 4,
                    recording.placed() ? TEXT_DIM : TEXT_FAINT);

            button(graphics, new int[]{editX(), y, EDIT_W, FIELD_H}, "Edit", mx, my);
            button(graphics, new int[]{playX(), y, PLAY_W, FIELD_H}, live ? "Stop" : "Play", mx, my);
            dangerButton(graphics, new int[]{deleteX(), y, DELETE_W, FIELD_H},
                    confirming == recording ? "Sure?" : "Delete", mx, my);
        }
    }

    @Override
    protected boolean clicked(MouseButtonEvent local) {
        if (local.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (inside(local.x(), local.y(), recordRect())) {
            Mc.setScreen(null);
            MovementCopier.startCommand();
            return true;
        }
        List<Recording> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i);
            if (!rowShown(y)) {
                continue;
            }
            Recording recording = rows.get(i);
            if (inside(local.x(), local.y(), new int[]{editX(), y, EDIT_W, FIELD_H})
                    || inside(local.x(), local.y(), new int[]{listX, y, nameW, FIELD_H})) {
                Mc.setScreen(new RecordingEditScreen(recording, this));
                return true;
            }
            if (inside(local.x(), local.y(), new int[]{playX(), y, PLAY_W, FIELD_H})) {
                if (MovementCopier.current() == recording) {
                    MovementCopier.stopPlayback("Stopped " + recording.name + ".");
                    return true;
                }
                Mc.setScreen(null);
                MovementCopier.playSoon(recording);
                return true;
            }
            if (inside(local.x(), local.y(), new int[]{deleteX(), y, DELETE_W, FIELD_H})) {
                if (confirming != recording) {
                    confirming = recording;
                    confirmUntil = System.currentTimeMillis() + CONFIRM_MS;
                    return true;
                }
                confirming = null;
                MovementCopier.forget(recording);
                Recordings.remove(recording);
                ChatUtil.info("Deleted " + recording.name + ".");
                clampScroll();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int my = logicalY(mouseY);
        if (my >= listY && my <= listY + listH) {
            scroll -= verticalAmount * ROW_H;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        Mc.setScreen(parent);
    }
}
