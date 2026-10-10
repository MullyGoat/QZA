package com.qza.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.tweaks.CommandShortcut;
import com.qza.tweaks.CommandShortcuts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CommandShortcutScreen extends Screen {
    private static final int PANEL_BG = 0x55000000;
    private static final int BOX_BORDER = 0x40FFFFFF;
    private static final int DIVIDER = 0xFFC8D4DC;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int PINK = 0xFFFF55FF;
    private static final int PINK_FILL = 0x99FF55FF;

    private static final float TITLE_SCALE = 1.5f;
    private static final int HEADER_H = 52;
    private static final int COLUMNS_H = 16;
    private static final int ROW_H = 22;
    private static final int FIELD_H = 16;
    private static final int GAP = 6;
    private static final int SHORTCUT_W = 96;
    private static final int ARROW_W = 12;
    private static final int ON_W = 30;
    private static final int DEL_W = 16;
    private static final int MIN_COMMAND_W = 80;

    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private Component tooltip;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listW;
    private int listY;
    private int listH;
    private int commandW;

    public CommandShortcutScreen() {
        super(Component.literal("QZA"));
    }

    private static float scale() {
        double pct = ConfigManager.get().guiScale;
        return (float) Math.max(0.5, Math.min(1.5, pct / 100.0));
    }

    private float offsetX() {
        return this.width * (1f - scale()) / 2f;
    }

    private float offsetY() {
        return this.height * (1f - scale()) / 2f;
    }

    @Override
    protected void init() {
        layout();
        rows.clear();
        for (CommandShortcut shortcut : CommandShortcuts.shortcuts()) {
            EditBox typed = field(SHORTCUT_W, 32, "/d", slashed(shortcut.shortcut),
                    value -> shortcut.shortcut = CommandShortcuts.strip(value));
            EditBox command = field(commandW, 256, "/warp dungeons", slashed(shortcut.command),
                    value -> shortcut.command = CommandShortcuts.strip(value));
            rows.add(new Row(shortcut, typed, command));
        }
        clampScroll();
    }

    private static String slashed(String value) {
        String stripped = CommandShortcuts.strip(value);
        return stripped.isEmpty() ? "" : "/" + stripped;
    }

    private EditBox field(int width, int maxLength, String hint, String value, Consumer<String> onChange) {
        EditBox box = new EditBox(this.font, listX, listY, Math.max(20, width - 8), 12, Component.empty());
        box.setBordered(false);
        box.setMaxLength(maxLength);
        box.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
        box.setValue(value == null ? "" : value);
        box.moveCursorToStart(false);
        box.setResponder(onChange);
        box.visible = false;
        addRenderableWidget(box);
        return box;
    }

    private void layout() {
        panelX = 8;
        panelY = 6;
        panelW = this.width - 16;
        panelH = this.height - 12;

        listX = panelX + 12;
        listW = panelW - 24;
        listY = panelY + HEADER_H;
        listH = Math.max(ROW_H, (panelY + panelH - 10) - listY);

        int fixed = SHORTCUT_W + ARROW_W + ON_W + DEL_W + (GAP * 4);
        commandW = Math.max(MIN_COMMAND_W, listW - fixed);
    }

    private int shortcutX() {
        return listX;
    }

    private int arrowX() {
        return shortcutX() + SHORTCUT_W + GAP;
    }

    private int commandX() {
        return arrowX() + ARROW_W + GAP;
    }

    private int onX() {
        return commandX() + commandW + GAP;
    }

    private int deleteX() {
        return onX() + ON_W + GAP;
    }

    private int contentHeight() {
        return COLUMNS_H + (rows.size() * ROW_H);
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - listH)));
    }

    private int rowY(int index) {
        return listY - (int) Math.round(scroll) + COLUMNS_H + (index * ROW_H);
    }

    private boolean rowShown(int y) {
        return y >= listY + COLUMNS_H && y + FIELD_H <= listY + listH;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float s = scale();
        int mx = Math.round((mouseX - offsetX()) / s);
        int my = Math.round((mouseY - offsetY()) / s);

        positionFields();
        tooltip = null;

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(graphics, panelX, panelY, panelW, panelH, 0x33FFFFFF);

        drawHeader(graphics, mx, my);
        drawRows(graphics, mx, my);

        super.extractRenderState(graphics, mx, my, delta);

        graphics.pose().popMatrix();

        if (tooltip != null) {
            graphics.setTooltipForNextFrame(this.font, this.font.split(tooltip, 220), mouseX, mouseY);
        }
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    private static boolean inside(double x, double y, int[] r) {
        return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
    }

    private int[] addRect() {
        int w = 96;
        return new int[]{panelX + panelW - w - 12, panelY + HEADER_H - 24, w, FIELD_H};
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mx, int my) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX + (panelW / 2), panelY + 8);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, "QZA COMMAND SHORTCUTS", 0, 0, PINK);
        graphics.pose().popMatrix();

        int[] add = addRect();
        boolean hovered = inside(mx, my, add);
        button(graphics, add, hovered);
        graphics.centeredText(this.font, "Add Shortcut", add[0] + (add[2] / 2), add[1] + 4, TEXT);

        int count = rows.size();
        String status = count + " shortcut" + (count == 1 ? "" : "s");
        if (!ConfigManager.get().commandShortcutsEnabled) {
            status += " - turn on Command Shortcuts in /qza to use them";
        }
        graphics.text(this.font, this.font.plainSubstrByWidth(status, add[0] - listX - 8),
                listX, panelY + HEADER_H - 20, TEXT_FAINT);

        graphics.fill(panelX + 8, panelY + HEADER_H - 6,
                panelX + panelW - 8, panelY + HEADER_H - 5, DIVIDER);
    }

    private void positionFields() {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = rowY(i);
            boolean shown = rowShown(y);
            row.shortcut.visible = shown;
            row.command.visible = shown;
            if (!shown) {
                continue;
            }
            row.shortcut.setPosition(shortcutX() + 4, y + 4);
            row.command.setPosition(commandX() + 4, y + 4);
            row.command.setWidth(Math.max(20, commandW - 8));
        }
    }

    private void drawRows(GuiGraphicsExtractor graphics, int mx, int my) {
        graphics.text(this.font, "SHORTCUT", shortcutX(), listY + 2, TEXT_FAINT);
        graphics.text(this.font, "RUNS", commandX(), listY + 2, TEXT_FAINT);

        if (rows.isEmpty()) {
            graphics.text(this.font, "No command shortcuts yet.", listX, listY + COLUMNS_H + 6, TEXT_DIM);
            graphics.text(this.font, "Click Add Shortcut, type a short command like /d, then the command it runs "
                    + "like /warp dungeons.", listX, listY + COLUMNS_H + 18, TEXT_FAINT);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = rowY(i);
            if (!rowShown(y)) {
                continue;
            }
            CommandShortcut shortcut = row.entry;

            box(graphics, shortcutX(), y, SHORTCUT_W);
            box(graphics, commandX(), y, commandW);
            graphics.centeredText(this.font, "->", arrowX() + (ARROW_W / 2), y + 4, TEXT_FAINT);
            if (inside(mx, my, new int[]{shortcutX(), y, SHORTCUT_W, FIELD_H})) {
                tooltip = Component.literal("What you type in chat, like /d");
            } else if (inside(mx, my, new int[]{commandX(), y, commandW, FIELD_H})) {
                tooltip = Component.literal("The command it runs instead, like /warp dungeons. "
                        + "Anything you type after the shortcut is added to the end.");
            }

            int[] on = new int[]{onX(), y, ON_W, FIELD_H};
            boolean overOn = inside(mx, my, on);
            pinkToggle(graphics, on, shortcut.enabled, overOn);
            graphics.centeredText(this.font, shortcut.enabled ? "ON" : "OFF", on[0] + (ON_W / 2), y + 4,
                    shortcut.enabled ? TEXT : TEXT_DIM);

            int[] del = new int[]{deleteX(), y, DEL_W, FIELD_H};
            boolean overDel = inside(mx, my, del);
            graphics.fill(del[0], del[1], del[0] + del[2], del[1] + del[3], overDel ? 0xAA8B2E2E : 0x66401E1E);
            outline(graphics, del[0], del[1], del[2], del[3], overDel ? 0xFFFF9A9A : 0xFFA85C5C);
            graphics.centeredText(this.font, "x", del[0] + (del[2] / 2), del[1] + 4,
                    overDel ? 0xFFFFFFFF : 0xFFCCCCCC);
        }
    }

    private void box(GuiGraphicsExtractor graphics, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + FIELD_H, 0x66000000);
        outline(graphics, x, y, w, FIELD_H, BOX_BORDER);
    }

    private static void button(GuiGraphicsExtractor graphics, int[] r, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xAA3C5A70 : 0x66223140);
        outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFAFD4EC : 0xFF6A8CA8);
    }

    private static void pinkToggle(GuiGraphicsExtractor graphics, int[] r, boolean on, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                on ? PINK_FILL : (hovered ? 0x55FFFFFF : 0x66000000));
        outline(graphics, r[0], r[1], r[2], r[3], on ? PINK : (hovered ? 0xFFDDDDDD : 0x80FFFFFF));
    }

    private MouseButtonEvent toLogical(MouseButtonEvent event) {
        float s = scale();
        float ox = offsetX();
        float oy = offsetY();
        if (s == 1f && ox == 0f && oy == 0f) {
            return event;
        }
        return new MouseButtonEvent((event.x() - ox) / s, (event.y() - oy) / s, event.buttonInfo());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent local = toLogical(event);
        if (local.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(local, doubleClick);
        }

        if (inside(local.x(), local.y(), addRect())) {
            CommandShortcuts.shortcuts().add(new CommandShortcut());
            ConfigManager.save();
            scroll = Double.MAX_VALUE;
            this.rebuildWidgets();
            return true;
        }

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = rowY(i);
            if (!rowShown(y)) {
                continue;
            }
            CommandShortcut shortcut = row.entry;
            if (inside(local.x(), local.y(), new int[]{onX(), y, ON_W, FIELD_H})) {
                shortcut.enabled = !shortcut.enabled;
                ConfigManager.save();
                return true;
            }
            if (inside(local.x(), local.y(), new int[]{deleteX(), y, DEL_W, FIELD_H})) {
                CommandShortcuts.shortcuts().remove(shortcut);
                ConfigManager.save();
                this.rebuildWidgets();
                return true;
            }
        }

        return super.mouseClicked(local, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        float s = scale();
        return super.mouseDragged(toLogical(event), deltaX / s, deltaY / s);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(toLogical(event));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float s = scale();
        double mx = (mouseX - offsetX()) / s;
        double my = (mouseY - offsetY()) / s;
        if (mx >= listX - 4 && mx <= panelX + panelW && my >= listY && my <= listY + listH) {
            scroll -= verticalAmount * ROW_H;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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

    private static final class Row {
        final CommandShortcut entry;
        final EditBox shortcut;
        final EditBox command;

        Row(CommandShortcut entry, EditBox shortcut, EditBox command) {
            this.entry = entry;
            this.shortcut = shortcut;
            this.command = command;
        }
    }
}
