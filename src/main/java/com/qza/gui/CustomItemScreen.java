package com.qza.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.CustomItemEntry;
import com.qza.config.QZAConfig;
import com.qza.custom.CustomItems;
import com.qza.inventory.ItemProtection;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomItemScreen extends Screen {
    private static final int PANEL_BG = 0x55000000;
    private static final int CARD_BG = 0x33000000;
    private static final int DIVIDER = 0xFFC8D4DC;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int PINK = 0xFFFF55FF;
    private static final int PINK_FILL = 0x99FF55FF;
    private static final int RED = 0xFFFF7777;

    private static final float TITLE_SCALE = 1.5f;
    private static final int HEADER_H = 52;
    private static final int FIELD_H = 16;
    private static final int ROW_H = 26;
    private static final int OPTION_H = 28;
    private static final int GAP = 4;
    private static final int SETTINGS_W = 70;
    private static final int REMOVE_W = 56;
    private static final int PICK_W = 96;
    private static final int TOGGLE_W = 132;
    private static final int OPTION_W = 120;
    private static final int PREVIEW = 64;
    private static final long RESCAN_MS = 500L;

    private static final int TYPE = 0;
    private static final int LOOK = 1;
    private static final int COLOUR = 2;
    private static final int EQUIP = 3;
    private static final int GLOW = 4;
    private static final int BLOCK = 5;
    private static final int REMOVE = 6;

    private enum Mode {
        LIST,
        ADD,
        EDIT
    }

    private final List<Row> rows = new ArrayList<>();
    private List<ItemStack> inventory = List.of();
    private Mode mode = Mode.LIST;
    private String editing;
    private long scannedAt;
    private int listScroll;
    private Component tooltip;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listY;

    private record Row(String uuid, CustomItemEntry entry, ItemStack stack) {
    }

    public CustomItemScreen() {
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
        listY = panelY + HEADER_H + 6;
        rescan();
    }

    private void rescan() {
        inventory = CustomItems.inventoryItems();
        Map<String, ItemStack> byUuid = new HashMap<>();
        for (ItemStack stack : inventory) {
            byUuid.putIfAbsent(ItemProtection.uuid(stack), stack);
        }
        rows.clear();
        for (Map.Entry<String, CustomItemEntry> e : CustomItems.entries().entrySet()) {
            ItemStack stack = byUuid.get(e.getKey());
            if (stack != null) {
                CustomItems.assigned(stack);
            }
            rows.add(new Row(e.getKey(), e.getValue(), stack));
        }
        if (mode == Mode.EDIT && edited() == null) {
            show(Mode.LIST);
        }
        scannedAt = System.currentTimeMillis();
    }

    private void show(Mode next) {
        mode = next;
        listScroll = 0;
    }

    private Row edited() {
        for (Row row : rows) {
            if (row.uuid().equals(editing)) {
                return row;
            }
        }
        return null;
    }

    private int count() {
        return mode == Mode.ADD ? inventory.size() : rows.size();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (System.currentTimeMillis() - scannedAt > RESCAN_MS) {
            rescan();
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
        switch (mode) {
            case ADD -> drawPicker(graphics, mx, my);
            case EDIT -> drawSettings(graphics, mx, my);
            default -> drawList(graphics, mx, my);
        }

        super.extractRenderState(graphics, mx, my, delta);
        graphics.pose().popMatrix();

        if (tooltip != null) {
            graphics.setTooltipForNextFrame(this.font, this.font.split(tooltip, 220), mouseX, mouseY);
        }
    }

    private int[] doneRect() {
        return new int[]{panelX + panelW - 64 - 12, panelY + HEADER_H - 24, 64, FIELD_H};
    }

    private int[] equipToggleRect() {
        int[] done = doneRect();
        return new int[]{done[0] - GAP * 2 - TOGGLE_W, done[1], TOGGLE_W, FIELD_H};
    }

    private int[] addRect() {
        return new int[]{panelX + panelW - 12 - 84, listY - 4, 84, FIELD_H};
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mx, int my) {
        String title = switch (mode) {
            case ADD -> "ADD A CUSTOM ITEM";
            case EDIT -> "ITEM SETTINGS";
            default -> "QZA CUSTOM ITEMS";
        };
        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX + (panelW / 2), panelY + 8);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, title, 0, 0, PINK);
        graphics.pose().popMatrix();

        int[] done = doneRect();
        button(graphics, done, inside(mx, my, done));
        graphics.centeredText(this.font, mode == Mode.LIST ? "Done" : "Back", done[0] + (done[2] / 2), done[1] + 4,
                TEXT);

        int textEnd = done[0] - 12;
        if (mode == Mode.LIST) {
            QZAConfig cfg = ConfigManager.get();
            int[] r = equipToggleRect();
            textEnd = r[0] - 12;
            boolean over = inside(mx, my, r);
            pinkToggle(graphics, r, cfg.customItemEquip, over);
            graphics.centeredText(this.font, "Equip Animations: " + (cfg.customItemEquip ? "On" : "Off"),
                    r[0] + (r[2] / 2), r[1] + 4, cfg.customItemEquip ? TEXT : TEXT_DIM);
            if (over) {
                tooltip = Component.literal("Turn every custom item's equip animation on or off at once");
            }
        }
        String status = switch (mode) {
            case ADD -> "Pick an item from your inventory and what it turns into";
            case EDIT -> "Click a button for the next option, right-click for the one before";
            default -> "Only you see these. Open an item's settings to change how it looks and works";
        };
        if (!CustomItems.enabled()) {
            status = "Turn on Custom Items in /qza to see them.";
        }
        graphics.text(this.font, this.font.plainSubstrByWidth(status, Math.max(20, textEnd - panelX - 12)),
                panelX + 12, panelY + HEADER_H - 20, TEXT_FAINT);
        graphics.fill(panelX + 8, panelY + HEADER_H - 6, panelX + panelW - 8, panelY + HEADER_H - 5, DIVIDER);
    }

    private int rowY(int row) {
        return listY + 16 + (row * ROW_H);
    }

    private int rowsShown() {
        int room = (panelY + panelH - 10) - (listY + 16);
        return Math.max(1, room / ROW_H);
    }

    private void clampList() {
        listScroll = Math.max(0, Math.min(listScroll, count() - rowsShown()));
    }

    private int[] removeRect(int row) {
        return new int[]{panelX + panelW - 12 - 4 - REMOVE_W, rowY(row) + 5, REMOVE_W, FIELD_H};
    }

    private int[] settingsRect(int row) {
        int[] remove = removeRect(row);
        return new int[]{remove[0] - GAP - SETTINGS_W, remove[1], SETTINGS_W, FIELD_H};
    }

    private int[] pickRect(int row, int option) {
        int right = panelX + panelW - 12 - 4;
        int n = CustomItems.SKINS.size();
        int x = right - (n - option) * PICK_W - (n - 1 - option) * GAP;
        return new int[]{x, rowY(row) + 5, PICK_W, FIELD_H};
    }

    private void drawItemRow(GuiGraphicsExtractor graphics, int i, Row row, int textW) {
        int x = panelX + 12;
        int y = rowY(i);
        CustomItemEntry entry = row.entry();
        graphics.fill(x, y, x + panelW - 24, y + ROW_H - 2, CARD_BG);
        graphics.item(row.stack() != null ? row.stack() : CustomItems.preview(entry), x + 4, y + 4);
        if (row.stack() != null) {
            List<FormattedCharSequence> name = this.font.split(row.stack().getHoverName(), Math.max(20, textW));
            if (!name.isEmpty()) {
                graphics.text(this.font, name.get(0), x + 26, y + 4, TEXT);
            }
        } else {
            String name = nameOf(entry) + " (not in inventory)";
            graphics.text(this.font, this.font.plainSubstrByWidth(name, Math.max(20, textW)), x + 26, y + 4, TEXT_DIM);
        }
        graphics.text(this.font, this.font.plainSubstrByWidth("Shows as " + CustomItems.describe(entry),
                Math.max(20, textW)), x + 26, y + 14, TEXT_FAINT);
    }

    private static String nameOf(CustomItemEntry entry) {
        return entry.name == null || entry.name.isEmpty() ? "Item" : entry.name;
    }

    private void drawList(GuiGraphicsExtractor graphics, int mx, int my) {
        int x = panelX + 12;
        graphics.text(this.font, "YOUR CUSTOM ITEMS", x, listY, TEXT_FAINT);
        int[] add = addRect();
        boolean overAdd = inside(mx, my, add);
        pinkToggle(graphics, add, true, overAdd);
        graphics.centeredText(this.font, "+ Add Item", add[0] + (add[2] / 2), add[1] + 4, TEXT);
        if (overAdd) {
            tooltip = Component.literal("Pick an item from your inventory to turn into a custom item");
        }
        if (rows.isEmpty()) {
            graphics.text(this.font, "No custom items yet.", x, listY + 20, TEXT_DIM);
            graphics.text(this.font, "Click Add Item to turn one of your items into a karambit, butterfly knife or "
                    + "lightsaber.", x, listY + 32, TEXT_FAINT);
            return;
        }
        clampList();
        int shown = Math.min(rows.size() - listScroll, rowsShown());
        for (int i = 0; i < shown; i++) {
            Row row = rows.get(listScroll + i);
            drawItemRow(graphics, i, row, settingsRect(i)[0] - (x + 26) - 8);
            int[] settings = settingsRect(i);
            boolean overSettings = inside(mx, my, settings);
            button(graphics, settings, overSettings);
            graphics.centeredText(this.font, "Settings", settings[0] + (settings[2] / 2), settings[1] + 4, TEXT);
            if (overSettings) {
                tooltip = Component.literal("Change how this item looks and works");
            }
            int[] remove = removeRect(i);
            boolean overRemove = inside(mx, my, remove);
            button(graphics, remove, overRemove);
            graphics.centeredText(this.font, "Remove", remove[0] + (remove[2] / 2), remove[1] + 4, RED);
            if (overRemove) {
                tooltip = Component.literal("Make this item look normal again");
            }
        }
        scrollHints(graphics, shown);
    }

    private void drawPicker(GuiGraphicsExtractor graphics, int mx, int my) {
        int x = panelX + 12;
        graphics.text(this.font, "ITEMS IN YOUR INVENTORY", x, listY, TEXT_FAINT);
        if (inventory.isEmpty()) {
            graphics.text(this.font, "No SkyBlock items with a UUID in your inventory.", x, listY + 20, TEXT_DIM);
            graphics.text(this.font, "Weapons, tools and armour have one. Put the item in your inventory to add it.",
                    x, listY + 32, TEXT_FAINT);
            return;
        }
        clampList();
        int shown = Math.min(inventory.size() - listScroll, rowsShown());
        for (int i = 0; i < shown; i++) {
            ItemStack stack = inventory.get(listScroll + i);
            int y = rowY(i);
            graphics.fill(x, y, x + panelW - 24, y + ROW_H - 2, CARD_BG);
            graphics.item(stack, x + 4, y + 4);
            int textW = pickRect(i, 0)[0] - (x + 26) - 8;
            List<FormattedCharSequence> name = this.font.split(stack.getHoverName(), Math.max(20, textW));
            if (!name.isEmpty()) {
                graphics.text(this.font, name.get(0), x + 26, y + 4, TEXT);
            }
            CustomItemEntry entry = CustomItems.assigned(stack);
            String detail = entry == null ? "Looks normal" : "Already shows as " + CustomItems.describe(entry);
            graphics.text(this.font, this.font.plainSubstrByWidth(detail, Math.max(20, textW)), x + 26, y + 14,
                    TEXT_FAINT);
            for (int o = 0; o < CustomItems.SKINS.size(); o++) {
                String option = CustomItems.SKINS.get(o);
                int[] r = pickRect(i, o);
                boolean on = entry != null && option.equals(entry.type);
                boolean over = inside(mx, my, r);
                pinkToggle(graphics, r, on, over);
                graphics.centeredText(this.font, CustomItems.skinLabel(option), r[0] + (r[2] / 2), r[1] + 4,
                        on ? TEXT : TEXT_DIM);
                if (over) {
                    tooltip = Component.literal("Turn this item into a " + CustomItems.skinLabel(option));
                }
            }
        }
        scrollHints(graphics, shown);
    }

    private static List<Integer> options(CustomItemEntry entry) {
        List<Integer> list = new ArrayList<>(List.of(TYPE, LOOK));
        boolean saber = CustomItems.LIGHTSABER.equals(entry.type);
        if (saber) {
            list.add(COLOUR);
        }
        list.add(EQUIP);
        if (saber) {
            list.add(GLOW);
        }
        list.add(BLOCK);
        list.add(REMOVE);
        return list;
    }

    private static String optionLabel(CustomItemEntry entry, int option) {
        boolean saber = CustomItems.LIGHTSABER.equals(entry.type);
        return switch (option) {
            case TYPE -> "Shows As";
            case LOOK -> saber ? "Hilt Style" : "Material";
            case COLOUR -> "Blade Colour";
            case EQUIP -> saber ? "Light Up On Equip" : "Equip Animation";
            case GLOW -> "Glow";
            case BLOCK -> "Sword Blocking";
            default -> "Remove";
        };
    }

    private static String optionHelp(CustomItemEntry entry, int option) {
        boolean saber = CustomItems.LIGHTSABER.equals(entry.type);
        return switch (option) {
            case TYPE -> "What this item turns into";
            case LOOK -> saber ? "The style of the lightsaber's hilt" : "What the knife is made of";
            case COLOUR -> "The colour of the blade";
            case EQUIP -> saber ? "Lights the blade up when you switch to it"
                    : "Plays the " + CustomItems.skinLabel(entry.type) + " equip animation when you switch to it";
            case GLOW -> "Makes the blade glow around it";
            case BLOCK -> "Lets it do the 1.8 sword blocking animation. Needs Sword Blocking on in Miscellaneous";
            default -> "Make this item look normal again";
        };
    }

    private static String optionValue(CustomItemEntry entry, int option) {
        return switch (option) {
            case TYPE -> CustomItems.skinLabel(entry.type);
            case LOOK -> CustomItems.variantLabel(entry.variant);
            case COLOUR -> CustomItems.colourLabel(entry.colour);
            case EQUIP -> entry.equip ? "On" : "Off";
            case GLOW -> entry.glow ? "On" : "Off";
            case BLOCK -> entry.block ? "On" : "Off";
            default -> "Remove";
        };
    }

    private static boolean isToggle(int option) {
        return option == EQUIP || option == GLOW || option == BLOCK;
    }

    private static boolean toggled(CustomItemEntry entry, int option) {
        return switch (option) {
            case EQUIP -> entry.equip;
            case GLOW -> entry.glow;
            case BLOCK -> entry.block;
            default -> false;
        };
    }

    private int optionX() {
        return panelX + 12 + PREVIEW + 24;
    }

    private int optionY(int index) {
        return listY + 16 + index * OPTION_H;
    }

    private int[] optionRect(int index) {
        return new int[]{panelX + panelW - 12 - 8 - OPTION_W, optionY(index) + 6, OPTION_W, FIELD_H};
    }

    private void drawSettings(GuiGraphicsExtractor graphics, int mx, int my) {
        Row row = edited();
        if (row == null) {
            return;
        }
        CustomItemEntry entry = row.entry();
        int x = panelX + 12;
        String name = row.stack() != null ? row.stack().getHoverName().getString() : nameOf(entry);
        graphics.text(this.font, this.font.plainSubstrByWidth(name.toUpperCase(java.util.Locale.ROOT), panelW - 40),
                x, listY, TEXT_FAINT);

        int previewY = listY + 16;
        graphics.fill(x, previewY, x + PREVIEW + 12, previewY + PREVIEW + 12, CARD_BG);
        outline(graphics, x, previewY, PREVIEW + 12, PREVIEW + 12, 0x40FFFFFF);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 6, previewY + 6);
        graphics.pose().scale(PREVIEW / 16f, PREVIEW / 16f);
        graphics.item(CustomItems.preview(entry), 0, 0);
        graphics.pose().popMatrix();
        List<FormattedCharSequence> describe = this.font.split(Component.literal(CustomItems.describe(entry)),
                PREVIEW + 12);
        for (int l = 0; l < Math.min(2, describe.size()); l++) {
            graphics.text(this.font, describe.get(l), x, previewY + PREVIEW + 16 + l * 10, TEXT_DIM);
        }

        List<Integer> options = options(entry);
        int left = optionX();
        int right = panelX + panelW - 12;
        for (int i = 0; i < options.size(); i++) {
            int option = options.get(i);
            int y = optionY(i);
            graphics.fill(left, y, right, y + OPTION_H - 2, CARD_BG);
            graphics.text(this.font, optionLabel(entry, option), left + 8, y + 4, option == REMOVE ? RED : TEXT);
            int[] r = optionRect(i);
            graphics.text(this.font, this.font.plainSubstrByWidth(optionHelp(entry, option), r[0] - left - 16),
                    left + 8, y + 15, TEXT_FAINT);
            boolean over = inside(mx, my, r);
            if (isToggle(option)) {
                pinkToggle(graphics, r, toggled(entry, option), over);
            } else {
                button(graphics, r, over);
            }
            int colour = option == COLOUR ? 0xFF000000 | CustomItems.colourRgb(entry.colour)
                    : option == REMOVE ? RED : TEXT;
            graphics.centeredText(this.font, optionValue(entry, option), r[0] + (r[2] / 2), r[1] + 4, colour);
            if (over) {
                tooltip = Component.literal(optionHelp(entry, option));
            }
        }
    }

    private void scrollHints(GuiGraphicsExtractor graphics, int shown) {
        int right = panelX + panelW - 12 - (mode == Mode.LIST ? addRect()[2] + 12 : 0);
        if (listScroll > 0) {
            String above = listScroll + " more above, scroll up";
            graphics.text(this.font, above, right - this.font.width(above), listY, TEXT_FAINT);
        }
        int below = count() - listScroll - shown;
        if (below > 0) {
            String more = below + " more below, scroll down";
            graphics.text(this.font, more, right - this.font.width(more), listY, TEXT_FAINT);
        }
    }

    private static void button(GuiGraphicsExtractor graphics, int[] r, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xAA3C5A70 : 0x66223140);
        outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFAFD4EC : 0xFF6A8CA8);
    }

    private static void pinkToggle(GuiGraphicsExtractor graphics, int[] r, boolean on, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], on ? PINK_FILL : (hovered ? 0x55FFFFFF : 0x66000000));
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent local = toLogical(event);
        boolean left = local.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = local.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (!left && !right) {
            return super.mouseClicked(local, doubleClick);
        }
        double x = local.x();
        double y = local.y();
        if (left && inside(x, y, doneRect())) {
            if (mode == Mode.LIST) {
                onClose();
            } else {
                show(Mode.LIST);
                rescan();
            }
            return true;
        }
        clampList();
        int step = left ? 1 : -1;
        switch (mode) {
            case ADD -> {
                int shown = Math.min(inventory.size() - listScroll, rowsShown());
                for (int i = 0; i < shown; i++) {
                    for (int o = 0; o < CustomItems.SKINS.size(); o++) {
                        if (left && inside(x, y, pickRect(i, o))) {
                            if (CustomItems.add(inventory.get(listScroll + i), CustomItems.SKINS.get(o))) {
                                show(Mode.LIST);
                                rescan();
                            }
                            return true;
                        }
                    }
                }
            }
            case EDIT -> {
                Row row = edited();
                if (row != null && clickOption(row, x, y, left, step)) {
                    return true;
                }
            }
            default -> {
                if (clickList(x, y, left)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(local, doubleClick);
    }

    private boolean clickList(double x, double y, boolean left) {
        if (!left) {
            return false;
        }
        QZAConfig cfg = ConfigManager.get();
        if (inside(x, y, equipToggleRect())) {
            cfg.customItemEquip = !cfg.customItemEquip;
            ConfigManager.save();
            return true;
        }
        if (inside(x, y, addRect())) {
            show(Mode.ADD);
            rescan();
            return true;
        }
        int shown = Math.min(rows.size() - listScroll, rowsShown());
        for (int i = 0; i < shown; i++) {
            Row row = rows.get(listScroll + i);
            if (inside(x, y, settingsRect(i))) {
                editing = row.uuid();
                show(Mode.EDIT);
                return true;
            }
            if (inside(x, y, removeRect(i))) {
                CustomItems.remove(row.uuid());
                rescan();
                return true;
            }
        }
        return false;
    }

    private boolean clickOption(Row row, double x, double y, boolean left, int step) {
        CustomItemEntry entry = row.entry();
        List<Integer> options = options(entry);
        for (int i = 0; i < options.size(); i++) {
            if (!inside(x, y, optionRect(i))) {
                continue;
            }
            switch (options.get(i)) {
                case TYPE -> CustomItems.cycleType(entry, step);
                case LOOK -> CustomItems.cycleVariant(entry, step);
                case COLOUR -> CustomItems.cycleColour(entry, step);
                case EQUIP -> {
                    if (left) {
                        CustomItems.toggleEquip(entry);
                    }
                }
                case GLOW -> {
                    if (left) {
                        CustomItems.toggleGlow(entry);
                    }
                }
                case BLOCK -> {
                    if (left) {
                        CustomItems.toggleBlock(entry);
                    }
                }
                default -> {
                    if (left) {
                        CustomItems.remove(row.uuid());
                        show(Mode.LIST);
                        rescan();
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(toLogical(event));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float s = scale();
        double my = (mouseY - offsetY()) / s;
        if (mode != Mode.EDIT && my >= listY && count() > rowsShown()) {
            listScroll -= (int) Math.signum(verticalAmount);
            clampList();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (mode != Mode.LIST && event.key() == InputConstants.KEY_ESCAPE) {
            show(Mode.LIST);
            rescan();
            return true;
        }
        return super.keyPressed(event);
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
