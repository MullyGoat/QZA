package com.qza.gui;

import com.qza.config.ConfigManager;
import com.qza.gui.setting.ActionSetting;
import com.qza.gui.setting.DropdownSetting;
import com.qza.gui.setting.Setting;
import com.qza.gui.setting.SettingsRegistry;
import com.qza.gui.setting.SliderSetting;
import com.qza.gui.setting.ToggleSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The /qza settings screen: pink QZA header with a search box, category rail on
 * the left, scrolling section/setting list on the right.
 *
 * Minecraft 26.x uses a retained-mode GUI pipeline: screens describe themselves
 * into a GuiGraphicsExtractor via extractRenderState() rather than drawing
 * immediately. Draw calls are named fill / text / centeredText.
 *
 * GUI Scale (Miscellaneous tab) shrinks the *entire* window, outer panel
 * included, about the screen centre. Layout is always computed in 100%-scale
 * logical pixels and the pose matrix does the shrinking, so row wrapping never
 * changes as you drag the slider.
 */
public class QZAScreen extends Screen {

    // ---- palette -----------------------------------------------------------
    private static final int PANEL_BG = 0x55000000;
    private static final int RAIL_BG = 0x33000000;
    private static final int BOX_BG = 0x66000000;
    private static final int BOX_BORDER = 0x40FFFFFF;
    private static final int SECTION_BG = 0x59000000;
    private static final int DIVIDER = 0xFFC8D4DC;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int ACCENT = 0xFF55FFFF;
    private static final int RAIL_LINE = 0xFF9AA5AC;
    private static final int PINK = 0xFFFF55FF;

    // ---- layout ------------------------------------------------------------
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
    /** Multiplier for the "QZA" title relative to the normal font. */
    private static final float TITLE_SCALE = 1.5f;

    /** Remembered across openings so /qza returns you where you were. */
    private static String selectedCategory = SettingsRegistry.CATEGORIES.get(0);
    private static double scroll;

    private List<Setting> allSettings = List.of();
    private final List<Row> rows = new ArrayList<>();
    private EditBox search;
    private String lastQuery = "";

    /**
     * Drag state. The coordinate transform is frozen when the grab starts:
     * the GUI Scale slider changes the very space the drag is measured in, so
     * reading it live makes the slider fight itself and slam to an extreme.
     */
    private SliderSetting draggingSlider;
    private float dragScale = 1f;
    private float dragOffsetX;
    private int dragTrackX;
    private int dragTrackW;

    // ---- open dropdown state (drawn on top of everything, own scroll) ------
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

    /** GUI Scale as a multiplier. */
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
                panelX + panelW - searchW - 16, panelY + 18, searchW, 14, Component.empty());
        search.setBordered(false);
        search.setMaxLength(64);
        search.setHint(Component.literal("Search...").withStyle(ChatFormatting.GRAY));
        search.setValue(lastQuery);
        addRenderableWidget(search);

        rebuildRows();
    }

    // ------------------------------------------------------------------ layout

    /**
     * Panel rectangles in logical pixels. Deliberately independent of GUI
     * Scale -- the matrix shrinks the result, so the outer panel scales with
     * everything else and text wrapping stays put.
     */
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

    private void rebuildRows() {
        // Row positions are about to move; an anchored dropdown would desync.
        openDropdown = null;
        rows.clear();
        String query = search == null ? "" : search.getValue().trim();
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

            // While searching, headers read "Category / Section" so results stay
            // identifiable across categories.
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
        clampScroll();
    }

    private int measure(Setting setting) {
        int textWidth = contentW - controlWidth(setting) - (CONTROL_PAD * 2) - 12;
        int lines = this.font.split(setting.description, Math.max(60, textWidth)).size();
        return Math.max(40, 9 + 11 + (lines * 10) + 8);
    }

    /**
     * Horizontal space a row's control needs, so description text can be
     * wrapped clear of it. For sliders that includes the value label drawn to
     * the left of the track, sized for the widest value the slider can show --
     * a fixed reserve, so the wrap does not jitter as the value changes.
     */
    private int controlWidth(Setting setting) {
        if (setting instanceof ToggleSetting) {
            return TOGGLE_W;
        }
        if (setting instanceof SliderSetting slider) {
            return SLIDER_W + sliderLabelReserve(slider);
        }
        if (setting instanceof DropdownSetting dropdown) {
            return dropdown.width;
        }
        if (setting instanceof ActionSetting action && action.buttonWidth > 0) {
            return action.buttonWidth;
        }
        return BUTTON_W;
    }

    private int sliderLabelReserve(SliderSetting slider) {
        int atMin = this.font.width(slider.labelFor(slider.min));
        int atMax = this.font.width(slider.labelFor(slider.max));
        return Math.max(atMin, atMax) + 8;
    }

    private int totalHeight() {
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

    // ------------------------------------------------------------------ render

    /** Keeps the world visible behind the panels instead of blurring it. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // intentionally empty
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

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(graphics, panelX, panelY, panelW, panelH, 0x33FFFFFF);
        graphics.fill(panelX, panelY + HEADER_H, panelX + RAIL_W, panelY + panelH, RAIL_BG);

        // Draws the child widgets, i.e. the search box.
        super.extractRenderState(graphics, mx, my, delta);

        drawHeader(graphics);
        drawRail(graphics, mx, my);
        drawContent(graphics, mx, my);
        // Last, and outside the content scissor, so it floats over the rows.
        drawOpenDropdown(graphics, mx, my);

        graphics.pose().popMatrix();
    }

    /**
     * enableScissor DOES honour the pose matrix -- verified in game: converting
     * to screen pixels by hand applied the scale and offset twice and clipped
     * the content to a narrow band on the right. Pass logical coordinates.
     */
    private void scissorLogical(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        graphics.enableScissor(x0, y0, x1, y1);
    }

    /** 26.x has no renderOutline, so borders are four thin fills. */
    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        // The font has one size, so the title is scaled by its own nested matrix.
        int centreX = panelX + (panelW / 2);
        int titleY = panelY + 14;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centreX, titleY);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, Component.literal("QZA"), 0, 0, PINK);
        graphics.pose().popMatrix();

        // underline beneath the search box
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

    private void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        scissorLogical(graphics, contentX - 4, contentY, contentX + contentW + 4, contentY + contentH);

        if (rows.isEmpty()) {
            String message = lastQuery.isEmpty()
                    ? "No settings in this category yet."
                    : "Nothing matches \"" + lastQuery + "\".";
            graphics.centeredText(this.font, Component.literal(message),
                    contentX + (contentW / 2), contentY + 16, TEXT_DIM);
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

        if (setting instanceof ToggleSetting toggle) {
            int[] r = toggleRect(row, y);
            drawToggle(graphics, r[0], r[1], toggle.value(), inside(mouseX, mouseY, r));
        } else if (setting instanceof SliderSetting slider) {
            int[] r = sliderRect(row, y);
            String label = slider.label();
            graphics.text(this.font, Component.literal(label),
                    r[0] - this.font.width(label) - 6, r[1] + 1, TEXT);
            drawSlider(graphics, r[0], r[1], slider.fraction());
        } else if (setting instanceof DropdownSetting dropdown) {
            int[] r = buttonRect(row, y);
            drawDropdownButton(graphics, r[0], r[1], r[2],
                    dropdown.currentLabel(), inside(mouseX, mouseY, r));
        } else if (setting instanceof ActionSetting action) {
            int[] r = buttonRect(row, y);
            drawButton(graphics, r[0], r[1], r[2], action.buttonLabel(), inside(mouseX, mouseY, r));
        }
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
        graphics.fill(x, midY - 1, handleX + 3, midY + 1, ACCENT);
        graphics.fill(handleX, y, handleX + 6, y + SLIDER_H, 0xFFE8E8E8);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w,
                            String label, boolean hovered) {
        graphics.fill(x, y, x + w, y + BUTTON_H, hovered ? 0xAA3C5A70 : 0x99223140);
        outline(graphics, x, y, w, BUTTON_H, hovered ? 0xFFAFD4EC : 0xFF6A8CA8);
        graphics.centeredText(this.font, Component.literal(trim(label, w - 8)),
                x + (w / 2), y + ((BUTTON_H - 8) / 2), TEXT);
    }

    /** Closed dropdown: label left-aligned, little triangle on the right. */
    private void drawDropdownButton(GuiGraphicsExtractor graphics, int x, int y, int w,
                                    String label, boolean hovered) {
        boolean open = openDropdown != null;
        graphics.fill(x, y, x + w, y + BUTTON_H, hovered || open ? 0xAA3C5A70 : 0x99223140);
        outline(graphics, x, y, w, BUTTON_H, hovered || open ? 0xFFAFD4EC : 0xFF6A8CA8);

        graphics.text(this.font, Component.literal(trim(label, w - 20)),
                x + 6, y + ((BUTTON_H - 8) / 2), TEXT);

        // Drawn from fills rather than a glyph, so no font coverage worries.
        int ax = x + w - 12;
        int ay = y + (BUTTON_H / 2) - 2;
        graphics.fill(ax, ay, ax + 7, ay + 1, TEXT);
        graphics.fill(ax + 1, ay + 1, ax + 6, ay + 2, TEXT);
        graphics.fill(ax + 2, ay + 2, ax + 5, ay + 3, TEXT);
        graphics.fill(ax + 3, ay + 3, ax + 4, ay + 4, TEXT);
    }

    /** Truncates with an ellipsis to fit maxWidth logical pixels. */
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

    // ------------------------------------------------------------------ dropdown

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
            return; // nothing to choose; the button already reads "(no music)"
        }
        ddOptions = new ArrayList<>(options);
        openDropdown = dropdown;
        ddScroll = 0;
        ddW = Math.max(rect[2], 120);
        ddH = Math.min(ddOptions.size(), DD_MAX_ROWS) * DD_ROW_H + 2;
        ddX = rect[0];
        ddY = rect[1] + rect[3] + 1;
        // Flip above the button if it would spill out of the panel.
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
            int colour = selected ? ACCENT : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
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

    // ------------------------------------------------------------------ hit boxes

    private int[] toggleRect(Row row, int y) {
        int x = contentX + contentW - CONTROL_PAD - TOGGLE_W;
        return new int[]{x, y + ((row.height - TOGGLE_H) / 2), TOGGLE_W, TOGGLE_H};
    }

    private int[] sliderRect(Row row, int y) {
        int x = contentX + contentW - CONTROL_PAD - SLIDER_W;
        return new int[]{x, y + ((row.height - SLIDER_H) / 2), SLIDER_W, SLIDER_H};
    }

    private int[] buttonRect(Row row, int y) {
        int w = controlWidth(row.setting);
        int x = contentX + contentW - CONTROL_PAD - w;
        return new int[]{x, y + ((row.height - BUTTON_H) / 2), w, BUTTON_H};
    }

    private static boolean inside(double mouseX, double mouseY, int[] rect) {
        return mouseX >= rect[0] && mouseX <= rect[0] + rect[2]
                && mouseY >= rect[1] && mouseY <= rect[1] + rect[3];
    }

    // ------------------------------------------------------------------ input

    /**
     * MouseButtonEvent is a record, so a logical-space copy can be handed to
     * child widgets -- otherwise the search box hitbox would drift from where
     * it is drawn once GUI Scale is not 100%.
     */
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

        // An open dropdown is on top and eats the click, whether it lands on an
        // option or outside (which just closes it).
        if (openDropdown != null) {
            if (inDropdown(local.x(), local.y())) {
                int index = (int) ((local.y() - (ddY + 1) + ddScroll) / DD_ROW_H);
                if (index >= 0 && index < ddOptions.size()) {
                    openDropdown.select(ddOptions.get(index));
                }
            }
            openDropdown = null;
            return true;
        }

        if (super.mouseClicked(local, doubleClick)) {
            return true;
        }
        if (local.button() != 0) {
            return false;
        }

        double mouseX = local.x();
        double mouseY = local.y();

        // Category rail
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

        // Settings list
        if (mouseX < contentX - 4 || mouseX > contentX + contentW + 4
                || mouseY < contentY || mouseY > contentY + contentH) {
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

            if (row.setting instanceof ToggleSetting toggle) {
                if (inside(mouseX, mouseY, toggleRect(row, y))) {
                    toggle.toggle();
                    // A toggle can reveal or hide other rows.
                    rebuildRows();
                    return true;
                }
            } else if (row.setting instanceof SliderSetting slider) {
                int[] r = sliderRect(row, y);
                // Generous vertical grab area -- the track itself is only 10px.
                if (mouseX >= r[0] - 2 && mouseX <= r[0] + r[2] + 2
                        && mouseY >= r[1] - 5 && mouseY <= r[1] + r[3] + 5) {
                    draggingSlider = slider;
                    // Freeze the transform and the track position for this drag.
                    dragScale = scale();
                    dragOffsetX = offsetX();
                    dragTrackX = r[0];
                    dragTrackW = r[2];
                    slider.setFromFraction((mouseX - r[0]) / (double) r[2]);
                    return true;
                }
            } else if (row.setting instanceof DropdownSetting dropdown) {
                int[] r = buttonRect(row, y);
                if (inside(mouseX, mouseY, r)) {
                    openDropdownAt(dropdown, r);
                    return true;
                }
            } else if (row.setting instanceof ActionSetting action) {
                if (inside(mouseX, mouseY, buttonRect(row, y))) {
                    action.run();
                    return true;
                }
            }
            break;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingSlider != null) {
            // Uses the transform captured at grab time, so adjusting GUI Scale
            // does not move the track out from under the cursor.
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

        // The dropdown scrolls independently while it is open.
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
    public void onClose() {
        ConfigManager.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ row model

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
