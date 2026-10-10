package com.qza.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.itemlist.MarketItems;
import com.qza.itemlist.MarketItems.Market;
import com.qza.itemlist.RepoItem;
import com.qza.search.MarketSearch;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class MarketSearchScreen extends Screen {
    private static final int DIM = 0x66000000;
    private static final int PANEL_BG = 0xC0101014;
    private static final int PANEL_EDGE = 0x33FFFFFF;
    private static final int BOX_BORDER = 0x40FFFFFF;
    private static final int DIVIDER = 0xFFC8D4DC;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int PINK = 0xFFFF55FF;
    private static final int PINK_FILL = 0x66FF55FF;
    private static final int HOVER_FILL = 0x30FFFFFF;
    private static final int STAR_GOLD = 0xFFFFAA00;
    private static final int STAR_RED = 0xFFFF5555;
    private static final int STAR_OFF = 0xFF555555;

    private static final float TITLE_SCALE = 1.5f;
    private static final int MAX_W = 300;
    private static final int PAD = 10;
    private static final int ROW_H = 18;
    private static final int GAP = 6;
    private static final int TITLE_H = 26;
    private static final int LABEL_H = 12;
    private static final int HINT_H = 14;
    private static final int BUTTON_W = 52;
    private static final int DELETE_W = 16;
    private static final int SUGGESTIONS = 6;

    private final Market market;
    private final String initial;
    private final List<MarketItems.Entry> suggestions = new ArrayList<>();

    private EditBox box;
    private int selected = -1;
    private boolean wasReady;
    private List<Component> tooltip;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int innerX;
    private int innerW;
    private int extrasY;
    private int searchY;
    private int listY;
    private int historyLabelY;
    private int historyY;
    private int hintY;

    public MarketSearchScreen(Market market, String initial) {
        super(Component.literal("QZA"));
        this.market = market;
        this.initial = initial == null ? "" : initial;
    }

    private boolean auction() {
        return market == Market.AUCTION;
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
        String value = box == null ? initial : box.getValue();
        box = new EditBox(this.font, innerX + 5, searchY + 5, innerW - BUTTON_W - GAP - 10, 10, Component.empty());
        box.setBordered(false);
        box.setMaxLength(64);
        box.setHint(Component.literal(auction() ? "Search the auction house..." : "Search the bazaar...")
                .withStyle(ChatFormatting.DARK_GRAY));
        box.setValue(value);
        box.setResponder(text -> {
            selected = -1;
            refresh();
        });
        addRenderableWidget(box);
        setInitialFocus(box);
        refresh();
    }

    private void layout() {
        panelW = Math.min(MAX_W, this.width - 16);
        innerW = panelW - (PAD * 2);
        panelH = PAD + TITLE_H + (auction() ? ROW_H + GAP : 0) + ROW_H + GAP
                + (SUGGESTIONS * ROW_H) + GAP + LABEL_H + (MarketSearch.HISTORY * ROW_H) + HINT_H + PAD;
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(4, (this.height - panelH) / 2);
        innerX = panelX + PAD;

        int y = panelY + PAD + TITLE_H;
        if (auction()) {
            extrasY = y;
            y += ROW_H + GAP;
        }
        searchY = y;
        y += ROW_H + GAP;
        listY = y;
        y += (SUGGESTIONS * ROW_H) + GAP;
        historyLabelY = y;
        y += LABEL_H;
        historyY = y;
        y += MarketSearch.HISTORY * ROW_H;
        hintY = y + 4;
    }

    private void refresh() {
        suggestions.clear();
        suggestions.addAll(MarketItems.matches(market, box == null ? initial : box.getValue(), SUGGESTIONS));
        if (selected >= suggestions.size()) {
            selected = suggestions.size() - 1;
        }
        wasReady = MarketItems.ready(market);
    }

    @Override
    public void tick() {
        super.tick();
        if (!wasReady && MarketItems.ready(market)) {
            refresh();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float s = scale();
        int mx = Math.round((mouseX - offsetX()) / s);
        int my = Math.round((mouseY - offsetY()) / s);
        tooltip = null;
        MarketItems.beginFrame();

        graphics.fill(0, 0, this.width, this.height, DIM);

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(graphics, panelX, panelY, panelW, panelH, PANEL_EDGE);

        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX + (panelW / 2), panelY + PAD);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, auction() ? "AUCTION HOUSE SEARCH" : "BAZAAR SEARCH", 0, 0, PINK);
        graphics.pose().popMatrix();
        graphics.fill(innerX, panelY + PAD + TITLE_H - 7, innerX + innerW, panelY + PAD + TITLE_H - 6, DIVIDER);

        if (auction()) {
            drawExtras(graphics, mx, my);
        }
        drawSearch(graphics, mx, my);
        drawSuggestions(graphics, mx, my);
        drawHistory(graphics, mx, my);

        graphics.centeredText(this.font, "Enter to search - Tab to fill in - Esc to close",
                panelX + (panelW / 2), hintY, TEXT_FAINT);

        super.extractRenderState(graphics, mx, my, delta);
        graphics.pose().popMatrix();

        if (tooltip != null) {
            graphics.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
        }
    }

    private int[] petRect() {
        return new int[]{innerX, extrasY, (innerW - GAP) / 2, ROW_H};
    }

    private int[] starRect() {
        int w = (innerW - GAP) / 2;
        return new int[]{innerX + w + GAP, extrasY, innerW - w - GAP, ROW_H};
    }

    private void drawExtras(GuiGraphicsExtractor graphics, int mx, int my) {
        int[] pet = petRect();
        boolean on = MarketSearch.maxPetLevel();
        boolean overPet = inside(mx, my, pet);
        toggle(graphics, pet, on, overPet);
        graphics.centeredText(this.font, "Max Pet Level: " + (on ? "ON" : "OFF"),
                pet[0] + (pet[2] / 2), pet[1] + 5, on ? TEXT : TEXT_DIM);
        if (overPet) {
            tooltip = List.of(Component.literal("Only shows pets at their max level, like [Lvl 100]"));
        }

        int[] star = starRect();
        boolean overStars = inside(mx, my, star);
        toggle(graphics, star, MarketSearch.stars() > 0, overStars);
        int glyph = this.font.width("✪");
        int x = star[0] + (star[2] - (glyph * MarketSearch.MAX_STARS)) / 2;
        for (int i = 0; i < MarketSearch.MAX_STARS; i++) {
            int colour = i >= MarketSearch.stars() ? STAR_OFF : (i < 5 ? STAR_GOLD : STAR_RED);
            graphics.text(this.font, "✪", x + (i * glyph), star[1] + 5, colour, false);
        }
        if (overStars) {
            tooltip = List.of(Component.literal("Stars to add to dungeon items. The red ones are master stars."),
                    Component.literal("Click the same star again to clear it").withStyle(ChatFormatting.GRAY));
        }
    }

    private int starAt(double mouseX) {
        int[] star = starRect();
        int glyph = this.font.width("✪");
        int x = star[0] + (star[2] - (glyph * MarketSearch.MAX_STARS)) / 2;
        int index = (int) Math.floor((mouseX - x) / glyph);
        return Math.max(0, Math.min(MarketSearch.MAX_STARS - 1, index)) + 1;
    }

    private int[] searchButton() {
        return new int[]{innerX + innerW - BUTTON_W, searchY, BUTTON_W, ROW_H};
    }

    private void drawSearch(GuiGraphicsExtractor graphics, int mx, int my) {
        int boxW = innerW - BUTTON_W - GAP;
        graphics.fill(innerX, searchY, innerX + boxW, searchY + ROW_H, 0x66000000);
        outline(graphics, innerX, searchY, boxW, ROW_H, box.isFocused() ? PINK : BOX_BORDER);

        int[] button = searchButton();
        boolean over = inside(mx, my, button);
        graphics.fill(button[0], button[1], button[0] + button[2], button[1] + button[3],
                over ? 0xAAFF55FF : PINK_FILL);
        outline(graphics, button[0], button[1], button[2], button[3], PINK);
        graphics.centeredText(this.font, "Search", button[0] + (button[2] / 2), button[1] + 5, TEXT);
    }

    private int[] suggestionRect(int index) {
        return new int[]{innerX, listY + (index * ROW_H), innerW, ROW_H};
    }

    private void drawSuggestions(GuiGraphicsExtractor graphics, int mx, int my) {
        if (suggestions.isEmpty()) {
            String status;
            if (!MarketItems.ready(market)) {
                status = auction() ? "Loading items..." : "Loading bazaar items...";
            } else if (box.getValue().isBlank()) {
                status = "Start typing to see items";
            } else {
                status = "No items match - Enter still searches for it";
            }
            graphics.text(this.font, status, innerX + 4, listY + 5, TEXT_FAINT, false);
            return;
        }
        for (int i = 0; i < suggestions.size(); i++) {
            MarketItems.Entry entry = suggestions.get(i);
            int[] row = suggestionRect(i);
            boolean over = inside(mx, my, row);
            drawRow(graphics, row, entry.name(), entry.item(), over, i == selected, row[2]);
        }
    }

    private int[] historyRect(int index) {
        return new int[]{innerX, historyY + (index * ROW_H), innerW - DELETE_W - 4, ROW_H};
    }

    private int[] deleteRect(int index) {
        return new int[]{innerX + innerW - DELETE_W, historyY + (index * ROW_H) + 1, DELETE_W, ROW_H - 2};
    }

    private void drawHistory(GuiGraphicsExtractor graphics, int mx, int my) {
        List<String> history = MarketSearch.history(market);
        graphics.text(this.font, "RECENT SEARCHES", innerX, historyLabelY, TEXT_FAINT, false);
        if (history.isEmpty()) {
            graphics.text(this.font, "Nothing yet", innerX + 4, historyY + 5, TEXT_FAINT, false);
            return;
        }
        for (int i = 0; i < Math.min(history.size(), MarketSearch.HISTORY); i++) {
            String text = history.get(i);
            int[] row = historyRect(i);
            drawRow(graphics, row, text, MarketItems.find(market, text), inside(mx, my, row), false, row[2]);

            int[] del = deleteRect(i);
            boolean overDel = inside(mx, my, del);
            graphics.fill(del[0], del[1], del[0] + del[2], del[1] + del[3], overDel ? 0xAA8B2E2E : 0x66401E1E);
            outline(graphics, del[0], del[1], del[2], del[3], overDel ? 0xFFFF9A9A : 0xFFA85C5C);
            graphics.centeredText(this.font, "x", del[0] + (del[2] / 2), del[1] + 4,
                    overDel ? 0xFFFFFFFF : 0xFFCCCCCC);
            if (overDel) {
                tooltip = List.of(Component.literal("Remove from recent searches"));
            }
        }
    }

    private void drawRow(GuiGraphicsExtractor graphics, int[] row, String text, RepoItem item,
                         boolean hovered, boolean chosen, int width) {
        if (chosen) {
            graphics.fill(row[0], row[1], row[0] + row[2], row[1] + row[3], PINK_FILL);
            outline(graphics, row[0], row[1], row[2], row[3], PINK);
        } else if (hovered) {
            graphics.fill(row[0], row[1], row[0] + row[2], row[1] + row[3], HOVER_FILL);
        }
        int textX = row[0] + 4;
        if (item != null) {
            graphics.item(item.stack(), row[0] + 1, row[1] + 1);
            textX = row[0] + 21;
            if (hovered) {
                tooltip = item.tooltip();
            }
        }
        graphics.text(this.font, this.font.plainSubstrByWidth(text, row[0] + width - textX - 4),
                textX, row[1] + 5, TEXT, false);
    }

    private static void toggle(GuiGraphicsExtractor graphics, int[] r, boolean on, boolean hovered) {
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
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    private void run(String text) {
        Mc.setScreen(null);
        MarketSearch.search(market, text);
    }

    private String chosenText() {
        if (selected >= 0 && selected < suggestions.size()) {
            return suggestions.get(selected).name();
        }
        return box.getValue();
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

        if (auction()) {
            if (inside(x, y, petRect())) {
                MarketSearch.toggleMaxPetLevel();
                return true;
            }
            if (inside(x, y, starRect())) {
                int clicked = starAt(x);
                MarketSearch.setStars(clicked == MarketSearch.stars() ? 0 : clicked);
                return true;
            }
        }
        if (inside(x, y, searchButton())) {
            run(box.getValue());
            return true;
        }
        for (int i = 0; i < suggestions.size(); i++) {
            if (inside(x, y, suggestionRect(i))) {
                run(suggestions.get(i).name());
                return true;
            }
        }
        List<String> history = MarketSearch.history(market);
        for (int i = 0; i < Math.min(history.size(), MarketSearch.HISTORY); i++) {
            if (inside(x, y, deleteRect(i))) {
                MarketSearch.forget(market, history.get(i));
                return true;
            }
            if (inside(x, y, historyRect(i))) {
                run(history.get(i));
                return true;
            }
        }
        return super.mouseClicked(local, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(toLogical(event));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        float s = scale();
        return super.mouseDragged(toLogical(event), deltaX / s, deltaY / s);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            run(chosenText());
            return true;
        }
        if (key == InputConstants.KEY_DOWN && !suggestions.isEmpty()) {
            selected = Math.min(suggestions.size() - 1, selected + 1);
            return true;
        }
        if (key == InputConstants.KEY_UP && !suggestions.isEmpty()) {
            selected = Math.max(-1, selected - 1);
            return true;
        }
        if (key == InputConstants.KEY_TAB && !suggestions.isEmpty()) {
            String fill = suggestions.get(Math.max(0, selected)).name();
            selected = -1;
            box.setValue(fill);
            box.moveCursorToEnd(false);
            setFocused(box);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
