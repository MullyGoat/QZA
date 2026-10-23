package com.qza.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.pack.PackCombiner;
import com.qza.pack.PackCombiner.Part;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.List;

public class PackCombinerScreen extends Screen {
    private static final int PANEL_BG = 0x99000000;
    private static final int CARD_BG = 0x33000000;
    private static final int BOX_BORDER = 0x40FFFFFF;
    private static final int DIVIDER = 0xFFC8D4DC;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int PINK = 0xFFFF55FF;
    private static final int PINK_FILL = 0x44FF55FF;
    private static final int MISSING = 0xFFFF5555;

    private static final float TITLE_SCALE = 1.5f;
    private static final float COLUMN_SCALE = 1.25f;
    private static final int HEADER_H = 52;
    private static final int COLUMN_HEAD = 30;
    private static final int FOOTER_H = 30;
    private static final int ROW_H = 26;
    private static final int GAP = 12;
    private static final int BUTTON_H = 16;
    private static final long RESCAN_MS = 1000L;

    private List<PackCombiner.Choice> choices = List.of();
    private final int[] scroll = new int[Part.values().length];
    private FileTime folderTime;
    private long checkedAt;
    private List<FormattedCharSequence> tooltip;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int colW;
    private int listY;
    private int listH;

    public PackCombinerScreen() {
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
        int columns = Part.values().length;
        colW = (panelW - 24 - GAP * (columns - 1)) / columns;
        listY = panelY + HEADER_H + COLUMN_HEAD;
        listH = Math.max(ROW_H, panelY + panelH - FOOTER_H - listY - 6);
        rescan();
    }

    private void rescan() {
        choices = PackCombiner.choices();
        folderTime = folderTime();
        checkedAt = System.currentTimeMillis();
        clampScroll();
    }

    private static FileTime folderTime() {
        try {
            return Files.getLastModifiedTime(PackCombiner.packsDir());
        } catch (IOException e) {
            return null;
        }
    }

    private int columnX(Part part) {
        return panelX + 12 + part.ordinal() * (colW + GAP);
    }

    private int rows() {
        return choices.size() + 1;
    }

    private int rowsShown() {
        return Math.max(1, listH / ROW_H);
    }

    private void clampScroll() {
        int max = Math.max(0, rows() - rowsShown());
        for (int i = 0; i < scroll.length; i++) {
            scroll[i] = Math.max(0, Math.min(scroll[i], max));
        }
    }

    private int[] rowRect(Part part, int shownIndex) {
        return new int[]{columnX(part) + 4, listY + 4 + shownIndex * ROW_H, colW - 8 - 4, ROW_H - 3};
    }

    private int[] combineRect() {
        int w = 76;
        return new int[]{panelX + panelW - w - 12, panelY + panelH - FOOTER_H + 6, w, BUTTON_H};
    }

    private int[] folderRect() {
        int w = 88;
        return new int[]{panelX + 12, panelY + panelH - FOOTER_H + 6, w, BUTTON_H};
    }

    private int[] doneRect() {
        int w = 64;
        return new int[]{panelX + panelW - w - 12, panelY + HEADER_H - 24, w, BUTTON_H};
    }

    private String fileAt(int row) {
        return row == 0 ? PackCombiner.NONE : choices.get(row - 1).file();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        if (now - checkedAt > RESCAN_MS) {
            checkedAt = now;
            FileTime time = folderTime();
            if (time != null && !time.equals(folderTime)) {
                rescan();
            }
        }
        float s = scale();
        int mx = Math.round((mouseX - offsetX()) / s);
        int my = Math.round((mouseY - offsetY()) / s);
        tooltip = null;

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(graphics, panelX, panelY, panelW, panelH, 0x33FFFFFF);
        drawHeader(graphics, mx, my);
        for (Part part : Part.values()) {
            drawColumn(graphics, part, mx, my);
        }
        drawFooter(graphics, mx, my);

        super.extractRenderState(graphics, mx, my, delta);
        graphics.pose().popMatrix();

        if (tooltip != null) {
            graphics.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
        }
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mx, int my) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX + (panelW / 2f), panelY + 8);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, "TEXTUREPACK COMBINER", 0, 0, PINK);
        graphics.pose().popMatrix();

        int[] done = doneRect();
        button(graphics, done, inside(mx, my, done));
        graphics.centeredText(this.font, "Done", done[0] + (done[2] / 2), done[1] + 4, TEXT);

        String hint = "Pick a pack for each part, then press Combine. Pick None to keep that part from your other packs";
        graphics.text(this.font, this.font.plainSubstrByWidth(hint, done[0] - panelX - 24), panelX + 12,
                panelY + HEADER_H - 20, TEXT_FAINT);
        graphics.fill(panelX + 8, panelY + HEADER_H - 6, panelX + panelW - 8, panelY + HEADER_H - 5, DIVIDER);
    }

    private void drawColumn(GuiGraphicsExtractor graphics, Part part, int mx, int my) {
        int x = columnX(part);
        int headY = panelY + HEADER_H + 2;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 2, headY);
        graphics.pose().scale(COLUMN_SCALE, COLUMN_SCALE);
        graphics.text(this.font, part.title, 0, 0, TEXT);
        graphics.pose().popMatrix();

        String chosen = PackCombiner.chosen(part);
        boolean missing = !PackCombiner.NONE.equals(chosen) && choices.stream().noneMatch(c -> c.file().equals(chosen));
        String label = PackCombiner.label(chosen) + (missing ? " (missing)" : "");
        graphics.text(this.font, this.font.plainSubstrByWidth(label, colW - 4), x + 2, headY + 15,
                missing ? MISSING : PINK);

        graphics.fill(x, listY, x + colW, listY + listH, CARD_BG);
        outline(graphics, x, listY, colW, listH, BOX_BORDER);

        int column = part.ordinal();
        int shownRows = Math.min(rows() - scroll[column], rowsShown());
        for (int i = 0; i < shownRows; i++) {
            int row = scroll[column] + i;
            String file = fileAt(row);
            int[] r = rowRect(part, i);
            boolean selected = file.equals(chosen);
            boolean hovered = inside(mx, my, r);
            graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                    selected ? PINK_FILL : (hovered ? 0x22FFFFFF : 0x18000000));
            outline(graphics, r[0], r[1], r[2], r[3], selected ? PINK : (hovered ? 0x80FFFFFF : 0x22FFFFFF));

            int textW = r[2] - 12;
            Component name;
            Component description;
            if (row == 0) {
                name = Component.literal("None");
                description = Component.literal("Keep " + part.what + " from your other packs")
                        .withStyle(ChatFormatting.GRAY);
            } else {
                PackCombiner.Choice choice = choices.get(row - 1);
                name = choice.title();
                description = choice.description();
            }
            List<FormattedCharSequence> nameLines = this.font.split(name, textW);
            List<FormattedCharSequence> descLines = this.font.split(description, textW);
            if (!nameLines.isEmpty()) {
                graphics.text(this.font, nameLines.get(0), r[0] + 6, r[1] + 3, selected ? PINK : TEXT);
            }
            if (!descLines.isEmpty()) {
                graphics.text(this.font, descLines.get(0), r[0] + 6, r[1] + 13, TEXT_DIM);
            }
            if (hovered && (nameLines.size() > 1 || descLines.size() > 1)) {
                List<FormattedCharSequence> tip = new java.util.ArrayList<>(this.font.split(name, 220));
                tip.addAll(this.font.split(description, 220));
                tooltip = tip;
            }
        }

        if (rows() > rowsShown()) {
            int barH = Math.max(12, listH * rowsShown() / rows());
            int maxScroll = rows() - rowsShown();
            int barY = listY + (int) ((listH - barH) * (scroll[column] / (double) maxScroll));
            graphics.fill(x + colW - 4, barY, x + colW - 2, barY + barH, 0x88FFFFFF);
        }
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int mx, int my) {
        int[] folder = folderRect();
        button(graphics, folder, inside(mx, my, folder));
        graphics.centeredText(this.font, "Packs Folder", folder[0] + (folder[2] / 2), folder[1] + 4, TEXT);

        int[] combine = combineRect();
        boolean busy = PackCombiner.building();
        pinkButton(graphics, combine, inside(mx, my, combine) && !busy);
        graphics.centeredText(this.font, busy ? "Combining..." : "Combine", combine[0] + (combine[2] / 2),
                combine[1] + 4, TEXT);

        String status;
        int colour = TEXT_FAINT;
        if (busy) {
            status = "Combining packs, this takes a few seconds...";
        } else if (ConfigManager.get().packCombinerEnabled) {
            status = "On: " + PackCombiner.summary(PackCombiner.chosenAll());
            colour = TEXT_DIM;
        } else {
            status = "Off. Press Combine to build the pack and turn it on";
        }
        int left = folder[0] + folder[2] + 10;
        int room = combine[0] - left - 10;
        graphics.text(this.font, this.font.plainSubstrByWidth(status, room), left, folder[1] + 4, colour);
    }

    private static void button(GuiGraphicsExtractor graphics, int[] r, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xAA3C5A70 : 0x66223140);
        outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFAFD4EC : 0xFF6A8CA8);
    }

    private static void pinkButton(GuiGraphicsExtractor graphics, int[] r, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xCCFF55FF : 0x99FF55FF);
        outline(graphics, r[0], r[1], r[2], r[3], PINK);
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
        double x = local.x();
        double y = local.y();
        if (inside(x, y, doneRect())) {
            onClose();
            return true;
        }
        if (inside(x, y, folderRect())) {
            PackCombiner.openFolder();
            return true;
        }
        if (inside(x, y, combineRect())) {
            if (!PackCombiner.building()) {
                ConfigManager.get().packCombinerEnabled = true;
                ConfigManager.save();
                PackCombiner.apply();
            }
            return true;
        }
        for (Part part : Part.values()) {
            int column = part.ordinal();
            int shownRows = Math.min(rows() - scroll[column], rowsShown());
            for (int i = 0; i < shownRows; i++) {
                if (inside(x, y, rowRect(part, i))) {
                    PackCombiner.choose(part, fileAt(scroll[column] + i));
                    return true;
                }
            }
        }
        return super.mouseClicked(local, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float s = scale();
        double x = (mouseX - offsetX()) / s;
        double y = (mouseY - offsetY()) / s;
        if (y >= listY && y <= listY + listH) {
            for (Part part : Part.values()) {
                int cx = columnX(part);
                if (x >= cx && x <= cx + colW) {
                    scroll[part.ordinal()] -= (int) Math.signum(verticalAmount);
                    clampScroll();
                    return true;
                }
            }
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
}
