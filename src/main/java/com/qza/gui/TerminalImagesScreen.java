package com.qza.gui;

import com.qza.config.ConfigManager;
import com.qza.terminal.TerminalImages;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class TerminalImagesScreen extends Screen {
    private static final int PANEL_BG = 0x55000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int PINK = 0xFFFF55FF;
    private static final int DIVIDER = 0xFFC8D4DC;

    private static final float TITLE_SCALE = 1.5f;
    private static final int HEADER_H = 52;
    private static final int ROW_H = 22;
    private static final int BUTTON_H = 16;
    private static final int NAME_W = 170;

    private final List<Row> rows = new ArrayList<>();
    private double scroll;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;

    public TerminalImagesScreen() {
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
        panelX = 8;
        panelY = 6;
        panelW = this.width - 16;
        panelH = this.height - 12;

        listX = panelX + 12;
        listY = panelY + HEADER_H;
        listW = panelW - 24;
        listH = Math.max(ROW_H, (panelY + panelH - 12) - listY - BUTTON_H - 8);

        rows.clear();
        for (String file : TerminalImages.reload()) {
            EditBox box = new EditBox(this.font, listX, listY, NAME_W - 10, 12,
                    Component.empty());
            box.setBordered(false);
            box.setMaxLength(28);
            box.setHint(Component.literal(TerminalImages.stripExtension(file))
                    .withStyle(ChatFormatting.DARK_GRAY));
            box.setValue(TerminalImages.aliasOf(file));
            box.setResponder(value -> {
                TerminalImages.rename(file, value);
                ConfigManager.save();
            });
            box.visible = false;
            addRenderableWidget(box);
            rows.add(new Row(file, box));
        }
    }

    private int[] rowRect(int index) {
        return new int[]{listX, listY + (index * ROW_H) - (int) Math.round(scroll),
                listW, ROW_H - 2};
    }

    private int[] nameRect(int index) {
        int[] r = rowRect(index);
        return new int[]{r[0] + r[2] - NAME_W, r[1] + 3, NAME_W, 16};
    }

    private int[] folderRect() {
        return new int[]{listX, listY + listH + 6, 96, BUTTON_H};
    }

    private int[] doneRect() {
        return new int[]{listX + 102, listY + listH + 6, 76, BUTTON_H};
    }

    private double maxScroll() {
        return Math.max(0, (rows.size() * ROW_H) - listH);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float s = scale();
        int mx = Math.round((mouseX - offsetX()) / s);
        int my = Math.round((mouseY - offsetY()) / s);

        for (int i = 0; i < rows.size(); i++) {
            int[] field = nameRect(i);
            boolean visible = field[1] >= listY - 2 && field[1] + field[3] <= listY + listH + 2;
            rows.get(i).box.visible = visible;
            if (visible) {
                rows.get(i).box.setX(field[0] + 5);
                rows.get(i).box.setY(field[1] + 4);
            }
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(graphics, panelX, panelY, panelW, panelH, 0x33FFFFFF);

        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX + (panelW / 2f), panelY + 10);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, "TERMINAL IMAGES", 0, 0, PINK);
        graphics.pose().popMatrix();

        graphics.fill(panelX + 8, panelY + HEADER_H - 10,
                panelX + panelW - 8, panelY + HEADER_H - 9, DIVIDER);

        if (rows.isEmpty()) {
            graphics.centeredText(this.font, "No images yet",
                    panelX + (panelW / 2), listY + 20, TEXT_DIM);
            graphics.centeredText(this.font,
                    "Open the folder, drop a .png in, then come back",
                    panelX + (panelW / 2), listY + 34, TEXT_FAINT);
        } else {
            graphics.enableScissor(listX - 2, listY, listX + listW + 2, listY + listH);
            for (int i = 0; i < rows.size(); i++) {
                drawRow(graphics, i, mx, my);
            }
            graphics.disableScissor();
        }

        int[] folder = folderRect();
        button(graphics, folder, "Open Folder", inside(mx, my, folder));
        int[] done = doneRect();
        button(graphics, done, "Done", inside(mx, my, done));

        super.extractRenderState(graphics, mx, my, delta);
        graphics.pose().popMatrix();
    }

    private void drawRow(GuiGraphicsExtractor graphics, int index, int mouseX, int mouseY) {
        Row row = rows.get(index);
        int[] r = rowRect(index);
        if (r[1] + r[3] < listY || r[1] > listY + listH) {
            return;
        }

        if (inside(mouseX, mouseY, r)) {
            graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x18FFFFFF);
        }
        graphics.text(this.font, row.file, r[0] + 4, r[1] + 7, TEXT_DIM);

        int[] field = nameRect(index);
        graphics.fill(field[0], field[1], field[0] + field[2], field[1] + field[3],
                0x66000000);
        outline(graphics, field[0], field[1], field[2], field[3], 0x40FFFFFF);
    }

    private void button(GuiGraphicsExtractor graphics, int[] r, String label, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xAA3C5A70 : 0x99223140);
        outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFAFD4EC : 0xFF6A8CA8);
        graphics.centeredText(this.font, label, r[0] + (r[2] / 2), r[1] + 4, TEXT);
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    private static boolean inside(double x, double y, int[] rect) {
        return x >= rect[0] && x <= rect[0] + rect[2]
                && y >= rect[1] && y <= rect[1] + rect[3];
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

        if (inside(local.x(), local.y(), folderRect())) {
            TerminalImages.openFolder();
            return true;
        }
        if (inside(local.x(), local.y(), doneRect())) {
            onClose();
            return true;
        }
        return super.mouseClicked(local, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        float s = scale();
        double my = (mouseY - offsetY()) / s;
        if (my >= listY && my <= listY + listH) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (verticalAmount * ROW_H)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        TerminalImages.forget();
        this.minecraft.setScreen(new TerminalCustomScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Row(String file, EditBox box) {
    }
}
