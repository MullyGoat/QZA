package com.qza.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.inventory.TerminatorIndicator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

public class TerminatorScreen extends Screen {
    private static final int PANEL_BG = 0x55000000;
    private static final int CARD_BG = 0x33000000;
    private static final int BOX_BORDER = 0x40FFFFFF;
    private static final int DIVIDER = 0xFFC8D4DC;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int PINK = 0xFFFF55FF;
    private static final int PINK_FILL = 0x99FF55FF;

    private static final float TITLE_SCALE = 1.5f;
    private static final int HEADER_H = 52;
    private static final int CARD_H = 104;
    private static final int FIELD_H = 16;
    private static final int LABEL_W = 48;
    private static final int GAP = 8;
    private static final int ROW_H = 24;
    private static final int PICK_W = 64;
    private static final int AUTO_W = 40;
    private static final long RESCAN_MS = 500L;

    private EditBox soulName;
    private EditBox duplexName;
    private List<ItemStack> found = List.of();
    private long scannedAt;
    private int listScroll;
    private Component tooltip;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int cardW;
    private int cardY;
    private int listY;

    public TerminatorScreen() {
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
        rescan();
        soulName = field(TerminatorIndicator.SOUL_EATER);
        duplexName = field(TerminatorIndicator.DUPLEX);
        updateHints();
    }

    private EditBox field(String kind) {
        EditBox box = new EditBox(this.font, 0, 0, Math.max(20, cardW - LABEL_W - 28), 12, Component.empty());
        box.setBordered(false);
        box.setMaxLength(64);
        box.setValue(TerminatorIndicator.name(kind));
        box.moveCursorToStart(false);
        box.setResponder(value -> TerminatorIndicator.setName(kind, value));
        addRenderableWidget(box);
        return box;
    }

    private void layout() {
        panelX = 8;
        panelY = 6;
        panelW = this.width - 16;
        panelH = this.height - 12;
        cardW = (panelW - 24 - GAP) / 2;
        cardY = panelY + HEADER_H;
        listY = cardY + CARD_H + 12;
    }

    private int cardX(String kind) {
        return TerminatorIndicator.SOUL_EATER.equals(kind) ? panelX + 12 : panelX + 12 + cardW + GAP;
    }

    private void rescan() {
        found = TerminatorIndicator.inventoryTerminators();
        scannedAt = System.currentTimeMillis();
    }

    private void updateHints() {
        hint(soulName, TerminatorIndicator.SOUL_EATER);
        hint(duplexName, TerminatorIndicator.DUPLEX);
    }

    private void hint(EditBox box, String kind) {
        if (box == null) {
            return;
        }
        ItemStack stack = TerminatorIndicator.find(found, kind);
        String name = stack.isEmpty() ? TerminatorIndicator.fallbackName(kind) : stack.getHoverName().getString();
        int room = Math.max(20, cardW - LABEL_W - 24);
        if (this.font.width(name) > room) {
            name = this.font.plainSubstrByWidth(name, room - this.font.width("...")).stripTrailing() + "...";
        }
        box.setHint(Component.literal(name).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (System.currentTimeMillis() - scannedAt > RESCAN_MS) {
            rescan();
            updateHints();
        }
        float s = scale();
        int mx = Math.round((mouseX - offsetX()) / s);
        int my = Math.round((mouseY - offsetY()) / s);
        tooltip = null;
        positionFields();

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(graphics, panelX, panelY, panelW, panelH, 0x33FFFFFF);

        drawHeader(graphics, mx, my);
        drawCard(graphics, TerminatorIndicator.SOUL_EATER, mx, my);
        drawCard(graphics, TerminatorIndicator.DUPLEX, mx, my);
        drawList(graphics, mx, my);

        super.extractRenderState(graphics, mx, my, delta);

        graphics.pose().popMatrix();

        if (tooltip != null) {
            graphics.setTooltipForNextFrame(this.font, this.font.split(tooltip, 220), mouseX, mouseY);
        }
    }

    private void positionFields() {
        for (String kind : TerminatorIndicator.KINDS) {
            EditBox box = TerminatorIndicator.SOUL_EATER.equals(kind) ? soulName : duplexName;
            box.setPosition(cardX(kind) + 8 + LABEL_W + 4, cardY + 26);
            box.setWidth(Math.max(20, cardW - LABEL_W - 24));
        }
    }

    private int[] doneRect() {
        int w = 64;
        return new int[]{panelX + panelW - w - 12, panelY + HEADER_H - 24, w, FIELD_H};
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mx, int my) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX + (panelW / 2), panelY + 8);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, "QZA TERMINATORS", 0, 0, PINK);
        graphics.pose().popMatrix();

        int[] done = doneRect();
        button(graphics, done, inside(mx, my, done));
        graphics.centeredText(this.font, "Done", done[0] + (done[2] / 2), done[1] + 4, TEXT);

        String status = "What shows on your screen while you hold each Terminator";
        if (!ConfigManager.get().terminatorIndicatorEnabled) {
            status = "Turn on Terminator Indicator in /qza to use this.";
        }
        graphics.text(this.font, this.font.plainSubstrByWidth(status, done[0] - panelX - 24),
                panelX + 12, panelY + HEADER_H - 20, TEXT_FAINT);

        graphics.fill(panelX + 8, panelY + HEADER_H - 6,
                panelX + panelW - 8, panelY + HEADER_H - 5, DIVIDER);
    }

    private int[] colourRect(String kind) {
        int x = cardX(kind) + 8 + LABEL_W;
        return new int[]{x, cardY + 42, cardW - LABEL_W - 16, FIELD_H};
    }

    private int[] autoRect(String kind) {
        return new int[]{cardX(kind) + cardW - AUTO_W - 8, cardY + 80, AUTO_W, FIELD_H};
    }

    private void drawCard(GuiGraphicsExtractor graphics, String kind, int mx, int my) {
        int x = cardX(kind);
        int y = cardY;
        graphics.fill(x, y, x + cardW, y + CARD_H, CARD_BG);
        outline(graphics, x, y, cardW, CARD_H, BOX_BORDER);

        int dot = TerminatorIndicator.dotRgb(kind);
        TerminatorIndicator.dot(graphics, x + 8, y + 7, dot);
        graphics.text(this.font, TerminatorIndicator.kindLabel(kind).toUpperCase(Locale.ROOT) + " TERMINATOR", x + 18, y + 6,
                0xFF000000 | dot);

        int labelX = x + 8;
        int fieldX = labelX + LABEL_W;
        int fieldW = cardW - LABEL_W - 16;

        graphics.text(this.font, "Name", labelX, y + 26, TEXT_DIM);
        box(graphics, fieldX, y + 22, fieldW);
        if (inside(mx, my, new int[]{fieldX, y + 22, fieldW, FIELD_H})) {
            tooltip = Component.literal("What shows on your screen. Leave it empty to use the item's own name");
        }

        graphics.text(this.font, "Colour", labelX, y + 46, TEXT_DIM);
        int[] colour = colourRect(kind);
        boolean overColour = inside(mx, my, colour);
        button(graphics, colour, overColour);
        String picked = TerminatorIndicator.colour(kind);
        graphics.centeredText(this.font, TerminatorIndicator.colourLabel(picked), colour[0] + (colour[2] / 2),
                colour[1] + 4, 0xFF000000 | TerminatorIndicator.colourRgb(picked));
        if (overColour) {
            tooltip = Component.literal("Click for the next colour, right-click for the one before. Item Colour "
                    + "keeps the item's own colours");
        }

        graphics.text(this.font, "Shows", labelX, y + 66, TEXT_DIM);
        ItemStack stack = TerminatorIndicator.find(found, kind);
        Component shown = TerminatorIndicator.display(stack, kind);
        List<FormattedCharSequence> lines = this.font.split(shown, fieldW);
        if (!lines.isEmpty()) {
            graphics.text(this.font, lines.get(0), fieldX, y + 66, 0xFFFFFFFF);
        }

        graphics.text(this.font, "Uses", labelX, y + 84, TEXT_DIM);
        String uuid = TerminatorIndicator.picked(kind);
        String uses = uuid.isEmpty() ? "Auto" : "Picked " + TerminatorIndicator.shortUuid(uuid);
        int usesW = uuid.isEmpty() ? fieldW : fieldW - AUTO_W - 6;
        graphics.text(this.font, this.font.plainSubstrByWidth(uses, usesW), fieldX, y + 84,
                uuid.isEmpty() ? TEXT_FAINT : TEXT);
        if (inside(mx, my, new int[]{fieldX, y + 80, usesW, FIELD_H})) {
            tooltip = Component.literal(uuid.isEmpty()
                    ? "QZA finds the Terminator with " + TerminatorIndicator.kindLabel(kind)
                    + " on it. Pick one in the list below to choose it yourself"
                    : "Only this Terminator counts as your " + TerminatorIndicator.kindLabel(kind) + " one");
        }
        if (!uuid.isEmpty()) {
            int[] auto = autoRect(kind);
            boolean overAuto = inside(mx, my, auto);
            button(graphics, auto, overAuto);
            graphics.centeredText(this.font, "Auto", auto[0] + (auto[2] / 2), auto[1] + 4, TEXT);
            if (overAuto) {
                tooltip = Component.literal("Go back to finding it by its " + TerminatorIndicator.kindLabel(kind)
                        + " enchant");
            }
        }
    }

    private int[] pickRect(int row, String kind) {
        int right = panelX + panelW - 12;
        int x = TerminatorIndicator.SOUL_EATER.equals(kind) ? right - (PICK_W * 2) - 6 : right - PICK_W;
        return new int[]{x, rowY(row) + 4, PICK_W, FIELD_H};
    }

    private int rowY(int row) {
        return listY + 14 + (row * ROW_H);
    }

    private int rowsShown() {
        int room = (panelY + panelH - 10) - (listY + 14);
        return Math.max(1, room / ROW_H);
    }

    private void clampList() {
        listScroll = Math.max(0, Math.min(listScroll, found.size() - rowsShown()));
    }

    private void drawList(GuiGraphicsExtractor graphics, int mx, int my) {
        int x = panelX + 12;
        graphics.text(this.font, "TERMINATORS IN YOUR INVENTORY", x, listY, TEXT_FAINT);
        if (found.isEmpty()) {
            graphics.text(this.font, "No Terminators in your inventory.", x, listY + 16, TEXT_DIM);
            graphics.text(this.font, "Put them in your inventory to pick which one is which if QZA gets it wrong.",
                    x, listY + 28, TEXT_FAINT);
            return;
        }
        clampList();
        int shown = Math.min(found.size() - listScroll, rowsShown());
        if (listScroll > 0) {
            String above = listScroll + " more above, scroll up";
            graphics.text(this.font, above, panelX + panelW - 12 - this.font.width(above), listY, TEXT_FAINT);
        }
        for (int i = 0; i < shown; i++) {
            ItemStack stack = found.get(listScroll + i);
            int y = rowY(i);
            int rowW = panelW - 24;
            graphics.fill(x, y, x + rowW, y + ROW_H - 2, CARD_BG);
            graphics.item(stack, x + 4, y + 3);

            int textW = pickRect(i, TerminatorIndicator.SOUL_EATER)[0] - (x + 26) - 8;
            List<FormattedCharSequence> name = this.font.split(stack.getHoverName(), Math.max(20, textW));
            if (!name.isEmpty()) {
                graphics.text(this.font, name.get(0), x + 26, y + 3, 0xFFFFFFFF);
            }
            String kind = TerminatorIndicator.kind(stack);
            String detail = TerminatorIndicator.details(stack);
            if (kind != null) {
                boolean auto = TerminatorIndicator.picked(kind).isEmpty();
                detail += auto ? "  - found as " + TerminatorIndicator.kindLabel(kind)
                        : "  - picked as " + TerminatorIndicator.kindLabel(kind);
            } else {
                detail += "  - not used";
            }
            graphics.text(this.font, this.font.plainSubstrByWidth(detail, Math.max(20, textW)), x + 26, y + 13,
                    TEXT_FAINT);

            String uuid = TerminatorIndicator.uuid(stack);
            for (String option : TerminatorIndicator.KINDS) {
                int[] r = pickRect(i, option);
                boolean on = !uuid.isEmpty() && uuid.equals(TerminatorIndicator.picked(option));
                boolean over = inside(mx, my, r);
                pinkToggle(graphics, r, on, over);
                graphics.centeredText(this.font, TerminatorIndicator.kindLabel(option), r[0] + (r[2] / 2), r[1] + 4,
                        on ? TEXT : TEXT_DIM);
                if (over) {
                    tooltip = uuid.isEmpty()
                            ? Component.literal("This Terminator has no SkyBlock UUID, so it can't be picked")
                            : Component.literal(on ? "Click to stop using this one as your "
                                    + TerminatorIndicator.kindLabel(option) + " Terminator"
                                    : "Use this one as your " + TerminatorIndicator.kindLabel(option)
                                    + " Terminator, whatever its enchants");
                }
            }
        }
        int below = found.size() - listScroll - shown;
        if (below > 0) {
            String more = below + " more below, scroll down";
            graphics.text(this.font, more, panelX + panelW - 12 - this.font.width(more), listY, TEXT_FAINT);
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

    private static void cycleColour(String kind, int step) {
        List<String> colours = TerminatorIndicator.nameColours();
        int at = colours.indexOf(TerminatorIndicator.colour(kind));
        int next = Math.floorMod((at < 0 ? 0 : at) + step, colours.size());
        TerminatorIndicator.setColour(kind, colours.get(next));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent local = toLogical(event);
        boolean left = local.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = local.button() == InputConstants.MOUSE_BUTTON_RIGHT;

        for (String kind : TerminatorIndicator.KINDS) {
            if ((left || right) && inside(local.x(), local.y(), colourRect(kind))) {
                cycleColour(kind, left ? 1 : -1);
                return true;
            }
            if (left && !TerminatorIndicator.picked(kind).isEmpty() && inside(local.x(), local.y(), autoRect(kind))) {
                TerminatorIndicator.pick(kind, "");
                return true;
            }
        }

        if (left) {
            if (inside(local.x(), local.y(), doneRect())) {
                onClose();
                return true;
            }
            clampList();
            int shown = Math.min(found.size() - listScroll, rowsShown());
            for (int i = 0; i < shown; i++) {
                String uuid = TerminatorIndicator.uuid(found.get(listScroll + i));
                for (String kind : TerminatorIndicator.KINDS) {
                    if (!inside(local.x(), local.y(), pickRect(i, kind))) {
                        continue;
                    }
                    if (!uuid.isEmpty()) {
                        boolean on = uuid.equals(TerminatorIndicator.picked(kind));
                        TerminatorIndicator.pick(kind, on ? "" : uuid);
                        updateHints();
                    }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float s = scale();
        double my = (mouseY - offsetY()) / s;
        if (my >= listY && found.size() > rowsShown()) {
            listScroll -= (int) Math.signum(verticalAmount);
            clampList();
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
}
