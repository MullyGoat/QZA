package com.qza.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.chat.ChatKeybind;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.gui.setting.ActionSetting;
import com.qza.gui.setting.DropdownSetting;
import com.qza.gui.setting.NumberSetting;
import com.qza.gui.setting.Setting;
import com.qza.gui.setting.SettingsRegistry;
import com.qza.gui.setting.SliderSetting;
import com.qza.gui.setting.ToggleSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QZAScreen extends Screen {
    private static final int PANEL_BG = 0x55000000;
    private static final int RAIL_BG = 0x33000000;
    private static final int BOX_BG = 0x66000000;
    private static final int BOX_BORDER = 0x40FFFFFF;
    private static final int SECTION_BG = 0x59000000;
    private static final int DIVIDER = 0xFFC8D4DC;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int ACCENT = 0xFF55FFFF;
    private static final int RAIL_LINE = 0xFF9AA5AC;
    private static final int PINK = 0xFFFF55FF;
    private static final int PINK_FILL = 0x99FF55FF;

    private static final int CARD_BG = 0x66000000;
    private static final int CARD_BG_HOVER = 0x88181C22;
    private static final int CARD_BODY = 0x40000000;
    private static final int CARD_BORDER = 0x33FFFFFF;
    private static final int CARD_BORDER_HOVER = 0x99FFFFFF;
    private static final int CARD_ON = 0x66FF55FF;
    private static final int CARD_ON_HOVER = 0x88FF55FF;
    private static final int SUB_HOVER = 0x1FFFFFFF;

    private static final int HEADER_H = 56;
    private static final int RAIL_W = 200;
    private static final int ROW_GAP = 6;
    private static final int SECTION_H = 18;
    private static final int CONTROL_PAD = 10;
    private static final int TOGGLE_W = 36;
    private static final int TOGGLE_H = 14;
    private static final int SLIDER_W = 100;
    private static final int SLIDER_H = 10;
    private static final int BUTTON_W = 88;
    private static final int BUTTON_H = 18;
    private static final int PENCIL_W = 18;

    private static final int CARD_H = 26;
    private static final int CARD_GAP = 6;
    private static final int CARD_MIN_W = 170;
    private static final int CARD_PAD = 10;
    private static final int SUB_H = 22;
    private static final int SUB_TALL_H = 36;
    private static final int SUB_PAD = 4;
    private static final int CHECK = 9;
    private static final int GROUP_H = 16;
    private static final int TIP_W = 220;
    private static final int MODE_W = 76;
    private static final int MODE_H = 16;

    private static final float TITLE_SCALE = 1.5f;

    private static String selectedCategory = SettingsRegistry.CATEGORIES.get(0);
    private static double scroll;
    private static final Set<String> expanded = new HashSet<>();

    private List<Setting> allSettings = List.of();
    private final List<Row> rows = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private final Set<String> searchOpen = new HashSet<>();
    private String searchOpenQuery = "";
    private int cardsHeight;
    private EditBox search;
    private List<FormattedCharSequence> hoverTip;
    private int widthCap = Integer.MAX_VALUE;

    private NumberSetting numberFocus;
    private int numberBox = -1;
    private String numberText = "";
    private String lastQuery = "";

    private SliderSetting draggingSlider;
    private float dragScale = 1f;
    private float dragOffsetX;
    private int dragTrackX;
    private int dragTrackW;

    private static final int DD_ROW_H = 12;
    private static final int DD_MAX_ROWS = 6;
    private DropdownSetting openDropdown;
    private List<String> ddOptions = List.of();
    private int ddX;
    private int ddY;
    private int ddW;
    private int ddH;
    private double ddScroll;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;

    public QZAScreen() {
        super(Component.literal("QZA"));
    }

    public static void openCategory(String category) {
        if (SettingsRegistry.CATEGORIES.contains(category)) {
            selectedCategory = category;
            scroll = 0;
        }
    }

    private static boolean modern() {
        return ConfigManager.get().newGui;
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
        allSettings = SettingsRegistry.build();
        layout();

        int searchW = Math.min(240, panelW / 4);
        search = new EditBox(this.font,
                modeRect()[0] - searchW - 14, panelY + 18, searchW, 14, Component.empty());
        search.setBordered(false);
        search.setMaxLength(64);
        search.setHint(Component.literal("Search...").withStyle(ChatFormatting.GRAY));
        search.setValue(lastQuery);
        addRenderableWidget(search);

        rebuildRows();
    }

    private void layout() {
        panelX = 8;
        panelY = 6;
        panelW = this.width - 16;
        panelH = this.height - 12;

        contentX = panelX + RAIL_W + 24;
        contentY = panelY + HEADER_H + 8;
        contentW = Math.max(120, (panelX + panelW - 16) - contentX);
        contentH = Math.max(40, (panelY + panelH - 10) - contentY);
    }

    private String query() {
        return search == null ? "" : search.getValue().trim();
    }

    private void rebuildRows() {
        openDropdown = null;
        if (modern()) {
            rebuildCards();
        } else {
            rebuildList();
        }
        clampScroll();
    }

    private void rebuildList() {
        rows.clear();
        String query = query();
        boolean searching = !query.isEmpty();

        String currentSection = null;
        for (Setting setting : allSettings) {
            if (!setting.isVisible()) {
                continue;
            }
            if (searching) {
                if (!setting.matchesSearch(query)) {
                    continue;
                }
            } else if (!setting.category.equals(selectedCategory)) {
                continue;
            }

            String header = searching ? setting.category + " / " + setting.section : setting.section;
            if (!header.equals(currentSection)) {
                currentSection = header;
                rows.add(Row.header(header));
            }
            rows.add(Row.setting(setting, measure(setting)));
        }

        int y = 0;
        for (Row row : rows) {
            row.y = y;
            y += row.height + ROW_GAP;
        }
    }

    private void rebuildCards() {
        cards.clear();
        groups.clear();
        String query = query();
        boolean searching = !query.isEmpty();
        boolean freshSearch = searching && !query.equals(searchOpenQuery);
        if (freshSearch) {
            searchOpen.clear();
        }
        searchOpenQuery = query;

        Map<String, List<Setting>> grouped = new LinkedHashMap<>();
        for (Setting setting : allSettings) {
            if (!searching && !setting.category.equals(selectedCategory)) {
                continue;
            }
            grouped.computeIfAbsent(setting.category + "/" + setting.card(), k -> new ArrayList<>()).add(setting);
        }

        for (Map.Entry<String, List<Setting>> entry : grouped.entrySet()) {
            Card card = Card.of(entry.getKey(), entry.getValue());
            if (!card.visible()) {
                continue;
            }
            if (searching) {
                boolean match = false;
                boolean subMatch = false;
                for (Setting setting : card.all) {
                    if (setting.isVisible() && setting.matchesSearch(query)) {
                        match = true;
                        subMatch |= setting != card.main;
                    }
                }
                if (!match) {
                    continue;
                }
                if (freshSearch && subMatch) {
                    searchOpen.add(card.key);
                }
            }
            card.open = card.expandable() && (searching ? searchOpen : expanded).contains(card.key);
            cards.add(card);
        }

        layoutCards(searching);
    }

    private void layoutCards(boolean searching) {
        int columns = contentW >= (CARD_MIN_W * 2) + CARD_GAP ? 2 : 1;
        int cardW = (contentW - ((columns - 1) * CARD_GAP)) / columns;
        int[] columnY = new int[columns];
        int index = 0;
        String category = null;

        for (Card card : cards) {
            if (searching && !card.category.equals(category)) {
                category = card.category;
                int top = tallest(columnY);
                groups.add(new Group(category, top));
                Arrays.fill(columnY, top + GROUP_H + 4);
                index = 0;
            }
            int column = index % columns;
            index++;
            card.x = contentX + (column * (cardW + CARD_GAP));
            card.w = cardW;
            card.y = columnY[column];
            measureCard(card);
            columnY[column] += card.h + CARD_GAP;
        }
        cardsHeight = cards.isEmpty() ? 0 : tallest(columnY) - CARD_GAP;
    }

    private static int tallest(int[] values) {
        int max = 0;
        for (int value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private void measureCard(Card card) {
        card.subs.clear();
        card.h = CARD_H;
        if (!card.open) {
            return;
        }
        int inner = card.w - (CARD_PAD * 2);
        widthCap = inner;
        int y = CARD_H + SUB_PAD;
        for (Setting setting : card.all) {
            if (setting == card.main || !setting.isVisible()) {
                continue;
            }
            int controlW = setting instanceof ToggleSetting ? CHECK : controlWidth(setting);
            boolean tall = this.font.width(setting.title) + 12 + controlW > inner;
            int h = tall ? SUB_TALL_H : SUB_H;
            card.subs.add(new Sub(setting, y, h, tall));
            y += h;
        }
        widthCap = Integer.MAX_VALUE;
        if (card.subs.isEmpty()) {
            y += SUB_H;
        }
        card.h = y + SUB_PAD;
    }

    private int measure(Setting setting) {
        int textWidth = contentW - controlWidth(setting) - (CONTROL_PAD * 2) - 12;
        int lines = this.font.split(setting.description, Math.max(60, textWidth)).size();
        return Math.max(40, 9 + 11 + (lines * 10) + 8);
    }

    private int controlWidth(Setting setting) {
        if (setting instanceof ToggleSetting) {
            return TOGGLE_W;
        }
        if (setting instanceof SliderSetting slider) {
            return SLIDER_W + sliderLabelReserve(slider);
        }
        if (setting instanceof DropdownSetting dropdown) {
            return dropdownWidth(dropdown) + (dropdown.hasEdit() ? PENCIL_W + 4 : 0);
        }
        if (setting instanceof NumberSetting number) {
            return numberWidth(number);
        }
        if (setting instanceof ActionSetting action && action.buttonWidth > 0) {
            return action.buttonWidth;
        }
        return BUTTON_W;
    }

    private int dropdownWidth(DropdownSetting dropdown) {
        int pencil = dropdown.hasEdit() ? PENCIL_W + 4 : 0;
        return Math.max(40, Math.min(dropdown.width, widthCap - pencil));
    }

    private int numberWidth(NumberSetting number) {
        int width = 0;
        for (int i = 0; i < number.fields.size(); i++) {
            if (i > 0) {
                width += number.separator.isEmpty()
                        ? 6 : this.font.width(number.separator) + 6;
            }
            width += NumberSetting.BOX_W;
            String unit = number.field(i).unit();
            if (!unit.isEmpty()) {
                width += this.font.width(unit) + 4;
            }
        }
        return width;
    }

    private int[] numberBoxRect(NumberSetting number, int right, int y, int h, int index) {
        int x = right - numberWidth(number);
        for (int i = 0; i < number.fields.size(); i++) {
            if (i > 0) {
                x += number.separator.isEmpty()
                        ? 6 : this.font.width(number.separator) + 6;
            }
            if (i == index) {
                return new int[]{x, y + ((h - NumberSetting.BOX_H) / 2),
                        NumberSetting.BOX_W, NumberSetting.BOX_H};
            }
            x += NumberSetting.BOX_W;
            String unit = number.field(i).unit();
            if (!unit.isEmpty()) {
                x += this.font.width(unit) + 4;
            }
        }
        return new int[]{x, y, 0, 0};
    }

    private String numberTextFor(NumberSetting number, int index) {
        if (numberFocus == number && numberBox == index) {
            return numberText;
        }
        return String.valueOf(number.values()[index]);
    }

    private void focusNumber(NumberSetting number, int index) {
        commitNumber();
        numberFocus = number;
        numberBox = index;
        numberText = String.valueOf(number.values()[index]);
    }

    private void commitNumber() {
        if (numberFocus != null) {
            numberFocus.commit(numberBox, numberText);
            ConfigManager.save();
        }
        numberFocus = null;
        numberBox = -1;
        numberText = "";
    }

    private void drawNumberBoxes(GuiGraphicsExtractor graphics, NumberSetting number,
                                 int right, int y, int h, int mouseX, int mouseY) {
        for (int i = 0; i < number.fields.size(); i++) {
            int[] r = numberBoxRect(number, right, y, h, i);
            String text = numberTextFor(number, i);
            boolean focused = numberFocus == number && numberBox == i;
            boolean bad = !number.validFor(i, text);

            graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x66000000);
            int border = bad ? 0xFFE05555
                    : focused ? 0xFFAFD4EC
                    : inside(mouseX, mouseY, r) ? 0xFF8FB4CC : 0xFF6A8CA8;
            outline(graphics, r[0], r[1], r[2], r[3], border);

            String shown = focused ? text + "_" : text;
            graphics.centeredText(this.font, Component.literal(shown),
                    r[0] + (r[2] / 2), r[1] + 4, bad ? 0xFFFF9B9B : TEXT);

            if (i > 0 && !number.separator.isEmpty()) {
                graphics.text(this.font, Component.literal(number.separator),
                        r[0] - this.font.width(number.separator) - 3, r[1] + 4, TEXT_DIM);
            }

            String unit = number.field(i).unit();
            if (!unit.isEmpty()) {
                graphics.text(this.font, Component.literal(unit),
                        r[0] + r[2] + 3, r[1] + 4, TEXT_DIM);
            }
        }
    }

    private int sliderLabelReserve(SliderSetting slider) {
        int atMin = this.font.width(slider.labelFor(slider.min));
        int atMax = this.font.width(slider.labelFor(slider.max));
        return Math.max(atMin, atMax) + 8;
    }

    private int totalHeight() {
        if (modern()) {
            return cardsHeight;
        }
        if (rows.isEmpty()) {
            return 0;
        }
        Row last = rows.get(rows.size() - 1);
        return last.y + last.height;
    }

    private void clampScroll() {
        double max = Math.max(0, totalHeight() - contentH);
        scroll = Math.max(0, Math.min(scroll, max));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!search.getValue().equals(lastQuery)) {
            lastQuery = search.getValue();
            scroll = 0;
            rebuildRows();
        }

        float s = scale();
        int mx = Math.round((mouseX - offsetX()) / s);
        int my = Math.round((mouseY - offsetY()) / s);
        hoverTip = null;

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(graphics, panelX, panelY, panelW, panelH, 0x33FFFFFF);
        graphics.fill(panelX, panelY + HEADER_H, panelX + RAIL_W, panelY + panelH, RAIL_BG);

        super.extractRenderState(graphics, mx, my, delta);

        drawHeader(graphics);
        drawEditGui(graphics, mx, my);
        drawModeToggle(graphics, mx, my);
        drawRail(graphics, mx, my);
        if (modern()) {
            drawCards(graphics, mx, my);
        } else {
            drawContent(graphics, mx, my);
        }

        drawOpenDropdown(graphics, mx, my);

        graphics.pose().popMatrix();

        if (hoverTip != null && openDropdown == null && draggingSlider == null) {
            graphics.setTooltipForNextFrame(this.font, hoverTip, mouseX, mouseY);
        }
    }

    private void scissorLogical(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        graphics.enableScissor(x0, y0, x1, y1);
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    private static void softFill(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x + 1, y, x + w - 1, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    private static void softOutline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x + 2, y, x + w - 2, y + 1, colour);
        graphics.fill(x + 2, y + h - 1, x + w - 2, y + h, colour);
        graphics.fill(x, y + 2, x + 1, y + h - 2, colour);
        graphics.fill(x + w - 1, y + 2, x + w, y + h - 2, colour);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, colour);
        graphics.fill(x + w - 2, y + 1, x + w - 1, y + 2, colour);
        graphics.fill(x + 1, y + h - 2, x + 2, y + h - 1, colour);
        graphics.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, colour);
    }

    private int[] editGuiRect() {
        return new int[]{panelX + 16, panelY + 14, 66, 16};
    }

    private void drawEditGui(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] r = editGuiRect();
        boolean hovered = inside(mouseX, mouseY, r);
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xAA3C5A70 : 0x99223140);
        outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFAFD4EC : 0xFF6A8CA8);
        graphics.centeredText(this.font, Component.literal("Edit GUI"),
                r[0] + (r[2] / 2), r[1] + 4, TEXT);
    }

    private int[] modeRect() {
        return new int[]{panelX + panelW - MODE_W - 16, panelY + 15, MODE_W, MODE_H};
    }

    private void drawModeToggle(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] r = modeRect();
        int half = r[2] / 2;
        boolean hovered = inside(mouseX, mouseY, r);
        boolean modern = modern();

        softFill(graphics, r[0], r[1], r[2], r[3], 0x66000000);
        if (modern) {
            softFill(graphics, r[0] + half, r[1], r[2] - half, r[3], PINK_FILL);
        } else {
            softFill(graphics, r[0], r[1], half, r[3], PINK_FILL);
        }
        softOutline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFDDDDDD : 0x80FFFFFF);

        graphics.centeredText(this.font, Component.literal("OG"), r[0] + (half / 2), r[1] + 4,
                modern ? TEXT_DIM : TEXT);
        graphics.centeredText(this.font, Component.literal("New"), r[0] + half + ((r[2] - half) / 2), r[1] + 4,
                modern ? TEXT : TEXT_DIM);

        if (hovered) {
            hoverTip = this.font.split(Component.literal("Switch between the OG QZA menu and the new one"), TIP_W);
        }
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        int centreX = panelX + (panelW / 2);
        int titleY = panelY + 14;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centreX, titleY);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, Component.literal("QZA"), 0, 0, PINK);
        graphics.pose().popMatrix();

        graphics.fill(search.getX() - 2, search.getY() + 14,
                search.getX() + search.getWidth(), search.getY() + 15, 0x66FFFFFF);

        graphics.fill(panelX + 8, panelY + HEADER_H - 6,
                panelX + panelW - 8, panelY + HEADER_H - 5, DIVIDER);
    }

    private void drawRail(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = panelY + HEADER_H + 14;
        for (String category : SettingsRegistry.CATEGORIES) {
            int x = panelX + 24;
            int textWidth = this.font.width(category);
            int lineWidth = Math.max(textWidth + 16, 108);
            boolean selected = category.equals(selectedCategory) && lastQuery.isEmpty();
            boolean hovered = mouseX >= x - 4 && mouseX <= x + lineWidth
                    && mouseY >= y - 3 && mouseY <= y + 12;

            int colour = selected ? ACCENT : (hovered ? 0xFFEEEEEE : TEXT);
            graphics.text(this.font, Component.literal(category), x, y, colour);
            graphics.fill(x - 4, y + 11, x - 4 + lineWidth, y + 12,
                    selected ? ACCENT : RAIL_LINE);

            y += 32;
        }
    }

    private boolean inContent(double mouseX, double mouseY) {
        return mouseX >= contentX - 4 && mouseX <= contentX + contentW + 4
                && mouseY >= contentY && mouseY <= contentY + contentH;
    }

    private void drawEmpty(GuiGraphicsExtractor graphics) {
        String message = lastQuery.isEmpty()
                ? "No settings in this category yet."
                : "Nothing matches \"" + lastQuery + "\".";
        graphics.centeredText(this.font, Component.literal(message),
                contentX + (contentW / 2), contentY + 16, TEXT_DIM);
    }

    private void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        scissorLogical(graphics, contentX - 4, contentY, contentX + contentW + 4, contentY + contentH);

        if (rows.isEmpty()) {
            drawEmpty(graphics);
            graphics.disableScissor();
            return;
        }

        int offset = contentY - (int) Math.round(scroll);
        for (Row row : rows) {
            int y = offset + row.y;
            if (y + row.height < contentY - 4 || y > contentY + contentH + 4) {
                continue;
            }
            if (row.header != null) {
                drawSectionHeader(graphics, row.header, y);
            } else {
                drawSettingRow(graphics, row, y, mouseX, mouseY);
            }
        }

        graphics.disableScissor();
        drawScrollbar(graphics);
    }

    private void drawSectionHeader(GuiGraphicsExtractor graphics, String header, int y) {
        graphics.fill(contentX, y, contentX + contentW, y + SECTION_H, SECTION_BG);
        graphics.centeredText(this.font, Component.literal(header),
                contentX + (contentW / 2), y + 5, TEXT);
    }

    private int rowRight() {
        return contentX + contentW - CONTROL_PAD;
    }

    private void drawSettingRow(GuiGraphicsExtractor graphics, Row row, int y, int mouseX, int mouseY) {
        Setting setting = row.setting;
        graphics.fill(contentX, y, contentX + contentW, y + row.height, BOX_BG);
        outline(graphics, contentX, y, contentW, row.height, BOX_BORDER);

        graphics.text(this.font, Component.literal(setting.title), contentX + 9, y + 8, TEXT);

        int textWidth = contentW - controlWidth(setting) - (CONTROL_PAD * 2) - 12;
        List<FormattedCharSequence> lines = this.font.split(setting.description, Math.max(60, textWidth));
        int lineY = y + 20;
        for (FormattedCharSequence line : lines) {
            graphics.text(this.font, line, contentX + 9, lineY, TEXT_DIM);
            lineY += 10;
        }

        drawControl(graphics, setting, rowRight(), y, row.height, mouseX, mouseY);
    }

    private void drawControl(GuiGraphicsExtractor graphics, Setting setting, int right, int y, int h,
                             int mouseX, int mouseY) {
        if (setting instanceof ToggleSetting toggle) {
            int[] r = toggleRect(right, y, h);
            drawToggle(graphics, r[0], r[1], toggle.value(), inside(mouseX, mouseY, r));
        } else if (setting instanceof SliderSetting slider) {
            int[] r = sliderRect(right, y, h);
            String label = slider.label();
            graphics.text(this.font, Component.literal(label),
                    r[0] - this.font.width(label) - 6, r[1] + 1, TEXT);
            drawSlider(graphics, r[0], r[1], slider.fraction());
        } else if (setting instanceof DropdownSetting dropdown) {
            int[] r = dropdownRect(setting, right, y, h);
            drawDropdownButton(graphics, r[0], r[1], r[2],
                    dropdown.currentLabel(), inside(mouseX, mouseY, r));
            if (dropdown.hasEdit()) {
                int[] p = pencilRect(setting, right, y, h);
                drawPencil(graphics, p, inside(mouseX, mouseY, p));
            }
        } else if (setting instanceof NumberSetting number) {
            drawNumberBoxes(graphics, number, right, y, h, mouseX, mouseY);
        } else if (setting instanceof ActionSetting action) {
            int[] r = buttonRect(setting, right, y, h);
            drawButton(graphics, r[0], r[1], r[2], action.buttonLabel(), inside(mouseX, mouseY, r));
        }
    }

    private void drawCards(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        scissorLogical(graphics, contentX - 4, contentY, contentX + contentW + 4, contentY + contentH);

        if (cards.isEmpty()) {
            drawEmpty(graphics);
            graphics.disableScissor();
            return;
        }

        boolean pointer = inContent(mouseX, mouseY) && openDropdown == null;
        int offset = contentY - (int) Math.round(scroll);

        for (Group group : groups) {
            int y = offset + group.y;
            if (y + GROUP_H < contentY || y > contentY + contentH) {
                continue;
            }
            graphics.text(this.font, Component.literal(group.category), contentX + 2, y + 4, PINK);
            int lineX = contentX + this.font.width(group.category) + 10;
            graphics.fill(lineX, y + 8, contentX + contentW, y + 9, 0x33FFFFFF);
        }

        for (Card card : cards) {
            int y = offset + card.y;
            if (y + card.h < contentY - 4 || y > contentY + contentH + 4) {
                continue;
            }
            widthCap = card.w - (CARD_PAD * 2);
            drawCard(graphics, card, y, pointer ? mouseX : -1, pointer ? mouseY : -1);
            widthCap = Integer.MAX_VALUE;
        }

        graphics.disableScissor();
        drawScrollbar(graphics);
    }

    private void drawCard(GuiGraphicsExtractor graphics, Card card, int y, int mouseX, int mouseY) {
        int x = card.x;
        int w = card.w;
        boolean on = card.kind == Kind.TOGGLE && ((ToggleSetting) card.main).value();
        boolean overHeader = inside(mouseX, mouseY, new int[]{x, y, w, CARD_H});
        boolean overCard = inside(mouseX, mouseY, new int[]{x, y, w, card.h});

        softFill(graphics, x, y, w, card.h, CARD_BG);
        if (card.open) {
            graphics.fill(x + 1, y + CARD_H, x + w - 1, y + card.h - 1, CARD_BODY);
        }
        int head = on ? (overHeader ? CARD_ON_HOVER : CARD_ON) : (overHeader ? CARD_BG_HOVER : 0);
        if (head != 0) {
            softFill(graphics, x, y, w, card.open ? CARD_H : card.h, head);
        }
        if (card.open) {
            graphics.fill(x + 1, y + CARD_H, x + w - 1, y + CARD_H + 1, on ? 0x66FF55FF : 0x22FFFFFF);
        }
        int border = on ? PINK : (overCard ? CARD_BORDER_HOVER : CARD_BORDER);
        softOutline(graphics, x, y, w, card.h, border);

        int textY = y + ((CARD_H - 8) / 2);
        int rightEdge = x + w - CARD_PAD;
        if (card.kind == Kind.ACTION) {
            String label = ((ActionSetting) card.main).buttonLabel();
            int labelW = this.font.width(label);
            graphics.text(this.font, Component.literal(label), rightEdge - labelW, textY,
                    overHeader ? PINK : TEXT_DIM);
            rightEdge -= labelW + 8;
        } else if (card.expandable()) {
            drawChevron(graphics, rightEdge - 7, y + (CARD_H / 2) - 2, card.open,
                    on ? TEXT : (overHeader ? TEXT : TEXT_DIM));
            rightEdge -= 15;
        }
        graphics.text(this.font, Component.literal(trim(card.title, rightEdge - x - CARD_PAD)),
                x + CARD_PAD, textY, on || overHeader ? TEXT : 0xFFE2E2E2);

        if (overHeader) {
            hoverTip = cardTip(card);
        }

        if (!card.open) {
            return;
        }

        int inner = x + CARD_PAD;
        int right = x + w - CARD_PAD;
        if (card.subs.isEmpty()) {
            String message = card.kind == Kind.TOGGLE && !on
                    ? "Turn it on to see its settings" : "Nothing else to change here";
            graphics.text(this.font, Component.literal(message), inner + 2,
                    y + CARD_H + SUB_PAD + ((SUB_H - 8) / 2), TEXT_FAINT);
            return;
        }

        for (Sub sub : card.subs) {
            int rowY = y + sub.y;
            int[] rowRect = {x + 1, rowY, w - 2, sub.h};
            boolean overRow = inside(mouseX, mouseY, rowRect);
            Setting setting = sub.setting;

            if (setting instanceof ToggleSetting toggle) {
                boolean value = toggle.value();
                if (overRow) {
                    graphics.fill(rowRect[0], rowRect[1], rowRect[0] + rowRect[2], rowRect[1] + rowRect[3], SUB_HOVER);
                }
                int labelY = rowY + ((sub.h - 8) / 2);
                graphics.text(this.font, Component.literal(trim(setting.title, right - inner - CHECK - 8)),
                        inner + 2, labelY, value ? PINK : (overRow ? TEXT : 0xFFCCCCCC));
                drawCheck(graphics, right - CHECK, rowY + ((sub.h - CHECK) / 2), value, overRow);
                if (overRow) {
                    hoverTip = this.font.split(setting.description, TIP_W);
                }
                continue;
            }

            int labelY = sub.tall ? rowY + 4 : rowY + ((sub.h - 8) / 2);
            graphics.text(this.font, Component.literal(trim(setting.title, right - inner)),
                    inner + 2, labelY, 0xFFCCCCCC);
            int controlY = sub.tall ? rowY + 13 : rowY;
            int controlH = sub.tall ? sub.h - 13 : sub.h;
            drawControl(graphics, setting, right, controlY, controlH, mouseX, mouseY);

            int labelW = this.font.width(setting.title);
            if (inside(mouseX, mouseY, new int[]{inner, labelY - 2, labelW + 4, 12})) {
                hoverTip = this.font.split(setting.description, TIP_W);
            }
        }
    }

    private List<FormattedCharSequence> cardTip(Card card) {
        if (card.main == null) {
            return null;
        }
        List<FormattedCharSequence> lines = new ArrayList<>(this.font.split(card.main.description, TIP_W));
        if (card.kind == Kind.TOGGLE && card.expandable()) {
            lines.addAll(this.font.split(Component.literal(card.open
                    ? "Right click to hide its settings" : "Right click for its settings")
                    .withStyle(ChatFormatting.DARK_GRAY), TIP_W));
        }
        return lines;
    }

    private static void drawChevron(GuiGraphicsExtractor graphics, int x, int y, boolean up, int colour) {
        for (int i = 0; i < 4; i++) {
            int row = up ? y + 3 - i : y + i;
            graphics.fill(x + i, row, x + 7 - i, row + 1, colour);
        }
    }

    private static void drawCheck(GuiGraphicsExtractor graphics, int x, int y, boolean on, boolean hovered) {
        if (on) {
            softFill(graphics, x, y, CHECK, CHECK, PINK);
            return;
        }
        softFill(graphics, x, y, CHECK, CHECK, 0x66000000);
        softOutline(graphics, x, y, CHECK, CHECK, hovered ? 0xFFDDDDDD : 0x80FFFFFF);
    }

    private void drawToggle(GuiGraphicsExtractor graphics, int x, int y, boolean on, boolean hovered) {
        int track = on ? 0xFF2E7D5B : 0xFF474747;
        graphics.fill(x, y + 1, x + TOGGLE_W, y + TOGGLE_H - 1, track);
        graphics.fill(x + 1, y, x + TOGGLE_W - 1, y + 1, track);
        graphics.fill(x + 1, y + TOGGLE_H - 1, x + TOGGLE_W - 1, y + TOGGLE_H, track);

        int knobX = on ? x + TOGGLE_W - TOGGLE_H + 1 : x + 1;
        int knob = on ? 0xFF8CF5C4 : (hovered ? 0xFFE4E4E4 : 0xFFB4B4B4);
        graphics.fill(knobX, y + 1, knobX + TOGGLE_H - 2, y + TOGGLE_H - 1, knob);
    }

    private void drawSlider(GuiGraphicsExtractor graphics, int x, int y, double fraction) {
        int midY = y + (SLIDER_H / 2);
        graphics.fill(x, midY - 1, x + SLIDER_W, midY + 1, 0xFF474747);
        int handleX = x + (int) Math.round((SLIDER_W - 6) * fraction);
        graphics.fill(x, midY - 1, handleX + 3, midY + 1, modern() ? PINK : ACCENT);
        graphics.fill(handleX, y, handleX + 6, y + SLIDER_H, 0xFFE8E8E8);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w,
                            String label, boolean hovered) {
        graphics.fill(x, y, x + w, y + BUTTON_H, hovered ? 0xAA3C5A70 : 0x99223140);
        outline(graphics, x, y, w, BUTTON_H, hovered ? 0xFFAFD4EC : 0xFF6A8CA8);
        graphics.centeredText(this.font, Component.literal(trim(label, w - 8)),
                x + (w / 2), y + ((BUTTON_H - 8) / 2), TEXT);
    }

    private void drawDropdownButton(GuiGraphicsExtractor graphics, int x, int y, int w,
                                    String label, boolean hovered) {
        boolean open = openDropdown != null;
        graphics.fill(x, y, x + w, y + BUTTON_H, hovered || open ? 0xAA3C5A70 : 0x99223140);
        outline(graphics, x, y, w, BUTTON_H, hovered || open ? 0xFFAFD4EC : 0xFF6A8CA8);

        graphics.text(this.font, Component.literal(trim(label, w - 20)),
                x + 6, y + ((BUTTON_H - 8) / 2), TEXT);

        int ax = x + w - 12;
        int ay = y + (BUTTON_H / 2) - 2;
        graphics.fill(ax, ay, ax + 7, ay + 1, TEXT);
        graphics.fill(ax + 1, ay + 1, ax + 6, ay + 2, TEXT);
        graphics.fill(ax + 2, ay + 2, ax + 5, ay + 3, TEXT);
        graphics.fill(ax + 3, ay + 3, ax + 4, ay + 4, TEXT);
    }

    private String trim(String label, int maxWidth) {
        if (this.font.width(label) <= maxWidth) {
            return label;
        }
        String shown = label;
        while (shown.length() > 1 && this.font.width(shown + "...") > maxWidth) {
            shown = shown.substring(0, shown.length() - 1);
        }
        return shown + "...";
    }

    private boolean inDropdown(double mouseX, double mouseY) {
        return openDropdown != null
                && mouseX >= ddX && mouseX <= ddX + ddW
                && mouseY >= ddY && mouseY <= ddY + ddH;
    }

    private int ddContentHeight() {
        return ddOptions.size() * DD_ROW_H;
    }

    private void clampDropdownScroll() {
        double max = Math.max(0, ddContentHeight() - (ddH - 2));
        ddScroll = Math.max(0, Math.min(ddScroll, max));
    }

    private void openDropdownAt(DropdownSetting dropdown, int[] rect) {
        dropdown.notifyOpen();
        List<String> options = dropdown.options();
        if (options.isEmpty()) {
            return;
        }
        ddOptions = new ArrayList<>(options);
        openDropdown = dropdown;
        ddScroll = 0;
        ddW = Math.max(rect[2], 120);
        ddH = Math.min(ddOptions.size(), DD_MAX_ROWS) * DD_ROW_H + 2;
        ddX = rect[0];
        ddY = rect[1] + rect[3] + 1;

        if (ddY + ddH > panelY + panelH - 4) {
            ddY = rect[1] - ddH - 1;
        }
    }

    private void drawOpenDropdown(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (openDropdown == null) {
            return;
        }
        graphics.fill(ddX, ddY, ddX + ddW, ddY + ddH, 0xF00E1218);
        outline(graphics, ddX, ddY, ddW, ddH, 0xFFAFD4EC);

        String current = openDropdown.value();
        boolean scrollable = ddContentHeight() > ddH - 2;
        int textRoom = ddW - 10 - (scrollable ? 5 : 0);

        scissorLogical(graphics, ddX + 1, ddY + 1, ddX + ddW - 1, ddY + ddH - 1);
        int top = ddY + 1 - (int) Math.round(ddScroll);
        for (int i = 0; i < ddOptions.size(); i++) {
            int rowY = top + (i * DD_ROW_H);
            if (rowY + DD_ROW_H < ddY || rowY > ddY + ddH) {
                continue;
            }
            String option = ddOptions.get(i);
            boolean hovered = mouseX >= ddX && mouseX <= ddX + ddW
                    && mouseY >= rowY && mouseY < rowY + DD_ROW_H;
            boolean selected = option.equals(current);

            if (hovered) {
                graphics.fill(ddX + 1, rowY, ddX + ddW - 1, rowY + DD_ROW_H, 0x663C5A70);
            }
            int colour = selected ? (modern() ? PINK : ACCENT) : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
            graphics.text(this.font,
                    Component.literal(trim(openDropdown.display(option), textRoom)),
                    ddX + 5, rowY + 2, colour);
        }
        graphics.disableScissor();

        if (scrollable) {
            int trackX = ddX + ddW - 4;
            int trackTop = ddY + 1;
            int trackH = ddH - 2;
            graphics.fill(trackX, trackTop, trackX + 3, trackTop + trackH, 0x33FFFFFF);
            int barH = Math.max(8, (int) ((float) trackH / ddContentHeight() * trackH));
            double max = Math.max(1, ddContentHeight() - trackH);
            int barY = trackTop + (int) ((ddScroll / max) * (trackH - barH));
            graphics.fill(trackX, barY, trackX + 3, barY + barH, 0xAAFFFFFF);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int total = totalHeight();
        if (total <= contentH) {
            return;
        }
        int trackX = contentX + contentW + 2;
        graphics.fill(trackX, contentY, trackX + 3, contentY + contentH, 0x33FFFFFF);
        int barH = Math.max(16, (int) ((float) contentH / total * contentH));
        int barY = contentY + (int) ((scroll / (total - contentH)) * (contentH - barH));
        graphics.fill(trackX, barY, trackX + 3, barY + barH, 0x99FFFFFF);
    }

    private int[] toggleRect(int right, int y, int h) {
        return new int[]{right - TOGGLE_W, y + ((h - TOGGLE_H) / 2), TOGGLE_W, TOGGLE_H};
    }

    private int[] sliderRect(int right, int y, int h) {
        return new int[]{right - SLIDER_W, y + ((h - SLIDER_H) / 2), SLIDER_W, SLIDER_H};
    }

    private int[] buttonRect(Setting setting, int right, int y, int h) {
        int w = controlWidth(setting);
        return new int[]{right - w, y + ((h - BUTTON_H) / 2), w, BUTTON_H};
    }

    private int[] dropdownRect(Setting setting, int right, int y, int h) {
        int[] r = buttonRect(setting, right, y, h);
        if (setting instanceof DropdownSetting dropdown && dropdown.hasEdit()) {
            return new int[]{r[0] + PENCIL_W + 4, r[1], dropdownWidth(dropdown), r[3]};
        }
        return r;
    }

    private int[] pencilRect(Setting setting, int right, int y, int h) {
        int[] r = buttonRect(setting, right, y, h);
        return new int[]{r[0], r[1], PENCIL_W, r[3]};
    }

    private void drawPencil(GuiGraphicsExtractor graphics, int[] r, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                hovered ? 0xAA3C5A70 : 0x99223140);
        outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFAFD4EC : 0xFF6A8CA8);

        int colour = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;
        int x = r[0] + 4;
        int y = r[1] + r[3] - 5;

        for (int i = 0; i < 6; i++) {
            graphics.fill(x + i, y - i, x + i + 2, y - i + 2, colour);
        }
        graphics.fill(x, y + 1, x + 2, y + 3, PINK);
    }

    private static boolean inside(double mouseX, double mouseY, int[] rect) {
        return mouseX >= rect[0] && mouseX <= rect[0] + rect[2]
                && mouseY >= rect[1] && mouseY <= rect[1] + rect[3];
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

        if (openDropdown != null) {
            if (inDropdown(local.x(), local.y())) {
                int index = (int) ((local.y() - (ddY + 1) + ddScroll) / DD_ROW_H);
                if (index >= 0 && index < ddOptions.size()) {
                    openDropdown.select(ddOptions.get(index));
                    rebuildRows();
                }
            }
            openDropdown = null;
            return true;
        }

        if (super.mouseClicked(local, doubleClick)) {
            return true;
        }
        boolean left = local.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = local.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (!left && !(right && modern())) {
            return false;
        }

        double mouseX = local.x();
        double mouseY = local.y();

        if (left && inside(mouseX, mouseY, modeRect())) {
            int[] r = modeRect();
            boolean pickNew = mouseX >= r[0] + (r[2] / 2);
            if (pickNew != modern()) {
                commitNumber();
                ConfigManager.get().newGui = pickNew;
                ConfigManager.save();
                scroll = 0;
                rebuildRows();
            }
            return true;
        }

        if (left && inside(mouseX, mouseY, editGuiRect())) {
            ConfigManager.save();
            Mc.setScreen(new GuiEditScreen());
            return true;
        }

        if (left) {
            int railY = panelY + HEADER_H + 14;
            for (String category : SettingsRegistry.CATEGORIES) {
                int x = panelX + 24;
                int lineWidth = Math.max(this.font.width(category) + 16, 108);
                if (mouseX >= x - 4 && mouseX <= x - 4 + lineWidth
                        && mouseY >= railY - 3 && mouseY <= railY + 12) {
                    selectedCategory = category;
                    search.setValue("");
                    lastQuery = "";
                    scroll = 0;
                    rebuildRows();
                    return true;
                }
                railY += 32;
            }
        }

        if (!inContent(mouseX, mouseY)) {
            return false;
        }

        if (modern()) {
            if (clickCards(mouseX, mouseY, left)) {
                return true;
            }
            commitNumber();
            return false;
        }

        int offset = contentY - (int) Math.round(scroll);
        for (Row row : rows) {
            if (row.header != null) {
                continue;
            }
            int y = offset + row.y;
            if (mouseY < y || mouseY > y + row.height) {
                continue;
            }
            if (clickControl(row.setting, rowRight(), y, row.height, mouseX, mouseY)) {
                return true;
            }
            break;
        }

        commitNumber();
        return false;
    }

    private boolean clickControl(Setting setting, int right, int y, int h, double mouseX, double mouseY) {
        if (setting instanceof ToggleSetting toggle) {
            if (inside(mouseX, mouseY, toggleRect(right, y, h))) {
                toggle.toggle();
                rebuildRows();
                return true;
            }
        } else if (setting instanceof SliderSetting slider) {
            int[] r = sliderRect(right, y, h);
            if (mouseX >= r[0] - 2 && mouseX <= r[0] + r[2] + 2
                    && mouseY >= r[1] - 5 && mouseY <= r[1] + r[3] + 5) {
                draggingSlider = slider;
                dragScale = scale();
                dragOffsetX = offsetX();
                dragTrackX = r[0];
                dragTrackW = r[2];
                slider.setFromFraction((mouseX - r[0]) / (double) r[2]);
                return true;
            }
        } else if (setting instanceof DropdownSetting dropdown) {
            if (dropdown.hasEdit() && inside(mouseX, mouseY, pencilRect(setting, right, y, h))) {
                dropdown.edit();
                return true;
            }
            int[] r = dropdownRect(setting, right, y, h);
            if (inside(mouseX, mouseY, r)) {
                openDropdownAt(dropdown, r);
                return true;
            }
        } else if (setting instanceof NumberSetting number) {
            for (int i = 0; i < number.fields.size(); i++) {
                if (inside(mouseX, mouseY, numberBoxRect(number, right, y, h, i))) {
                    focusNumber(number, i);
                    return true;
                }
            }
        } else if (setting instanceof ActionSetting action) {
            if (inside(mouseX, mouseY, buttonRect(setting, right, y, h))) {
                action.run();
                return true;
            }
        }
        return false;
    }

    private boolean clickCards(double mouseX, double mouseY, boolean left) {
        int offset = contentY - (int) Math.round(scroll);
        for (Card card : cards) {
            int y = offset + card.y;
            if (!inside(mouseX, mouseY, new int[]{card.x, y, card.w, card.h})) {
                continue;
            }

            if (mouseY <= y + CARD_H) {
                commitNumber();
                if (!left || card.kind == Kind.GROUP) {
                    if (card.expandable()) {
                        toggleOpen(card);
                    }
                } else if (card.kind == Kind.TOGGLE) {
                    ((ToggleSetting) card.main).toggle();
                    rebuildRows();
                } else {
                    ((ActionSetting) card.main).run();
                }
                return true;
            }

            if (!left) {
                return true;
            }
            int right = card.x + card.w - CARD_PAD;
            for (Sub sub : card.subs) {
                int rowY = y + sub.y;
                if (mouseY < rowY || mouseY > rowY + sub.h) {
                    continue;
                }
                if (sub.setting instanceof ToggleSetting toggle) {
                    commitNumber();
                    toggle.toggle();
                    rebuildRows();
                    return true;
                }
                int controlY = sub.tall ? rowY + 13 : rowY;
                int controlH = sub.tall ? sub.h - 13 : sub.h;
                widthCap = card.w - (CARD_PAD * 2);
                boolean handled = clickControl(sub.setting, right, controlY, controlH, mouseX, mouseY);
                widthCap = Integer.MAX_VALUE;
                if (handled) {
                    return true;
                }
                break;
            }
            commitNumber();
            return true;
        }
        return false;
    }

    private void toggleOpen(Card card) {
        Set<String> open = query().isEmpty() ? expanded : searchOpen;
        if (!open.remove(card.key)) {
            open.add(card.key);
        }
        rebuildRows();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingSlider != null) {
            double logicalX = (event.x() - dragOffsetX) / dragScale;
            draggingSlider.setFromFraction((logicalX - dragTrackX) / (double) dragTrackW);
            return true;
        }
        return super.mouseDragged(toLogical(event), deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSlider != null) {
            draggingSlider = null;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(toLogical(event));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float s = scale();
        double mx = (mouseX - offsetX()) / s;
        double my = (mouseY - offsetY()) / s;

        if (openDropdown != null) {
            if (inDropdown(mx, my)) {
                ddScroll -= verticalAmount * DD_ROW_H;
                clampDropdownScroll();
                return true;
            }
            openDropdown = null;
        }

        if (mx >= contentX - 4 && mx <= contentX + contentW + 8
                && my >= contentY && my <= contentY + contentH) {
            scroll -= verticalAmount * 18;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ChatKeybind.capturing()) {
            ChatKeybind.capture(event.key());
            ChatKeybind.swallowNextChar();
            return true;
        }

        if (numberFocus != null) {
            if (event.key() == InputConstants.KEY_BACKSPACE) {
                if (!numberText.isEmpty()) {
                    numberText = numberText.substring(0, numberText.length() - 1);
                    numberFocus.commit(numberBox, numberText);
                    ConfigManager.save();
                }
                return true;
            }
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                commitNumber();
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE) {

                numberFocus = null;
                numberBox = -1;
                numberText = "";
                return true;
            }
            if (event.key() == InputConstants.KEY_TAB) {
                NumberSetting current = numberFocus;
                int next = (numberBox + 1) % current.fields.size();
                focusNumber(current, next);
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (numberFocus != null) {
            int typed = event.codepoint();
            if (typed >= '0' && typed <= '9') {
                if (numberText.length() < numberFocus.field(numberBox).digits()) {

                    String base = "0".equals(numberText) ? "" : numberText;
                    numberText = base + (char) typed;
                    numberFocus.commit(numberBox, numberText);
                    ConfigManager.save();
                }
                return true;
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        ChatKeybind.cancel();
        ConfigManager.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Kind {
        TOGGLE,
        ACTION,
        GROUP
    }

    private record Sub(Setting setting, int y, int h, boolean tall) {
    }

    private record Group(String category, int y) {
    }

    private static final class Card {
        final String key;
        final String category;
        final String title;
        final Kind kind;
        final Setting main;
        final List<Setting> all;
        final List<Sub> subs = new ArrayList<>();
        boolean open;
        int x;
        int y;
        int w;
        int h;

        private Card(String key, String category, String title, Kind kind, Setting main, List<Setting> all) {
            this.key = key;
            this.category = category;
            this.title = title;
            this.kind = kind;
            this.main = main;
            this.all = all;
        }

        static Card of(String key, List<Setting> all) {
            Setting first = all.get(0);
            Kind kind;
            if (first instanceof ToggleSetting) {
                kind = Kind.TOGGLE;
            } else if (all.size() == 1 && first instanceof ActionSetting) {
                kind = Kind.ACTION;
            } else {
                kind = Kind.GROUP;
            }
            String title = kind == Kind.ACTION && !first.namesCard() ? first.title : first.card();
            return new Card(key, first.category, title, kind, kind == Kind.GROUP ? null : first, all);
        }

        boolean visible() {
            if (main != null) {
                return main.isVisible();
            }
            for (Setting setting : all) {
                if (setting.isVisible()) {
                    return true;
                }
            }
            return false;
        }

        boolean expandable() {
            return all.size() > (main == null ? 0 : 1);
        }
    }

    private static final class Row {
        final String header;
        final Setting setting;
        int height;
        int y;

        private Row(String header, Setting setting, int height) {
            this.header = header;
            this.setting = setting;
            this.height = height;
        }

        static Row header(String text) {
            return new Row(text, null, SECTION_H);
        }

        static Row setting(Setting setting, int height) {
            return new Row(null, setting, height);
        }
    }
}
