package com.qza.gui;

import com.qza.config.ConfigManager;
import com.qza.music.MusicAliases;
import com.qza.music.MusicLibrary;
import com.qza.music.MusicTrims;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MusicNamesScreen extends Screen {
    private static final int PANEL_BG = 0x55000000;
    private static final int BOX_BG = 0x66000000;
    private static final int BOX_BORDER = 0x40FFFFFF;
    private static final int DIVIDER = 0xFFC8D4DC;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int PINK = 0xFFFF55FF;

    private static final float TITLE_SCALE = 1.5f;
    private static final int HEADER_H = 44;
    private static final int COLUMNS_H = 16;
    private static final int ROW_H = 22;
    private static final int FIELD_H = 16;
    private static final int GEAR_W = 18;

    private final List<Entry> entries = new ArrayList<>();
    private double scroll;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listW;
    private int listY;
    private int listH;
    private int nameW;
    private int fileX;

    public MusicNamesScreen() {
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
        entries.clear();

        for (Path track : MusicLibrary.tracks()) {
            String file = track.getFileName().toString();
            EditBox box = new EditBox(this.font, listX + 5, listY, nameW - 10, 12,
                    Component.empty());
            box.setBordered(false);
            box.setMaxLength(48);
            box.setHint(Component.literal(MusicAliases.stripExtension(file))
                    .withStyle(ChatFormatting.DARK_GRAY));
            box.setValue(MusicAliases.aliasOf(file));
            box.visible = false;
            addRenderableWidget(box);
            entries.add(new Entry(file, box));
        }
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

        nameW = Math.max(90, Math.min(240, (int) (listW * 0.45)));
        fileX = listX + nameW + 14;
    }

    private int contentHeight() {
        return COLUMNS_H + (entries.size() * ROW_H);
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - listH)));
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

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(graphics, panelX, panelY, panelW, panelH, 0x33FFFFFF);

        drawHeader(graphics);
        drawRows(graphics, mx, my);

        super.extractRenderState(graphics, mx, my, delta);

        graphics.pose().popMatrix();
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX + (panelW / 2), panelY + 10);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, "QZA SONG NAMES", 0, 0, PINK);
        graphics.pose().popMatrix();

        graphics.fill(panelX + 8, panelY + HEADER_H - 6,
                panelX + panelW - 8, panelY + HEADER_H - 5, DIVIDER);
    }

    private int[] gearRect(int rowY) {
        return new int[]{listX + listW - GEAR_W - 8, rowY, GEAR_W, FIELD_H};
    }

    private static boolean inside(double x, double y, int[] r) {
        return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
    }

    private void drawGear(GuiGraphicsExtractor graphics, int[] r, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                hovered ? 0xAA3C5A70 : 0x66223140);
        outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFAFD4EC : 0xFF6A8CA8);

        int colour = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;
        int x = r[0] + 3;
        int y = r[1] + 3;

        graphics.fill(x + 4, y, x + 7, y + 2, colour);
        graphics.fill(x + 4, y + 9, x + 7, y + 11, colour);
        graphics.fill(x, y + 4, x + 2, y + 7, colour);
        graphics.fill(x + 9, y + 4, x + 11, y + 7, colour);

        graphics.fill(x + 2, y + 2, x + 9, y + 4, colour);
        graphics.fill(x + 2, y + 7, x + 9, y + 9, colour);
        graphics.fill(x + 2, y + 4, x + 4, y + 7, colour);
        graphics.fill(x + 7, y + 4, x + 9, y + 7, colour);
    }

    private void drawRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (entries.isEmpty()) {
            graphics.centeredText(this.font, "No music in config/qza/music/",
                    panelX + (panelW / 2), listY + 20, TEXT_DIM);
            return;
        }

        int top = listY - (int) Math.round(scroll);

        graphics.text(this.font, "Display Name", listX, top + 3, TEXT_DIM);
        graphics.text(this.font, "File", fileX, top + 3, TEXT_DIM);
        graphics.fill(listX, top + COLUMNS_H - 3, listX + listW, top + COLUMNS_H - 2, 0x33FFFFFF);

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int y = top + COLUMNS_H + (i * ROW_H);
            if (y + ROW_H < listY || y > listY + listH) {
                continue;
            }

            graphics.fill(listX, y, listX + nameW, y + FIELD_H, BOX_BG);
            outline(graphics, listX, y, nameW, FIELD_H, BOX_BORDER);

            int[] gear = gearRect(y);
            graphics.text(this.font, trim(entry.file, gear[0] - fileX - 8),
                    fileX, y + 4, TEXT_FAINT);
            drawGear(graphics, gear, inside(mouseX, mouseY, gear));

            String trimLabel = MusicTrims.label(entry.file);
            graphics.text(this.font, trimLabel,
                    gear[0] - this.font.width(trimLabel) - 6, y + 4,
                    MusicTrims.isTrimmed(entry.file) ? PINK : TEXT_FAINT);
        }

        int total = contentHeight();
        if (total > listH) {
            int trackX = panelX + panelW - 6;
            graphics.fill(trackX, listY, trackX + 3, listY + listH, 0x33FFFFFF);
            int barH = Math.max(16, (int) ((float) listH / total * listH));
            int barY = listY + (int) ((scroll / (total - listH)) * (listH - barH));
            graphics.fill(trackX, barY, trackX + 3, barY + barH, 0x99FFFFFF);
        }
    }

    private void positionFields() {
        int top = listY - (int) Math.round(scroll);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int y = top + COLUMNS_H + (i * ROW_H);
            boolean shown = y >= listY && y + FIELD_H <= listY + listH;
            entry.box.visible = shown;
            if (shown) {
                entry.box.setX(listX + 5);
                entry.box.setY(y + 4);
                entry.box.setWidth(nameW - 10);
            }
        }
    }

    private String trim(String label, int maxWidth) {
        if (maxWidth <= 0 || this.font.width(label) <= maxWidth) {
            return label;
        }
        String shown = label;
        while (shown.length() > 1 && this.font.width(shown + "...") > maxWidth) {
            shown = shown.substring(0, shown.length() - 1);
        }
        return shown + "...";
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

        if (local.button() == 0) {
            int top = listY - (int) Math.round(scroll);
            for (int i = 0; i < entries.size(); i++) {
                int y = top + COLUMNS_H + (i * ROW_H);
                if (y < listY || y + FIELD_H > listY + listH) {
                    continue;
                }
                if (inside(local.x(), local.y(), gearRect(y))) {
                    save();
                    this.minecraft.setScreen(new SongTrimScreen(entries.get(i).file));
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

    private void save() {
        for (Entry entry : entries) {
            MusicAliases.set(entry.file, entry.box.getValue());
        }
        ConfigManager.save();
    }

    @Override
    public void onClose() {
        save();
        Minecraft.getInstance().setScreen(new QZAScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class Entry {
        final String file;
        final EditBox box;

        Entry(String file, EditBox box) {
            this.file = file;
            this.box = box;
        }
    }
}
