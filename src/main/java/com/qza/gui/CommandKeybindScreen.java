package com.qza.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.keybind.CommandBind;
import com.qza.keybind.CommandKeybinds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CommandKeybindScreen extends Screen {
    private static final int PANEL_BG = 0x55000000;
    private static final int BOX_BORDER = 0x40FFFFFF;
    private static final int DIVIDER = 0xFFC8D4DC;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int PINK = 0xFFFF55FF;
    private static final int PINK_FILL = 0x99FF55FF;

    private static final float TITLE_SCALE = 1.5f;
    private static final int HEADER_H = 52;
    private static final int COLUMNS_H = 16;
    private static final int ROW_H = 22;
    private static final int FIELD_H = 16;
    private static final int GAP = 6;
    private static final int NAME_W = 96;
    private static final int KEY_W = 72;
    private static final int MOD_W = 48;
    private static final int MENU_W = 52;
    private static final int ISLAND_W = 84;
    private static final int ON_W = 30;
    private static final int DEL_W = 16;
    private static final int MIN_COMMAND_W = 60;

    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private Component tooltip;
    private CommandBind capturing;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listW;
    private int listY;
    private int listH;
    private int commandW;

    public CommandKeybindScreen() {
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

    private static List<CommandBind> binds() {
        List<CommandBind> binds = ConfigManager.get().commandBinds;
        binds.removeIf(bind -> bind == null);
        return binds;
    }

    @Override
    protected void init() {
        layout();
        rows.clear();
        for (CommandBind bind : binds()) {
            EditBox name = field(NAME_W, 32, "Name", bind.name, value -> bind.name = value);
            EditBox command = field(commandW, 256, "/command or message", bind.command,
                    value -> bind.command = value);
            EditBox islands = field(ISLAND_W, 64, "Any island", bind.islands, value -> bind.islands = value);
            rows.add(new Row(bind, name, command, islands));
        }
        clampScroll();
    }

    private EditBox field(int width, int maxLength, String hint, String value,
                          Consumer<String> onChange) {
        EditBox box = new EditBox(this.font, listX, listY, Math.max(20, width - 8), 12, Component.empty());
        box.setBordered(false);
        box.setMaxLength(maxLength);
        box.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
        box.setValue(value == null ? "" : value);
        box.moveCursorToStart(false);
        box.setResponder(onChange);
        box.visible = false;
        addRenderableWidget(box);
        return box;
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

        int fixed = NAME_W + KEY_W + MOD_W + MENU_W + ISLAND_W + ON_W + DEL_W + (GAP * 7);
        commandW = Math.max(MIN_COMMAND_W, listW - fixed);
    }

    private int nameX() {
        return listX;
    }

    private int commandX() {
        return nameX() + NAME_W + GAP;
    }

    private int keyX() {
        return commandX() + commandW + GAP;
    }

    private int modX() {
        return keyX() + KEY_W + GAP;
    }

    private int menuX() {
        return modX() + MOD_W + GAP;
    }

    private int islandX() {
        return menuX() + MENU_W + GAP;
    }

    private int onX() {
        return islandX() + ISLAND_W + GAP;
    }

    private int deleteX() {
        return onX() + ON_W + GAP;
    }

    private int contentHeight() {
        return COLUMNS_H + (rows.size() * ROW_H);
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - listH)));
    }

    private int rowY(int index) {
        return listY - (int) Math.round(scroll) + COLUMNS_H + (index * ROW_H);
    }

    private boolean rowShown(int y) {
        return y >= listY + COLUMNS_H && y + FIELD_H <= listY + listH;
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
        tooltip = null;

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(graphics, panelX, panelY, panelW, panelH, 0x33FFFFFF);

        drawHeader(graphics, mx, my);
        drawRows(graphics, mx, my);

        super.extractRenderState(graphics, mx, my, delta);

        graphics.pose().popMatrix();

        if (tooltip != null) {
            graphics.setTooltipForNextFrame(this.font, this.font.split(tooltip, 220), mouseX, mouseY);
        }
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

    private int[] addRect() {
        int w = 96;
        return new int[]{panelX + panelW - w - 12, panelY + HEADER_H - 24, w, FIELD_H};
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mx, int my) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX + (panelW / 2), panelY + 8);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, "QZA COMMAND KEYBINDS", 0, 0, PINK);
        graphics.pose().popMatrix();

        int[] add = addRect();
        boolean hovered = inside(mx, my, add);
        button(graphics, add, hovered, false);
        graphics.centeredText(this.font, "Add Keybind", add[0] + (add[2] / 2), add[1] + 4, TEXT);

        int count = rows.size();
        String status = ConfigManager.get().commandKeybindsEnabled
                ? count + " keybind" + (count == 1 ? "" : "s")
                : count + " keybind" + (count == 1 ? "" : "s") + " - turn on Command Keybinds in /qza to use them";
        graphics.text(this.font, this.font.plainSubstrByWidth(status, add[0] - listX - 8),
                listX, panelY + HEADER_H - 20, TEXT_FAINT);

        graphics.fill(panelX + 8, panelY + HEADER_H - 6,
                panelX + panelW - 8, panelY + HEADER_H - 5, DIVIDER);
    }

    private void positionFields() {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = rowY(i);
            boolean shown = rowShown(y);
            row.name.visible = shown;
            row.command.visible = shown;
            row.islands.visible = shown;
            if (!shown) {
                continue;
            }
            row.name.setPosition(nameX() + 4, y + 4);
            row.command.setPosition(commandX() + 4, y + 4);
            row.command.setWidth(Math.max(20, commandW - 8));
            row.islands.setPosition(islandX() + 4, y + 4);
        }
    }

    private void drawRows(GuiGraphicsExtractor graphics, int mx, int my) {
        graphics.text(this.font, "NAME", nameX(), listY + 2, TEXT_FAINT);
        graphics.text(this.font, "COMMAND", commandX(), listY + 2, TEXT_FAINT);
        graphics.text(this.font, "KEY", keyX(), listY + 2, TEXT_FAINT);
        graphics.text(this.font, "MOD", modX(), listY + 2, TEXT_FAINT);
        graphics.text(this.font, "MENUS", menuX(), listY + 2, TEXT_FAINT);
        graphics.text(this.font, "ISLANDS", islandX(), listY + 2, TEXT_FAINT);

        if (rows.isEmpty()) {
            graphics.text(this.font, "No command keybinds yet.", listX, listY + COLUMNS_H + 6, TEXT_DIM);
            graphics.text(this.font, "Click Add Keybind, type a command like /warp dungeon_hub, then pick a key.",
                    listX, listY + COLUMNS_H + 18, TEXT_FAINT);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = rowY(i);
            if (!rowShown(y)) {
                continue;
            }
            CommandBind bind = row.bind;

            box(graphics, nameX(), y, NAME_W);
            box(graphics, commandX(), y, commandW);
            box(graphics, islandX(), y, ISLAND_W);
            if (inside(mx, my, new int[]{commandX(), y, commandW, FIELD_H})) {
                tooltip = Component.literal("Starts with / to run a command, anything else is sent as a chat message");
            } else if (inside(mx, my, new int[]{islandX(), y, ISLAND_W, FIELD_H})) {
                tooltip = Component.literal("Only works on these islands, like \"catacombs kuudra\". "
                        + "Leave it empty to work everywhere.");
            }

            int[] key = new int[]{keyX(), y, KEY_W, FIELD_H};
            boolean overKey = inside(mx, my, key);
            boolean armed = capturing == bind;
            button(graphics, key, overKey, armed);
            String keyText = armed ? "Press a key..." : CommandKeybinds.keyLabel(bind.key);
            graphics.centeredText(this.font, this.font.plainSubstrByWidth(keyText, KEY_W - 6),
                    key[0] + (KEY_W / 2), y + 4, armed ? PINK : (bind.bound() ? TEXT : TEXT_FAINT));
            if (overKey) {
                tooltip = Component.literal("Click, then press a key or mouse button. "
                        + "Backspace clears it, Esc cancels.");
            }

            int[] mod = new int[]{modX(), y, MOD_W, FIELD_H};
            boolean overMod = inside(mx, my, mod);
            button(graphics, mod, overMod, false);
            graphics.centeredText(this.font, CommandKeybinds.modifierLabel(bind.modifier),
                    mod[0] + (MOD_W / 2), y + 4, TEXT);
            if (overMod) {
                tooltip = Component.literal("Which of Shift, Ctrl or Alt has to be held. "
                        + "Any ignores them, None needs all of them let go.");
            }

            int[] menu = new int[]{menuX() + (MENU_W - FIELD_H) / 2, y, FIELD_H, FIELD_H};
            boolean overMenu = inside(mx, my, menu);
            pinkToggle(graphics, menu, bind.inMenus, overMenu);
            if (overMenu) {
                tooltip = Component.literal("Also works while a menu like your inventory or a chest is open");
            }

            int[] on = new int[]{onX(), y, ON_W, FIELD_H};
            boolean overOn = inside(mx, my, on);
            pinkToggle(graphics, on, bind.enabled, overOn);
            graphics.centeredText(this.font, bind.enabled ? "ON" : "OFF", on[0] + (ON_W / 2), y + 4,
                    bind.enabled ? TEXT : TEXT_DIM);

            int[] del = new int[]{deleteX(), y, DEL_W, FIELD_H};
            boolean overDel = inside(mx, my, del);
            graphics.fill(del[0], del[1], del[0] + del[2], del[1] + del[3], overDel ? 0xAA8B2E2E : 0x66401E1E);
            outline(graphics, del[0], del[1], del[2], del[3], overDel ? 0xFFFF9A9A : 0xFFA85C5C);
            graphics.centeredText(this.font, "x", del[0] + (del[2] / 2), del[1] + 4,
                    overDel ? 0xFFFFFFFF : 0xFFCCCCCC);
        }
    }

    private void box(GuiGraphicsExtractor graphics, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + FIELD_H, 0x66000000);
        outline(graphics, x, y, w, FIELD_H, BOX_BORDER);
    }

    private static void button(GuiGraphicsExtractor graphics, int[] r, boolean hovered, boolean active) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                active ? 0x66FF55FF : (hovered ? 0xAA3C5A70 : 0x66223140));
        outline(graphics, r[0], r[1], r[2], r[3],
                active ? PINK : (hovered ? 0xFFAFD4EC : 0xFF6A8CA8));
    }

    private static void pinkToggle(GuiGraphicsExtractor graphics, int[] r, boolean on, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                on ? PINK_FILL : (hovered ? 0x55FFFFFF : 0x66000000));
        outline(graphics, r[0], r[1], r[2], r[3], on ? PINK : (hovered ? 0xFFDDDDDD : 0x80FFFFFF));
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

        if (capturing != null) {
            if (local.button() != InputConstants.MOUSE_BUTTON_LEFT) {
                capturing.key = InputConstants.Type.MOUSE.getOrCreate(local.button()).getName();
                capturing = null;
                ConfigManager.save();
                return true;
            }
            capturing = null;
        }

        if (local.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(local, doubleClick);
        }

        if (inside(local.x(), local.y(), addRect())) {
            binds().add(new CommandBind());
            ConfigManager.save();
            scroll = Double.MAX_VALUE;
            this.rebuildWidgets();
            return true;
        }

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = rowY(i);
            if (!rowShown(y)) {
                continue;
            }
            CommandBind bind = row.bind;
            if (inside(local.x(), local.y(), new int[]{keyX(), y, KEY_W, FIELD_H})) {
                setFocused(null);
                capturing = bind;
                return true;
            }
            if (inside(local.x(), local.y(), new int[]{modX(), y, MOD_W, FIELD_H})) {
                bind.modifier = CommandKeybinds.nextModifier(bind.modifier);
                ConfigManager.save();
                return true;
            }
            if (inside(local.x(), local.y(), new int[]{menuX() + (MENU_W - FIELD_H) / 2, y, FIELD_H, FIELD_H})) {
                bind.inMenus = !bind.inMenus;
                ConfigManager.save();
                return true;
            }
            if (inside(local.x(), local.y(), new int[]{onX(), y, ON_W, FIELD_H})) {
                bind.enabled = !bind.enabled;
                ConfigManager.save();
                return true;
            }
            if (inside(local.x(), local.y(), new int[]{deleteX(), y, DEL_W, FIELD_H})) {
                binds().remove(bind);
                ConfigManager.save();
                this.rebuildWidgets();
                return true;
            }
        }

        return super.mouseClicked(local, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturing != null) {
            int key = event.key();
            if (key == InputConstants.KEY_ESCAPE) {
                capturing = null;
            } else if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                capturing.key = CommandBind.UNBOUND;
                capturing = null;
                ConfigManager.save();
            } else {
                capturing.key = InputConstants.getKey(event).getName();
                capturing = null;
                ConfigManager.save();
            }
            return true;
        }
        return super.keyPressed(event);
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
        double mx = (mouseX - offsetX()) / s;
        double my = (mouseY - offsetY()) / s;
        if (mx >= listX - 4 && mx <= panelX + panelW && my >= listY && my <= listY + listH) {
            scroll -= verticalAmount * ROW_H;
            clampScroll();
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

    private static final class Row {
        final CommandBind bind;
        final EditBox name;
        final EditBox command;
        final EditBox islands;

        Row(CommandBind bind, EditBox name, EditBox command, EditBox islands) {
            this.bind = bind;
            this.name = name;
            this.command = command;
            this.islands = islands;
        }
    }
}
