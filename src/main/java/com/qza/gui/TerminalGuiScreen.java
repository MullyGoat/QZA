package com.qza.gui;

import com.qza.config.ConfigManager;
import com.qza.terminal.TerminalGrid;
import com.qza.terminal.TerminalLayout;
import com.qza.terminal.TerminalOverlay;
import com.qza.terminal.TerminalPainter;
import com.qza.terminal.TerminalSamples;
import com.qza.terminal.TerminalTemplate;
import com.qza.terminal.TerminalTemplates;
import com.qza.terminal.TerminalType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public class TerminalGuiScreen extends Screen {
    private static final int PANEL_BG = 0x55000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int PINK = 0xFFFF55FF;
    private static final int DIVIDER = 0xFFC8D4DC;

    private static final float TITLE_SCALE = 1.5f;
    private static final int HEADER_H = 52;
    private static final int RAIL_W = 150;
    private static final int ROW_H = 18;
    private static final int BUTTON_H = 16;
    private static final int DROP_ROW_H = 13;

    private static TerminalType selected = TerminalType.NUMBERS;

    private boolean dropping;
    private double dropScroll;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int railX;
    private int railY;
    private int viewX;
    private int viewY;
    private int viewW;
    private int viewH;

    public TerminalGuiScreen() {
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

        railX = panelX + 12;
        railY = panelY + HEADER_H;

        viewX = railX + RAIL_W + 14;
        viewY = railY;
        viewW = Math.max(120, (panelX + panelW - 12) - viewX);
        viewH = Math.max(80, (panelY + panelH - 12) - viewY);
    }

    private String templateKey() {
        return ConfigManager.get().terminalTemplates.getOrDefault(selected.key, "odin");
    }

    private void setTemplate(String key) {
        ConfigManager.get().terminalTemplates.put(selected.key, key);
        ConfigManager.save();
    }

    private int[] rowRect(int index) {
        return new int[]{railX, railY + (index * ROW_H), RAIL_W, ROW_H - 2};
    }

    private int[] dropRect() {
        return new int[]{railX, railY + (TerminalType.values().length * ROW_H) + 22,
                RAIL_W, BUTTON_H};
    }

    private int[] customRect() {
        int[] drop = dropRect();
        return new int[]{railX, drop[1] + BUTTON_H + 8, RAIL_W, BUTTON_H};
    }

    private int[] allRect() {
        int[] custom = customRect();
        return new int[]{railX, custom[1] + BUTTON_H + 6, RAIL_W, BUTTON_H};
    }

    private int dropListH() {
        return Math.min(6, TerminalTemplates.keys().size()) * DROP_ROW_H + 2;
    }

    private int[] dropListRect() {
        int[] drop = dropRect();
        return new int[]{drop[0], drop[1] + BUTTON_H + 1, RAIL_W, dropListH()};
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
        graphics.pose().translate(panelX + (panelW / 2f), panelY + 10);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, "TERMINAL GUI", 0, 0, PINK);
        graphics.pose().popMatrix();

        graphics.fill(panelX + 8, panelY + HEADER_H - 10,
                panelX + panelW - 8, panelY + HEADER_H - 9, DIVIDER);

        drawRail(graphics, mx, my);
        drawButtons(graphics, mx, my);
        drawPreview(graphics);

        super.extractRenderState(graphics, mx, my, delta);

        if (dropping) {
            drawDropList(graphics, mx, my);
        }

        graphics.pose().popMatrix();
    }

    private void drawRail(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        TerminalType[] types = TerminalType.values();
        for (int i = 0; i < types.length; i++) {
            int[] r = rowRect(i);
            boolean active = types[i] == selected;
            boolean hovered = inside(mouseX, mouseY, r);

            if (active) {
                graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x66000000);
                graphics.fill(r[0], r[1], r[0] + 2, r[1] + r[3], PINK);
            } else if (hovered) {
                graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x22FFFFFF);
            }

            graphics.text(this.font, types[i].label, r[0] + 8, r[1] + 4,
                    active ? PINK : (hovered ? TEXT : TEXT_DIM));

            String template = TerminalTemplates.label(
                    ConfigManager.get().terminalTemplates.getOrDefault(types[i].key, "odin"));
            int w = this.font.width(template);
            graphics.text(this.font, template, r[0] + r[2] - w - 6, r[1] + 4, TEXT_FAINT);
        }
    }

    private void drawButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] drop = dropRect();
        graphics.text(this.font, "Template", drop[0], drop[1] - 11, TEXT_DIM);
        button(graphics, drop, TerminalTemplates.label(templateKey()) + "   v",
                inside(mouseX, mouseY, drop));

        int[] custom = customRect();
        button(graphics, custom, "Edit Custom Template", inside(mouseX, mouseY, custom));

        int[] all = allRect();
        button(graphics, all, "Use For Every Terminal", inside(mouseX, mouseY, all));
    }

    private void button(GuiGraphicsExtractor graphics, int[] r, String label, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xAA3C5A70 : 0x99223140);
        outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFAFD4EC : 0xFF6A8CA8);
        graphics.centeredText(this.font, TerminalPainter.fit(this.font, label, r[2] - 8),
                r[0] + (r[2] / 2), r[1] + 4, TEXT);
    }

    private void drawDropList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] box = dropListRect();
        graphics.fill(box[0], box[1], box[0] + box[2], box[1] + box[3], 0xF00E1218);
        outline(graphics, box[0], box[1], box[2], box[3], PINK);

        List<String> keys = TerminalTemplates.keys();
        int first = (int) Math.round(dropScroll);
        int shown = (box[3] - 2) / DROP_ROW_H;

        for (int i = 0; i < shown && first + i < keys.size(); i++) {
            String key = keys.get(first + i);
            int rowY = box[1] + 1 + (i * DROP_ROW_H);
            boolean hovered = mouseX >= box[0] && mouseX <= box[0] + box[2]
                    && mouseY >= rowY && mouseY < rowY + DROP_ROW_H;
            boolean active = key.equals(templateKey());

            if (hovered) {
                graphics.fill(box[0] + 1, rowY, box[0] + box[2] - 1, rowY + DROP_ROW_H,
                        0x663C5A70);
            }
            graphics.text(this.font, TerminalTemplates.label(key), box[0] + 6, rowY + 3,
                    active ? PINK : (hovered ? TEXT : TEXT_DIM));
        }

        if (keys.size() > shown) {
            String more = (first + shown) + "/" + keys.size();
            graphics.text(this.font, more,
                    box[0] + box[2] - this.font.width(more) - 4,
                    box[1] + box[3] - 10, TEXT_FAINT);
        }
    }

    private void drawPreview(GuiGraphicsExtractor graphics) {
        graphics.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0x33000000);
        outline(graphics, viewX, viewY, viewW, viewH, 0x33FFFFFF);

        TerminalGrid grid = TerminalOverlay.limit(TerminalSamples.of(selected), selected);
        TerminalTemplate template = TerminalTemplates.get(templateKey());
        TerminalLayout natural = TerminalLayout.at(grid, template, 0, 0, 0, 0);

        float fit = Math.min(1f, Math.min((viewW - 16f) / natural.width,
                (viewH - 16f) / natural.height));

        graphics.pose().pushMatrix();
        graphics.pose().translate(viewX + (viewW / 2f), viewY + (viewH / 2f));
        graphics.pose().scale(fit, fit);
        graphics.pose().translate(-natural.width / 2f, -natural.height / 2f);

        TerminalPainter.draw(graphics, this.font, grid, template, natural,
                selected.sampleTitle, -1);

        graphics.pose().popMatrix();
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
        double mouseX = local.x();
        double mouseY = local.y();

        if (dropping) {
            int[] box = dropListRect();
            if (inside(mouseX, mouseY, box)) {
                List<String> keys = TerminalTemplates.keys();
                int first = (int) Math.round(dropScroll);
                int index = first + (int) ((mouseY - (box[1] + 1)) / DROP_ROW_H);
                if (index >= 0 && index < keys.size()) {
                    setTemplate(keys.get(index));
                }
                dropping = false;
                return true;
            }
            dropping = false;
            if (inside(mouseX, mouseY, dropRect())) {
                return true;
            }
        }

        if (inside(mouseX, mouseY, dropRect())) {
            dropping = true;
            dropScroll = 0;
            return true;
        }

        if (inside(mouseX, mouseY, customRect())) {
            this.minecraft.setScreen(new TerminalCustomScreen());
            return true;
        }

        if (inside(mouseX, mouseY, allRect())) {
            String key = templateKey();
            for (TerminalType type : TerminalType.values()) {
                ConfigManager.get().terminalTemplates.put(type.key, key);
            }
            ConfigManager.save();
            return true;
        }

        TerminalType[] types = TerminalType.values();
        for (int i = 0; i < types.length; i++) {
            if (inside(mouseX, mouseY, rowRect(i))) {
                selected = types[i];
                return true;
            }
        }

        return super.mouseClicked(local, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (dropping) {
            List<String> keys = TerminalTemplates.keys();
            int shown = (dropListH() - 2) / DROP_ROW_H;
            double max = Math.max(0, keys.size() - shown);
            dropScroll = Math.max(0, Math.min(max, dropScroll - verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        this.minecraft.setScreen(new QZAScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
