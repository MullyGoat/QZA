package com.qza.gui;

import com.qza.config.ConfigManager;
import com.qza.music.MusicAliases;
import com.qza.music.MusicLibrary;
import com.qza.music.MusicTrims;
import com.qza.music.PcmStream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Locale;

public class SongTrimScreen extends Screen {
    private static final int PANEL_BG = 0x55000000;
    private static final int BOX_BG = 0x66000000;
    private static final int BOX_BORDER = 0x40FFFFFF;
    private static final int DIVIDER = 0xFFC8D4DC;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int PINK = 0xFFFF55FF;
    private static final int BAD = 0xFFE05555;

    private static final float TITLE_SCALE = 1.5f;
    private static final int FIELD_W = 70;
    private static final int FIELD_H = 16;

    private final String file;
    private double length;

    private EditBox startBox;
    private EditBox endBox;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int bodyX;
    private int bodyY;

    public SongTrimScreen(String file) {
        super(Component.literal("QZA"));
        this.file = file;
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

        Path path = MusicLibrary.findByName(file);
        length = path == null ? 0.0 : PcmStream.lengthOf(path);

        startBox = field(bodyX + 60, bodyY, MusicTrims.start(file));
        endBox = field(bodyX + 60, bodyY + 26, MusicTrims.end(file));
    }

    private EditBox field(int x, int y, double value) {
        EditBox box = new EditBox(this.font, x + 5, y + 4, FIELD_W - 10, 12, Component.empty());
        box.setBordered(false);
        box.setMaxLength(12);
        box.setHint(Component.literal("0:00").withStyle(ChatFormatting.DARK_GRAY));
        box.setValue(format(value));
        addRenderableWidget(box);
        return box;
    }

    private static String format(double value) {
        if (value <= 0) {
            return "";
        }
        if (value < 60) {
            return trimZeros(value);
        }
        int minutes = (int) (value / 60);
        double seconds = value - (minutes * 60.0);
        return minutes + ":" + (seconds < 10 ? "0" : "") + trimZeros(seconds);
    }

    private static String trimZeros(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private void layout() {
        panelW = Math.min(this.width - 40, 320);
        panelH = 130;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        bodyX = panelX + 16;
        bodyY = panelY + 52;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float s = scale();
        int mx = Math.round((mouseX - offsetX()) / s);
        int my = Math.round((mouseY - offsetY()) / s);

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(graphics, panelX, panelY, panelW, panelH, 0x33FFFFFF);

        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX + (panelW / 2), panelY + 10);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, "QZA SONG LENGTH", 0, 0, PINK);
        graphics.pose().popMatrix();

        graphics.fill(panelX + 8, panelY + 30, panelX + panelW - 8, panelY + 31, DIVIDER);

        graphics.centeredText(this.font, trim(MusicAliases.display(file), panelW - 24),
                panelX + (panelW / 2), panelY + 36, TEXT);

        drawField(graphics, "Start", bodyX, bodyY, startBox);
        drawField(graphics, "End", bodyX, bodyY + 26, endBox);

        String note = length > 0
                ? "Song is " + MusicTrims.time(length) + " long - leave End blank to play to the end"
                : "Leave End blank to play to the end";
        graphics.text(this.font, trim(note, panelW - 24), bodyX, bodyY + 56, TEXT_FAINT);

        super.extractRenderState(graphics, mx, my, delta);

        graphics.pose().popMatrix();
    }

    private void drawField(GuiGraphicsExtractor graphics, String label,
                           int x, int y, EditBox box) {
        graphics.text(this.font, label, x, y + 4, TEXT_DIM);

        int fx = x + 60;
        boolean ok = parse(box) >= 0;
        graphics.fill(fx, y, fx + FIELD_W, y + FIELD_H, BOX_BG);
        outline(graphics, fx, y, FIELD_W, FIELD_H, ok ? BOX_BORDER : BAD);

        graphics.text(this.font, "sec", fx + FIELD_W + 6, y + 4, TEXT_FAINT);
    }

    private double parse(EditBox box) {
        return parseTime(box.getValue());
    }

    static double parseTime(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return 0.0;
        }

        try {
            if (text.indexOf(':') < 0) {
                double value = Double.parseDouble(text);
                return value < 0 ? -1.0 : value;
            }

            String[] parts = text.split(":", -1);
            if (parts.length > 3) {
                return -1.0;
            }
            double total = 0.0;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.isEmpty()) {
                    return -1.0;
                }
                double value = Double.parseDouble(part);
                if (value < 0) {
                    return -1.0;
                }
                if (i < parts.length - 1 && value != Math.rint(value)) {
                    return -1.0;
                }
                total = (total * 60.0) + value;
            }
            return total;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
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
        return super.mouseClicked(toLogical(event), doubleClick);
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
    public void onClose() {
        double from = parse(startBox);
        double to = parse(endBox);
        if (from >= 0 && to >= 0) {
            MusicTrims.set(file, from, to);
            ConfigManager.save();
        }
        Minecraft.getInstance().setScreen(new MusicNamesScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
