package com.qza.cheat;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.cheat.click.AutoClicker;
import com.qza.cheat.movement.CopierScreen;
import com.qza.compat.Mc;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

public class BloodClickScreen extends CopierScreen {
    private static final int HEADER_H = 52;
    private static final int COLUMNS_H = 16;
    private static final int ROW_H = 22;
    private static final int GAP = 6;
    private static final int DELETE_W = 44;
    private static final long CONFIRM_MS = 3000L;

    private final Screen parent;
    private double scroll;
    private String confirming;
    private long confirmUntil;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listH;
    private int nameW;

    public BloodClickScreen(Screen parent) {
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
        nameW = Math.max(60, (panelW - 24) - (DELETE_W + GAP));
        clampScroll();
    }

    private List<String> rows() {
        return BloodClick.keys();
    }

    private int deleteX() {
        return listX + nameW + GAP;
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

    private int[] addRect() {
        int w = 96;
        return new int[]{panelX + panelW - w - 12, panelY + HEADER_H - 24, w, FIELD_H};
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, int mx, int my) {
        if (confirming != null && System.currentTimeMillis() > confirmUntil) {
            confirming = null;
        }
        panel(graphics, panelX, panelY, panelW, panelH, "QZA BLOODCAMP TRIGGERBOT");

        int[] add = addRect();
        button(graphics, add, BloodClick.heldLabel() + " Held", mx, my);

        int count = rows().size();
        String status = count + " item" + (count == 1 ? "" : "s");
        if (!BloodClick.enabled()) {
            status += " - turn on BloodCamp Triggerbot in /qza under Cheats to use them";
        } else if (!CheatConfigManager.get().bloodClickWhitelist) {
            status += " - turn on Only Whitelisted Items to limit clicks to these";
        } else {
            status += " - only these click on blood mobs";
        }
        graphics.text(this.font, trim(status, add[0] - listX - 8), listX, panelY + HEADER_H - 20, TEXT_FAINT);
        graphics.fill(panelX + 8, panelY + HEADER_H - 6, panelX + panelW - 8, panelY + HEADER_H - 5, DIVIDER);

        graphics.text(this.font, "ITEM", listX, listY + 2, TEXT_FAINT);

        if (rows().isEmpty()) {
            graphics.text(this.font, "No items yet.", listX, listY + COLUMNS_H + 6, TEXT_DIM);
            graphics.text(this.font, trim("Hold the item you want it to click with and press Add Held. "
                    + "Every item is remembered on its own, so only that exact one clicks.", panelW - 24),
                    listX, listY + COLUMNS_H + 18, TEXT_FAINT);
            return;
        }

        List<String> rows = rows();
        String heldUuid = AutoClicker.uuidOf(AutoClicker.held());
        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i);
            if (!rowShown(y)) {
                continue;
            }
            String uuid = rows.get(i);
            String name = BloodClick.items().get(uuid);
            if (name == null) {
                continue;
            }
            graphics.text(this.font, trim(name, nameW - 4), listX, y + 4, uuid.equals(heldUuid) ? GOOD : TEXT);
            dangerButton(graphics, new int[]{deleteX(), y, DELETE_W, FIELD_H},
                    uuid.equals(confirming) ? "Sure?" : "Remove", mx, my);
        }
    }

    @Override
    protected boolean clicked(MouseButtonEvent local) {
        if (local.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (inside(local.x(), local.y(), addRect())) {
            BloodClick.toggleHeld();
            clampScroll();
            return true;
        }
        List<String> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i);
            if (!rowShown(y)) {
                continue;
            }
            String uuid = rows.get(i);
            if (inside(local.x(), local.y(), new int[]{deleteX(), y, DELETE_W, FIELD_H})) {
                if (!uuid.equals(confirming)) {
                    confirming = uuid;
                    confirmUntil = System.currentTimeMillis() + CONFIRM_MS;
                    return true;
                }
                confirming = null;
                BloodClick.forget(uuid);
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
