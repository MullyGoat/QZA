package com.qza.cheat.click;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.cheat.movement.CopierScreen;
import com.qza.compat.Mc;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

public class AutoClickerScreen extends CopierScreen {
    private static final int HEADER_H = 52;
    private static final int COLUMNS_H = 16;
    private static final int ROW_H = 22;
    private static final int GAP = 6;
    private static final int CLICKS_W = 70;
    private static final int STEP_W = 18;
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

    public AutoClickerScreen(Screen parent) {
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
        int fixed = CLICKS_W + (STEP_W * 2) + DELETE_W + (GAP * 4);
        nameW = Math.max(60, (panelW - 24) - fixed);
        clampScroll();
    }

    private List<String> rows() {
        return AutoClicker.keys();
    }

    private int clicksX() {
        return listX + nameW + GAP;
    }

    private int slowerX() {
        return clicksX() + CLICKS_W + GAP;
    }

    private int fasterX() {
        return slowerX() + STEP_W;
    }

    private int deleteX() {
        return fasterX() + STEP_W + GAP;
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

    private int[] bindRect() {
        int w = 96;
        return new int[]{panelX + panelW - w - 12, panelY + HEADER_H - 24, w, FIELD_H};
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, int mx, int my) {
        if (confirming != null && System.currentTimeMillis() > confirmUntil) {
            confirming = null;
        }
        panel(graphics, panelX, panelY, panelW, panelH, "QZA AUTO CLICKER");

        int[] bind = bindRect();
        button(graphics, bind, AutoClicker.heldLabel() + " Held", mx, my);

        int count = rows().size();
        String status = count + " item" + (count == 1 ? "" : "s");
        if (!AutoClicker.enabled()) {
            status += " - turn on Auto Clicker in /qza under Cheats to use them";
        } else {
            status += " - hold left click with one of them out";
        }
        graphics.text(this.font, trim(status, bind[0] - listX - 8), listX, panelY + HEADER_H - 20, TEXT_FAINT);
        graphics.fill(panelX + 8, panelY + HEADER_H - 6, panelX + panelW - 8, panelY + HEADER_H - 5, DIVIDER);

        graphics.text(this.font, "ITEM", listX, listY + 2, TEXT_FAINT);
        graphics.text(this.font, "CLICKS", clicksX(), listY + 2, TEXT_FAINT);

        if (rows().isEmpty()) {
            graphics.text(this.font, "No items yet.", listX, listY + COLUMNS_H + 6, TEXT_DIM);
            graphics.text(this.font, trim("Hold the item you want it on and click Bind Held, or use "
                    + "/qza autoclicker bind. Every item is remembered on its own, so only that exact "
                    + "one clicks.", panelW - 24), listX, listY + COLUMNS_H + 18, TEXT_FAINT);
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
            ClickItem item = AutoClicker.items().get(uuid);
            if (item == null) {
                continue;
            }
            boolean live = uuid.equals(heldUuid);
            graphics.text(this.font, trim(item.name, nameW - 4), listX, y + 4, live ? GOOD : TEXT);
            graphics.text(this.font, AutoClicker.number(item.cps) + " a second", clicksX(), y + 4, TEXT_DIM);

            button(graphics, new int[]{slowerX(), y, STEP_W, FIELD_H}, "-", mx, my);
            button(graphics, new int[]{fasterX(), y, STEP_W, FIELD_H}, "+", mx, my);
            dangerButton(graphics, new int[]{deleteX(), y, DELETE_W, FIELD_H},
                    uuid.equals(confirming) ? "Sure?" : "Remove", mx, my);
        }
    }

    @Override
    protected boolean clicked(MouseButtonEvent local) {
        if (local.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (inside(local.x(), local.y(), bindRect())) {
            AutoClicker.toggleHeld();
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
            ClickItem item = AutoClicker.items().get(uuid);
            if (item == null) {
                continue;
            }
            if (inside(local.x(), local.y(), new int[]{slowerX(), y, STEP_W, FIELD_H})) {
                AutoClicker.setCps(uuid, item.cps - 1);
                return true;
            }
            if (inside(local.x(), local.y(), new int[]{fasterX(), y, STEP_W, FIELD_H})) {
                AutoClicker.setCps(uuid, item.cps + 1);
                return true;
            }
            if (inside(local.x(), local.y(), new int[]{deleteX(), y, DELETE_W, FIELD_H})) {
                if (!uuid.equals(confirming)) {
                    confirming = uuid;
                    confirmUntil = System.currentTimeMillis() + CONFIRM_MS;
                    return true;
                }
                confirming = null;
                AutoClicker.forget(uuid);
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
