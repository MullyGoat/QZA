package com.qza.gui;

import com.qza.compat.Mc;
import com.mojang.blaze3d.platform.InputConstants;
import com.qza.config.ConfigManager;
import com.qza.util.ChatUtil;
import com.qza.waypoint.Waypoint;
import com.qza.waypoint.WaypointColour;
import com.qza.waypoint.WaypointEditor;
import com.qza.waypoint.WaypointGroup;
import com.qza.waypoint.WaypointList;
import com.qza.waypoint.WaypointSize;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class WaypointScreen extends Screen {
    private static final int PANEL_BG = 0x55000000;
    private static final int BOX_BORDER = 0x40FFFFFF;
    private static final int DIVIDER = 0xFFC8D4DC;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int PINK = 0xFFFF55FF;
    private static final int GREEN = 0xFF7BE87B;
    private static final int ICON = 0xFFE0E0E0;
    private static final int ICON_OFF = 0xFF8A8A8A;
    private static final int STRIKE = 0xFFFF6B6B;
    private static final long COPIED_MS = 2500L;

    private static final String[] EYE = {
            "...#####...",
            ".##.....##.",
            "#...###...#",
            "#...###...#",
            "#...###...#",
            ".##.....##.",
            "...#####..."};

    private static final float TITLE_SCALE = 1.5f;
    private static final int HEADER_H = 52;
    private static final int COLUMNS_H = 16;
    private static final int ROW_H = 22;
    private static final int FIELD_H = 16;
    private static final int SWATCH_W = 46;
    private static final int DEL_W = 16;
    private static final int GAP = 4;
    private static final int COORD_W = 96;
    private static final int SIZE_W = 54;

    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private Component tooltip;
    private String copied;
    private Waypoint copiedFrom;
    private long copiedAt;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listW;
    private int listY;
    private int listH;
    private int nameW;

    public WaypointScreen() {
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

        for (WaypointGroup group : WaypointList.groups()) {
            EditBox name = field(nameW - 10, 32, group.label());
            name.setHint(Component.literal("Name").withStyle(ChatFormatting.DARK_GRAY));
            name.setValue(group.name());

            EditBox coords = field(COORD_W - 10, 20, group.coordsText());
            coords.setValue(group.coordsText());

            EditBox size = null;
            if (group.single()) {
                size = field(SIZE_W - 10, 11, group.sizeText());
                size.setValue(group.sizeText());
            }

            rows.add(new Row(group, name, coords, size));
        }
    }

    private EditBox field(int width, int maxLength, String hint) {
        EditBox box = new EditBox(this.font, listX + 5, listY, Math.max(20, width), 12,
                Component.empty());
        box.setBordered(false);
        box.setMaxLength(maxLength);
        box.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
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

        int fixed = COORD_W + SIZE_W + SWATCH_W + (DEL_W * 3) + (GAP * 2) + 42;
        nameW = Math.max(70, listW - fixed);
    }

    private int contentHeight() {
        return COLUMNS_H + (rows.size() * ROW_H);
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - listH)));
    }

    private int nameX() {
        return listX + 5;
    }

    private int coordX() {
        return listX + nameW + 10;
    }

    private int sizeX() {
        return coordX() + COORD_W + 8;
    }

    private int swatchX() {
        return sizeX() + SIZE_W + 8;
    }

    private int eyeX() {
        return swatchX() + SWATCH_W + 8;
    }

    private int copyX() {
        return eyeX() + DEL_W + GAP;
    }

    private int deleteX() {
        return copyX() + DEL_W + GAP;
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
            graphics.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
        }
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mx, int my) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX + (panelW / 2), panelY + 8);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, "QZA WAYPOINTS", 0, 0, PINK);
        graphics.pose().popMatrix();

        int[] add = manualRect();
        boolean hovered = inside(mx, my, add);
        boolean on = WaypointEditor.active();
        graphics.fill(add[0], add[1], add[0] + add[2], add[1] + add[3],
                on ? 0xAA2E6B34 : (hovered ? 0xAA3C5A70 : 0x66223140));
        outline(graphics, add[0], add[1], add[2], add[3],
                on ? 0xFF7BE87B : (hovered ? 0xFFAFD4EC : 0xFF6A8CA8));
        graphics.centeredText(this.font, on ? "Editing - click to stop" : "Manual Add",
                add[0] + (add[2] / 2), add[1] + 4, on ? 0xFF7BE87B : TEXT);

        if (copiedNow()) {
            int room = manualRect()[0] - listX - 8;
            graphics.text(this.font, this.font.plainSubstrByWidth("Copied " + copied, room),
                    listX, panelY + HEADER_H - 20, GREEN);
        } else {
            graphics.text(this.font, WaypointList.size() + "/" + WaypointList.MAX,
                    listX, panelY + HEADER_H - 20, TEXT_FAINT);
        }

        graphics.fill(panelX + 8, panelY + HEADER_H - 6,
                panelX + panelW - 8, panelY + HEADER_H - 5, DIVIDER);
    }

    private int[] manualRect() {
        int w = 104;
        return new int[]{panelX + panelW - w - 12, panelY + HEADER_H - 24, w, FIELD_H};
    }

    private static boolean inside(double x, double y, int[] r) {
        return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
    }

    private void positionFields() {
        int top = listY - (int) Math.round(scroll);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = top + COLUMNS_H + (i * ROW_H);
            boolean shown = y >= listY && y + FIELD_H <= listY + listH;

            row.name.visible = shown;
            row.coords.visible = shown;
            if (row.size != null) {
                row.size.visible = shown;
            }
            if (!shown) {
                continue;
            }
            row.name.setPosition(nameX() + 3, y + 3);
            row.name.setWidth(Math.max(20, nameW - 10));
            row.coords.setPosition(coordX() + 3, y + 3);
            if (row.size != null) {
                row.size.setPosition(sizeX() + 3, y + 3);
            }
        }
    }

    private void drawRows(GuiGraphicsExtractor graphics, int mx, int my) {
        graphics.text(this.font, "NAME", nameX(), listY + 2, TEXT_FAINT);
        graphics.text(this.font, "X Y Z", coordX(), listY + 2, TEXT_FAINT);
        graphics.text(this.font, "SIZE", sizeX(), listY + 2, TEXT_FAINT);
        graphics.text(this.font, "COLOUR", swatchX(), listY + 2, TEXT_FAINT);

        if (rows.isEmpty()) {
            graphics.text(this.font, "No waypoints yet.", listX, listY + COLUMNS_H + 6, TEXT_DIM);
            graphics.text(this.font, "Use Manual Add, or /qza waypoint add X Y Z <colour> "
                    + "<size> <name>", listX, listY + COLUMNS_H + 18, TEXT_FAINT);
            return;
        }

        int top = listY - (int) Math.round(scroll);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = top + COLUMNS_H + (i * ROW_H);
            if (y < listY || y + FIELD_H > listY + listH) {
                continue;
            }

            WaypointGroup group = row.group;
            box(graphics, nameX(), y, nameW - 6);
            box(graphics, coordX(), y, COORD_W - 6);
            box(graphics, sizeX(), y, SIZE_W - 6);
            if (row.size == null) {
                String text = group.sizeText();
                if (this.font.width(text) > SIZE_W - 12) {
                    text = String.valueOf(group.members().size());
                }
                graphics.text(this.font, text, sizeX() + 3, y + 4, TEXT_DIM);
            }

            int[] swatch = new int[]{swatchX(), y, SWATCH_W, FIELD_H};
            boolean overSwatch = inside(mx, my, swatch);
            graphics.fill(swatch[0], swatch[1], swatch[0] + swatch[2], swatch[1] + swatch[3],
                    group.argb());
            outline(graphics, swatch[0], swatch[1], swatch[2], swatch[3],
                    overSwatch ? 0xFFFFFFFF : 0x80FFFFFF);
            graphics.centeredText(this.font, group.colour(),
                    swatch[0] + (swatch[2] / 2), swatch[1] + 4, 0xFF101010);

            if (!group.visible()) {
                graphics.fill(nameX(), y, swatchX() + SWATCH_W, y + FIELD_H, 0x66000000);
            }

            int[] eye = new int[]{eyeX(), y, DEL_W, FIELD_H};
            boolean overEye = inside(mx, my, eye);
            button(graphics, eye, overEye, false);
            drawEye(graphics, eye[0] + 2, eye[1] + 4, group.visible());
            if (overEye) {
                tooltip = Component.literal(group.visible() ? "Hide in game" : "Show in game");
            }

            int[] copy = new int[]{copyX(), y, DEL_W, FIELD_H};
            boolean overCopy = inside(mx, my, copy);
            boolean justCopied = copiedNow() && group.members().contains(copiedFrom);
            button(graphics, copy, overCopy, justCopied);
            drawCopy(graphics, copy[0] + 3, copy[1] + 2, justCopied ? GREEN : ICON);
            if (overCopy) {
                tooltip = Component.literal("Copy the command that makes this waypoint");
            }

            int[] del = new int[]{deleteX(), y, DEL_W, FIELD_H};
            boolean overDel = inside(mx, my, del);
            graphics.fill(del[0], del[1], del[0] + del[2], del[1] + del[3],
                    overDel ? 0xAA8B2E2E : 0x66401E1E);
            outline(graphics, del[0], del[1], del[2], del[3],
                    overDel ? 0xFFFF9A9A : 0xFFA85C5C);
            graphics.centeredText(this.font, "x", del[0] + (del[2] / 2), del[1] + 4,
                    overDel ? 0xFFFFFFFF : 0xFFCCCCCC);
        }
    }

    private static void button(GuiGraphicsExtractor graphics, int[] r, boolean hovered, boolean done) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xAA3C5A70 : 0x66223140);
        outline(graphics, r[0], r[1], r[2], r[3],
                done ? GREEN : (hovered ? 0xFFAFD4EC : 0xFF6A8CA8));
    }

    private static void drawEye(GuiGraphicsExtractor graphics, int x, int y, boolean visible) {
        int colour = visible ? ICON : ICON_OFF;
        for (int row = 0; row < EYE.length; row++) {
            for (int col = 0; col < EYE[row].length(); col++) {
                if (EYE[row].charAt(col) == '#') {
                    graphics.fill(x + col, y + row, x + col + 1, y + row + 1, colour);
                }
            }
        }
        if (!visible) {
            for (int i = 0; i < 11; i++) {
                graphics.fill(x + i, y - 2 + i, x + i + 1, y - 1 + i, STRIKE);
            }
        }
    }

    private static void drawCopy(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        graphics.fill(x, y, x + 7, y + 1, colour);
        graphics.fill(x, y + 1, x + 1, y + 8, colour);
        graphics.fill(x + 1, y + 7, x + 3, y + 8, colour);
        graphics.fill(x + 6, y + 1, x + 7, y + 3, colour);
        outline(graphics, x + 3, y + 3, 7, 8, colour);
    }

    private boolean copiedNow() {
        return copied != null && System.currentTimeMillis() - copiedAt < COPIED_MS;
    }

    private void box(GuiGraphicsExtractor graphics, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + FIELD_H, 0x66000000);
        outline(graphics, x, y, w, FIELD_H, BOX_BORDER);
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

        if (local.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(local.x(), local.y(), manualRect())) {
            apply();
            WaypointEditor.toggle();
            if (WaypointEditor.active()) {
                Mc.setScreen(null);
            }
            return true;
        }

        if (local.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int top = listY - (int) Math.round(scroll);
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int y = top + COLUMNS_H + (i * ROW_H);
                if (y < listY || y + FIELD_H > listY + listH) {
                    continue;
                }

                if (inside(local.x(), local.y(), new int[]{swatchX(), y, SWATCH_W, FIELD_H})) {
                    row.group.setColour(WaypointColour.next(row.group.colour()));
                    WaypointList.save();
                    return true;
                }
                if (inside(local.x(), local.y(), new int[]{eyeX(), y, DEL_W, FIELD_H})) {
                    row.group.setVisible(!row.group.visible());
                    WaypointList.save();
                    return true;
                }
                if (inside(local.x(), local.y(), new int[]{copyX(), y, DEL_W, FIELD_H})) {
                    apply();
                    copy(row.group);
                    return true;
                }
                if (inside(local.x(), local.y(), new int[]{deleteX(), y, DEL_W, FIELD_H})) {
                    apply();
                    for (Waypoint waypoint : row.group.members()) {
                        WaypointList.remove(waypoint);
                    }
                    rebuild();
                    return true;
                }
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
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
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

    private void copy(WaypointGroup group) {
        String command = group.command();
        if (command == null) {
            ChatUtil.error("That waypoint is too big to fit in one command.");
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(command);
        copied = command;
        copiedFrom = group.first();
        copiedAt = System.currentTimeMillis();
    }

    private void rebuild() {
        this.rebuildWidgets();
    }

    private void apply() {
        boolean changed = false;

        for (Row row : rows) {
            WaypointGroup group = row.group;

            String name = row.name.getValue().trim();
            if (!name.equals(group.name())) {
                group.setName(name);
                changed = true;
            }

            int[] coords = coords(row.coords.getValue());
            int[] at = group.coords();
            if (coords != null && (coords[0] != at[0] || coords[1] != at[1] || coords[2] != at[2])) {
                group.moveTo(coords[0], coords[1], coords[2]);
                changed = true;
            }

            if (row.size == null) {
                continue;
            }
            Waypoint waypoint = group.first();
            WaypointSize.Size size = WaypointSize.parse(row.size.getValue());
            if (size != null && (size.width() != waypoint.width
                    || size.height() != waypoint.height || size.depth() != waypoint.depth)) {
                waypoint.width = size.width();
                waypoint.height = size.height();
                waypoint.depth = size.depth();
                changed = true;
            }
        }

        if (changed) {
            WaypointList.save();
        }
    }

    private static int[] coords(String text) {
        String[] parts = text.trim().split("[ ,]+");
        if (parts.length != 3) {
            return null;
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }

    @Override
    public void onClose() {
        apply();
        ChatUtil.info(WaypointList.size() + " waypoint"
                + (WaypointList.size() == 1 ? "" : "s") + " saved.");
        Mc.setScreen(new QZAScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class Row {
        final WaypointGroup group;
        final EditBox name;
        final EditBox coords;
        final EditBox size;

        Row(WaypointGroup group, EditBox name, EditBox coords, EditBox size) {
            this.group = group;
            this.name = name;
            this.coords = coords;
            this.size = size;
        }
    }
}
