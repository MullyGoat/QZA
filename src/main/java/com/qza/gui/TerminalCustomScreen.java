package com.qza.gui;

import com.qza.config.ConfigManager;
import com.qza.terminal.TerminalGrid;
import com.qza.terminal.TerminalLayout;
import com.qza.terminal.TerminalOverlay;
import com.qza.terminal.TerminalPainter;
import com.qza.terminal.TerminalPalette;
import com.qza.terminal.TerminalSamples;
import com.qza.terminal.TerminalTemplate;
import com.qza.terminal.TerminalType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class TerminalCustomScreen extends Screen {
    private static final int PANEL_BG = 0x55000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int PINK = 0xFFFF55FF;
    private static final int DIVIDER = 0xFFC8D4DC;

    private static final float TITLE_SCALE = 1.5f;
    private static final int HEADER_H = 52;
    private static final int LIST_W = 244;
    private static final int ROW_H = 18;
    private static final int CTRL_W = 92;
    private static final int STEP_W = 13;
    private static final int SWATCH_W = 16;
    private static final int BUTTON_H = 16;
    private static final int TAB_H = 14;

    private static final int CHIP = 12;
    private static final int PALETTE_PAD = 3;

    private static final String CYCLE = "cycle";
    private static final String NUMBER = "number";
    private static final String COLOUR = "colour";
    private static final String TOGGLE = "toggle";
    private static final String TEXT_ROW = "text";

    private static TerminalType preview = TerminalType.NUMBERS;

    private final List<Row> rows = new ArrayList<>();
    private double scroll;

    private int paletteFor = -1;
    private int paletteX;
    private int paletteY;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listH;
    private int viewX;
    private int viewY;
    private int viewW;
    private int viewH;

    public TerminalCustomScreen() {
        super(Component.literal("QZA"));
    }

    private static TerminalTemplate model() {
        return ConfigManager.get().terminalCustom;
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
        listH = Math.max(ROW_H, (panelY + panelH - 12) - listY - BUTTON_H - 8);

        viewX = listX + LIST_W + 14;
        viewY = listY;
        viewW = Math.max(120, (panelX + panelW - 12) - viewX);
        viewH = Math.max(80, (panelY + panelH - 12) - viewY);

        rows.clear();
        TerminalTemplate t = model();

        rows.add(text("Name", t.name, v -> t.name = v, 24));

        rows.add(cycle("Cell Shape", () -> TerminalTemplate.pretty(t.shape),
                step -> t.shape = spin(TerminalTemplate.shapes(), t.shape, step)));
        rows.add(cycle("Slot Label", () -> TerminalTemplate.pretty(t.label),
                step -> t.label = spin(TerminalTemplate.labels(), t.label, step)));
        rows.add(cycle("Selected Style", () -> TerminalTemplate.pretty(t.mark),
                step -> t.mark = spin(TerminalTemplate.marks(), t.mark, step)));

        rows.add(number("Cell Size", () -> t.cell, v -> t.cell = v,
                TerminalTemplate.MIN_CELL, TerminalTemplate.MAX_CELL));
        rows.add(number("Gap", () -> t.gap, v -> t.gap = v, 0, TerminalTemplate.MAX_GAP));
        rows.add(number("Corner Radius", () -> t.radius, v -> t.radius = v, 0, 24));
        rows.add(number("Padding", () -> t.pad, v -> t.pad = v, 0, TerminalTemplate.MAX_PAD));
        rows.add(number("Edge Width", () -> t.edgeWidth, v -> t.edgeWidth = v,
                0, TerminalTemplate.MAX_BORDER));

        rows.add(toggle("Colour From Item", () -> t.itemColour, v -> t.itemColour = v));
        rows.add(toggle("Show Title", () -> t.showTitle, v -> t.showTitle = v));

        rows.add(colour("Panel", () -> t.panel, v -> t.panel = v));
        rows.add(colour("Panel Edge", () -> t.panelEdge, v -> t.panelEdge = v));
        rows.add(colour("Empty Slot", () -> t.slotEmpty, v -> t.slotEmpty = v));
        rows.add(colour("Plain Slot", () -> t.slotPlain, v -> t.slotPlain = v));
        rows.add(colour("Item Tint", () -> t.tint, v -> t.tint = v));
        rows.add(colour("Label Text", () -> t.textColour, v -> t.textColour = v));
        rows.add(colour("Selected", () -> t.markColour, v -> t.markColour = v));
        rows.add(colour("Hover", () -> t.hoverColour, v -> t.hoverColour = v));
        rows.add(colour("Title", () -> t.titleColour, v -> t.titleColour = v));
    }

    private static String spin(List<String> all, String current, int step) {
        int at = all.indexOf(current);
        int next = ((at < 0 ? 0 : at) + step) % all.size();
        return all.get(next < 0 ? all.size() - 1 : next);
    }

    private Row cycle(String label, Supplier<String> value, IntConsumer step) {
        Row row = new Row(label, CYCLE);
        row.value = value;
        row.step = step;
        return row;
    }

    private Row number(String label, IntSupplier value, IntConsumer set, int min, int max) {
        Row row = new Row(label, NUMBER);
        row.value = () -> String.valueOf(value.getAsInt());
        row.step = step -> set.accept(Math.max(min, Math.min(max, value.getAsInt() + step)));
        return row;
    }

    private Row toggle(String label, Supplier<Boolean> value, Consumer<Boolean> set) {
        Row row = new Row(label, TOGGLE);
        row.value = () -> value.get() ? "On" : "Off";
        row.step = step -> set.accept(!value.get());
        return row;
    }

    private Row colour(String label, IntSupplier value, IntConsumer set) {
        Row row = new Row(label, COLOUR);
        row.swatch = value;
        row.apply = set;
        row.value = () -> hex(value.getAsInt());
        row.box = field(CTRL_W - SWATCH_W - 6, 8, hex(value.getAsInt()));
        row.box.setValue(hex(value.getAsInt()));
        row.box.setResponder(entered -> {
            Integer parsed = parse(entered);
            if (parsed != null) {
                set.accept(parsed);
            }
        });
        return row;
    }

    private Row text(String label, String initial, Consumer<String> set, int max) {
        Row row = new Row(label, TEXT_ROW);
        row.value = () -> initial;
        row.box = field(CTRL_W, max, initial);
        row.box.setValue(initial == null ? "" : initial);
        row.box.setResponder(set);
        return row;
    }

    private EditBox field(int width, int maxLength, String hint) {
        EditBox box = new EditBox(this.font, listX, listY, Math.max(20, width), 12,
                Component.empty());
        box.setBordered(false);
        box.setMaxLength(maxLength);
        box.setHint(Component.literal(hint == null ? "" : hint)
                .withStyle(ChatFormatting.DARK_GRAY));
        box.visible = false;
        addRenderableWidget(box);
        return box;
    }

    private static String hex(int argb) {
        return String.format("%08X", argb);
    }

    private static Integer parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.length() != 6 && text.length() != 8) {
            return null;
        }
        try {
            long value = Long.parseLong(text, 16);
            return text.length() == 6 ? (int) (0xFF000000L | value) : (int) value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int[] rowRect(int index) {
        return new int[]{listX, listY + (index * ROW_H) - (int) Math.round(scroll),
                LIST_W, ROW_H - 2};
    }

    private int[] controlRect(int index) {
        int[] r = rowRect(index);
        return new int[]{r[0] + r[2] - CTRL_W, r[1], CTRL_W, ROW_H - 4};
    }

    private int[] swatchRect(int index) {
        int[] c = controlRect(index);
        return new int[]{c[0], c[1], SWATCH_W, c[3]};
    }

    private int[] tabRect(int index) {
        int w = viewW / TerminalType.values().length;
        return new int[]{viewX + (index * w), viewY, w, TAB_H};
    }

    private int[] resetRect() {
        return new int[]{listX, listY + listH + 6, 86, BUTTON_H};
    }

    private int[] backRect() {
        return new int[]{listX + 92, listY + listH + 6, 86, BUTTON_H};
    }

    private int paletteW() {
        return (TerminalPalette.COLUMNS * CHIP) + (PALETTE_PAD * 2);
    }

    private int paletteH() {
        return ((TerminalPalette.HUE_ROWS + 1) * CHIP) + (PALETTE_PAD * 2) + 11;
    }

    private int[] paletteRect() {
        return new int[]{paletteX, paletteY, paletteW(), paletteH()};
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
            Row row = rows.get(i);
            if (row.box == null) {
                continue;
            }
            int[] c = controlRect(i);
            boolean visible = c[1] >= listY - 2 && c[1] + c[3] <= listY + listH + 2;
            row.box.visible = visible;
            if (!visible) {
                continue;
            }
            row.box.setX(COLOUR.equals(row.kind) ? c[0] + SWATCH_W + 6 : c[0] + 4);
            row.box.setY(c[1] + 3);
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(graphics, panelX, panelY, panelW, panelH, 0x33FFFFFF);

        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX + (panelW / 2f), panelY + 10);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, "CUSTOM TEMPLATE", 0, 0, PINK);
        graphics.pose().popMatrix();

        graphics.fill(panelX + 8, panelY + HEADER_H - 10,
                panelX + panelW - 8, panelY + HEADER_H - 9, DIVIDER);

        graphics.enableScissor(listX - 2, listY, listX + LIST_W + 2, listY + listH);
        for (int i = 0; i < rows.size(); i++) {
            drawRow(graphics, i, mx, my);
        }
        graphics.disableScissor();

        drawTabs(graphics, mx, my);
        drawPreview(graphics);

        int[] reset = resetRect();
        button(graphics, reset, "Reset", inside(mx, my, reset));
        int[] back = backRect();
        button(graphics, back, "Done", inside(mx, my, back));

        super.extractRenderState(graphics, mx, my, delta);

        if (paletteFor >= 0) {
            drawPalette(graphics, mx, my);
        }

        graphics.pose().popMatrix();
    }

    private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        TerminalType[] types = TerminalType.values();
        for (int i = 0; i < types.length; i++) {
            int[] r = tabRect(i);
            boolean active = types[i] == preview;
            boolean hovered = inside(mouseX, mouseY, r);
            graphics.centeredText(this.font,
                    TerminalPainter.fit(this.font, types[i].label, r[2] - 4),
                    r[0] + (r[2] / 2), r[1] + 3,
                    active ? PINK : (hovered ? TEXT : TEXT_DIM));
            graphics.fill(r[0] + 1, r[1] + TAB_H - 1, r[0] + r[2] - 1, r[1] + TAB_H,
                    active ? PINK : 0x33FFFFFF);
        }
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
        graphics.text(this.font, row.label, r[0] + 4, r[1] + 5, TEXT_DIM);

        int[] c = controlRect(index);
        switch (row.kind) {
            case CYCLE, TOGGLE -> {
                boolean over = inside(mouseX, mouseY, c);
                graphics.fill(c[0], c[1], c[0] + c[2], c[1] + c[3],
                        over ? 0xAA3C5A70 : 0x99223140);
                outline(graphics, c[0], c[1], c[2], c[3], over ? 0xFFAFD4EC : 0xFF6A8CA8);
                graphics.centeredText(this.font, row.value.get(),
                        c[0] + (c[2] / 2), c[1] + 3, TEXT);
            }
            case NUMBER -> {
                int[] down = new int[]{c[0], c[1], STEP_W, c[3]};
                int[] up = new int[]{c[0] + c[2] - STEP_W, c[1], STEP_W, c[3]};
                stepper(graphics, down, "-", inside(mouseX, mouseY, down));
                stepper(graphics, up, "+", inside(mouseX, mouseY, up));
                graphics.centeredText(this.font, row.value.get(),
                        c[0] + (c[2] / 2), c[1] + 3, TEXT);
            }
            case COLOUR -> {
                int[] sw = swatchRect(index);
                boolean over = inside(mouseX, mouseY, sw);
                graphics.fill(sw[0], sw[1], sw[0] + sw[2], sw[1] + sw[3], 0xFF101010);
                graphics.fill(sw[0] + 1, sw[1] + 1, sw[0] + sw[2] - 1, sw[1] + sw[3] - 1,
                        row.swatch.getAsInt());
                outline(graphics, sw[0], sw[1], sw[2], sw[3],
                        over ? 0xFFFFFFFF : 0x66FFFFFF);

                int boxX = c[0] + SWATCH_W + 6;
                graphics.fill(boxX - 2, c[1], c[0] + c[2], c[1] + c[3], 0x55000000);
                outline(graphics, boxX - 2, c[1], c[0] + c[2] - boxX + 2, c[3], 0x40FFFFFF);
            }
            default -> {
                graphics.fill(c[0], c[1], c[0] + c[2], c[1] + c[3], 0x55000000);
                outline(graphics, c[0], c[1], c[2], c[3], 0x40FFFFFF);
            }
        }
    }

    private void drawPalette(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] box = paletteRect();
        graphics.fill(box[0], box[1], box[0] + box[2], box[1] + box[3], 0xF00E1218);
        outline(graphics, box[0], box[1], box[2], box[3], PINK);

        int current = rows.get(paletteFor).swatch.getAsInt();
        int hovered = 0;
        boolean any = false;

        for (int i = 0; i < TerminalPalette.size(); i++) {
            int column = i % TerminalPalette.COLUMNS;
            int row = i / TerminalPalette.COLUMNS;
            int x = box[0] + PALETTE_PAD + (column * CHIP);
            int y = box[1] + PALETTE_PAD + (row * CHIP);
            int argb = TerminalPalette.at(i);

            graphics.fill(x, y, x + CHIP - 1, y + CHIP - 1, argb);
            boolean over = mouseX >= x && mouseX < x + CHIP - 1
                    && mouseY >= y && mouseY < y + CHIP - 1;
            if (over) {
                outline(graphics, x, y, CHIP - 1, CHIP - 1, 0xFFFFFFFF);
                hovered = argb;
                any = true;
            } else if ((argb & 0xFFFFFF) == (current & 0xFFFFFF)) {
                outline(graphics, x, y, CHIP - 1, CHIP - 1, PINK);
            }
        }

        int alphaY = box[1] + PALETTE_PAD + (TerminalPalette.HUE_ROWS * CHIP);
        for (int step = 0; step < TerminalPalette.ALPHA_STEPS; step++) {
            int x = box[0] + PALETTE_PAD + (step * CHIP);
            int value = TerminalPalette.alphaAt(step);
            int argb = (value << 24) | (current & 0xFFFFFF);

            graphics.fill(x, alphaY, x + CHIP - 1, alphaY + CHIP - 1, 0xFF303030);
            graphics.fill(x, alphaY, x + ((CHIP - 1) / 2), alphaY + ((CHIP - 1) / 2), 0xFF606060);
            graphics.fill(x + ((CHIP - 1) / 2), alphaY + ((CHIP - 1) / 2),
                    x + CHIP - 1, alphaY + CHIP - 1, 0xFF606060);
            graphics.fill(x, alphaY, x + CHIP - 1, alphaY + CHIP - 1, argb);

            boolean over = mouseX >= x && mouseX < x + CHIP - 1
                    && mouseY >= alphaY && mouseY < alphaY + CHIP - 1;
            if (over) {
                outline(graphics, x, alphaY, CHIP - 1, CHIP - 1, 0xFFFFFFFF);
                hovered = argb;
                any = true;
            }
        }

        String label = any ? hex(hovered) : hex(current);
        graphics.text(this.font, label, box[0] + PALETTE_PAD,
                box[1] + box[3] - 10, any ? TEXT : TEXT_FAINT);
    }

    private void stepper(GuiGraphicsExtractor graphics, int[] r, String label, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xAA3C5A70 : 0x66223140);
        graphics.centeredText(this.font, label, r[0] + (r[2] / 2), r[1] + 3,
                hovered ? TEXT : TEXT_FAINT);
    }

    private void button(GuiGraphicsExtractor graphics, int[] r, String label, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xAA3C5A70 : 0x99223140);
        outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFAFD4EC : 0xFF6A8CA8);
        graphics.centeredText(this.font, label, r[0] + (r[2] / 2), r[1] + 4, TEXT);
    }

    private void drawPreview(GuiGraphicsExtractor graphics) {
        int top = viewY + TAB_H + 4;
        int height = viewH - TAB_H - 4;
        graphics.fill(viewX, top, viewX + viewW, top + height, 0x33000000);
        outline(graphics, viewX, top, viewW, height, 0x33FFFFFF);

        TerminalGrid grid = TerminalOverlay.limit(TerminalSamples.of(preview), preview,
                preview.argument(preview.sampleTitle));
        TerminalTemplate template = model().copy().tidy();
        TerminalLayout natural = TerminalLayout.at(grid, template, 0, 0, 0, 0);

        float fit = Math.min(1f, Math.min((viewW - 16f) / natural.width,
                (height - 16f) / natural.height));

        graphics.pose().pushMatrix();
        graphics.pose().translate(viewX + (viewW / 2f), top + (height / 2f));
        graphics.pose().scale(fit, fit);
        graphics.pose().translate(-natural.width / 2f, -natural.height / 2f);
        TerminalPainter.draw(graphics, this.font, grid, template, natural,
                preview.sampleTitle, -1);
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

    private void pickFromPalette(double mouseX, double mouseY) {
        int[] box = paletteRect();
        Row row = rows.get(paletteFor);
        int current = row.swatch.getAsInt();

        int column = (int) ((mouseX - (box[0] + PALETTE_PAD)) / CHIP);
        int gridRow = (int) ((mouseY - (box[1] + PALETTE_PAD)) / CHIP);
        if (column < 0 || column >= TerminalPalette.COLUMNS || gridRow < 0) {
            return;
        }

        Integer picked = null;
        if (gridRow < TerminalPalette.HUE_ROWS) {
            int chosen = TerminalPalette.at((gridRow * TerminalPalette.COLUMNS) + column);
            picked = (current & 0xFF000000) | (chosen & 0xFFFFFF);
        } else if (gridRow == TerminalPalette.HUE_ROWS) {
            picked = (TerminalPalette.alphaAt(column) << 24) | (current & 0xFFFFFF);
        }

        if (picked == null) {
            return;
        }
        row.apply.accept(picked);
        if (row.box != null) {
            row.box.setValue(hex(picked));
        }
        model().tidy();
        ConfigManager.save();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent local = toLogical(event);
        double mouseX = local.x();
        double mouseY = local.y();

        if (paletteFor >= 0) {
            if (inside(mouseX, mouseY, paletteRect())) {
                pickFromPalette(mouseX, mouseY);
                return true;
            }
            paletteFor = -1;
        }

        if (inside(mouseX, mouseY, resetRect())) {
            ConfigManager.get().terminalCustom = new TerminalTemplate();
            ConfigManager.save();
            this.minecraft.setScreen(new TerminalCustomScreen());
            return true;
        }
        if (inside(mouseX, mouseY, backRect())) {
            onClose();
            return true;
        }

        TerminalType[] types = TerminalType.values();
        for (int i = 0; i < types.length; i++) {
            if (inside(mouseX, mouseY, tabRect(i))) {
                preview = types[i];
                return true;
            }
        }

        if (mouseY >= listY && mouseY <= listY + listH) {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (COLOUR.equals(row.kind) && inside(mouseX, mouseY, swatchRect(i))) {
                    openPalette(i);
                    return true;
                }

                int[] c = controlRect(i);
                if (!inside(mouseX, mouseY, c)) {
                    continue;
                }
                if (CYCLE.equals(row.kind) || TOGGLE.equals(row.kind)) {
                    row.step.accept(local.button() == 1 ? -1 : 1);
                    model().tidy();
                    ConfigManager.save();
                    return true;
                }
                if (NUMBER.equals(row.kind)) {
                    boolean down = mouseX < c[0] + STEP_W;
                    boolean up = mouseX > c[0] + c[2] - STEP_W;
                    if (down || up) {
                        row.step.accept(down ? -1 : 1);
                        model().tidy();
                        ConfigManager.save();
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(local, doubleClick);
    }

    private void openPalette(int index) {
        int[] sw = swatchRect(index);
        paletteFor = index;
        paletteX = Math.min(sw[0], panelX + panelW - paletteW() - 6);
        paletteY = Math.min(sw[1] + sw[3] + 2, panelY + panelH - paletteH() - 6);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        float s = scale();
        double mx = (mouseX - offsetX()) / s;
        double my = (mouseY - offsetY()) / s;

        if (paletteFor >= 0) {
            return true;
        }
        if (mx >= listX && mx <= listX + LIST_W && my >= listY && my <= listY + listH) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (verticalAmount * ROW_H)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        model().tidy();
        ConfigManager.save();
        this.minecraft.setScreen(new TerminalGuiScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class Row {
        final String label;
        final String kind;

        Supplier<String> value;
        IntConsumer step;
        IntSupplier swatch;
        IntConsumer apply;
        EditBox box;

        Row(String label, String kind) {
            this.label = label;
            this.kind = kind;
        }
    }
}
