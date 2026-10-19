package com.qza.inventory;

import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.mixin.CustomDataAccessor;
import com.qza.gui.QZAChatScreen;
import com.qza.notify.NotificationBox;
import com.qza.waypoint.WaypointColour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class TerminatorIndicator {
    public static final String SOUL_EATER = "soul_eater";
    public static final String DUPLEX = "duplex";
    public static final List<String> KINDS = List.of(SOUL_EATER, DUPLEX);

    public static final String GUI = "gui";
    public static final String DOT = "dot";
    public static final String BOTH = "both";
    public static final List<String> SHOW = List.of(GUI, DOT, BOTH);

    private static final String TERMINATOR = "TERMINATOR";
    private static final String SOUL_EATER_ENCHANT = "ultimate_soul_eater";
    private static final String DUPLEX_ENCHANT = "ultimate_reiterate";
    private static final int ITEM_GOLD = 0xFFAA00;
    private static final int SLOT = 16;
    private static final int DOT_SIZE = 6;
    private static final int DOT_EDGE = 0xE0101010;

    private TerminatorIndicator() {
    }

    public static String showLabel(String show) {
        return switch (show == null ? BOTH : show) {
            case GUI -> "GUI";
            case DOT -> "Dot";
            default -> "Both";
        };
    }

    public static String kindLabel(String kind) {
        return SOUL_EATER.equals(kind) ? "Soul Eater" : "Duplex";
    }

    public static boolean enabled() {
        return ConfigManager.get().terminatorIndicatorEnabled;
    }

    public static boolean showsGui() {
        String show = ConfigManager.get().terminatorIndicatorShow;
        return !DOT.equals(show);
    }

    public static boolean showsDot() {
        String show = ConfigManager.get().terminatorIndicatorShow;
        return !GUI.equals(show);
    }

    private static CompoundTag tag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        return ((CustomDataAccessor) (Object) data).qzaTag();
    }

    private static CompoundTag terminatorTag(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag != null && TERMINATOR.equals(tag.getStringOr("id", "")) ? tag : null;
    }

    public static boolean isTerminator(ItemStack stack) {
        return terminatorTag(stack) != null;
    }

    public static String uuid(ItemStack stack) {
        CompoundTag tag = terminatorTag(stack);
        return tag == null ? "" : tag.getStringOr("uuid", "");
    }

    public static String ultimate(ItemStack stack) {
        CompoundTag tag = terminatorTag(stack);
        if (tag == null) {
            return "";
        }
        CompoundTag enchants = tag.getCompoundOrEmpty("enchantments");
        if (enchants.getIntOr(SOUL_EATER_ENCHANT, 0) > 0) {
            return SOUL_EATER;
        }
        if (enchants.getIntOr(DUPLEX_ENCHANT, 0) > 0) {
            return DUPLEX;
        }
        return "";
    }

    public static String details(ItemStack stack) {
        CompoundTag tag = terminatorTag(stack);
        if (tag == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        String modifier = tag.getStringOr("modifier", "");
        parts.add(modifier.isEmpty() ? "No reforge" : capitalise(modifier));
        CompoundTag enchants = tag.getCompoundOrEmpty("enchantments");
        int soul = enchants.getIntOr(SOUL_EATER_ENCHANT, 0);
        int duplex = enchants.getIntOr(DUPLEX_ENCHANT, 0);
        if (soul > 0) {
            parts.add("Soul Eater " + roman(soul));
        } else if (duplex > 0) {
            parts.add("Duplex " + roman(duplex));
        } else {
            parts.add("No Soul Eater or Duplex");
        }
        String id = tag.getStringOr("uuid", "");
        if (!id.isEmpty()) {
            parts.add(shortUuid(id));
        }
        return String.join("  ", parts);
    }

    public static String shortUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return "";
        }
        return uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
    }

    public static String kind(ItemStack stack) {
        CompoundTag tag = terminatorTag(stack);
        if (tag == null) {
            return null;
        }
        QZAConfig cfg = ConfigManager.get();
        String id = tag.getStringOr("uuid", "");
        if (!id.isEmpty()) {
            if (id.equals(cfg.terminatorSoulEaterUuid)) {
                return SOUL_EATER;
            }
            if (id.equals(cfg.terminatorDuplexUuid)) {
                return DUPLEX;
            }
        }
        String ultimate = ultimate(stack);
        if (SOUL_EATER.equals(ultimate) && blank(cfg.terminatorSoulEaterUuid)) {
            return SOUL_EATER;
        }
        if (DUPLEX.equals(ultimate) && blank(cfg.terminatorDuplexUuid)) {
            return DUPLEX;
        }
        return null;
    }

    public static String name(String kind) {
        QZAConfig cfg = ConfigManager.get();
        String name = SOUL_EATER.equals(kind) ? cfg.terminatorSoulEaterName : cfg.terminatorDuplexName;
        return name == null ? "" : name;
    }

    public static void setName(String kind, String name) {
        QZAConfig cfg = ConfigManager.get();
        String value = name == null ? "" : name.strip();
        if (SOUL_EATER.equals(kind)) {
            cfg.terminatorSoulEaterName = value;
        } else {
            cfg.terminatorDuplexName = value;
        }
    }

    public static String colour(String kind) {
        QZAConfig cfg = ConfigManager.get();
        String colour = SOUL_EATER.equals(kind) ? cfg.terminatorSoulEaterColour : cfg.terminatorDuplexColour;
        return colour == null ? "" : colour;
    }

    public static void setColour(String kind, String colour) {
        QZAConfig cfg = ConfigManager.get();
        if (SOUL_EATER.equals(kind)) {
            cfg.terminatorSoulEaterColour = colour;
        } else {
            cfg.terminatorDuplexColour = colour;
        }
        ConfigManager.save();
    }

    public static List<String> nameColours() {
        List<String> out = new ArrayList<>();
        out.add("");
        out.addAll(WaypointColour.names());
        return out;
    }

    public static String colourLabel(String colour) {
        if (colour == null || colour.isEmpty()) {
            return "Item Colour";
        }
        return capitalise(colour);
    }

    public static int colourRgb(String colour) {
        return colour == null || colour.isEmpty() ? ITEM_GOLD : WaypointColour.argb(colour) & 0xFFFFFF;
    }

    public static String picked(String kind) {
        QZAConfig cfg = ConfigManager.get();
        String id = SOUL_EATER.equals(kind) ? cfg.terminatorSoulEaterUuid : cfg.terminatorDuplexUuid;
        return id == null ? "" : id;
    }

    public static void pick(String kind, String uuid) {
        QZAConfig cfg = ConfigManager.get();
        String id = uuid == null ? "" : uuid;
        if (SOUL_EATER.equals(kind)) {
            cfg.terminatorSoulEaterUuid = id;
            if (!id.isEmpty() && id.equals(cfg.terminatorDuplexUuid)) {
                cfg.terminatorDuplexUuid = "";
            }
        } else {
            cfg.terminatorDuplexUuid = id;
            if (!id.isEmpty() && id.equals(cfg.terminatorSoulEaterUuid)) {
                cfg.terminatorSoulEaterUuid = "";
            }
        }
        ConfigManager.save();
    }

    public static int dotRgb(String kind) {
        QZAConfig cfg = ConfigManager.get();
        return WaypointColour.argb(SOUL_EATER.equals(kind) ? cfg.terminatorSoulEaterDot : cfg.terminatorDuplexDot);
    }

    public static String fallbackName(String kind) {
        return SOUL_EATER.equals(kind) ? "Precise Terminator" : "Spiritual Terminator";
    }

    public static Component display(ItemStack stack, String kind) {
        String custom = name(kind);
        String colour = colour(kind);
        boolean haveItem = stack != null && !stack.isEmpty();
        if (custom.isEmpty() && colour.isEmpty() && haveItem) {
            return stack.getHoverName();
        }
        String text = !custom.isEmpty() ? custom : haveItem ? stack.getHoverName().getString() : fallbackName(kind);
        int rgb = colour.isEmpty() ? (haveItem ? firstColour(stack.getHoverName()) : ITEM_GOLD) : colourRgb(colour);
        return Component.literal(text).withStyle(Style.EMPTY.withColor(rgb));
    }

    private static int firstColour(Component component) {
        int[] found = {ITEM_GOLD};
        boolean[] done = {false};
        component.visit((style, text) -> {
            TextColor colour = style.getColor();
            if (!done[0] && colour != null && !text.isBlank()) {
                found[0] = colour.getValue();
                done[0] = true;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return found[0];
    }

    public static List<ItemStack> inventoryTerminators() {
        List<ItemStack> out = new ArrayList<>();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return out;
        }
        Inventory inventory = player.getInventory();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!isTerminator(stack)) {
                continue;
            }
            String id = uuid(stack);
            if (!id.isEmpty() && !seen.add(id)) {
                continue;
            }
            out.add(stack);
        }
        return out;
    }

    public static ItemStack find(List<ItemStack> stacks, String kind) {
        for (ItemStack stack : stacks) {
            if (kind.equals(kind(stack))) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.terminatorIndicatorEnabled || !showsGui()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Screen screen = Mc.screen();
        if (screen != null && !(screen instanceof ChatScreen) && !(screen instanceof QZAChatScreen)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        String kind = kind(held);
        if (kind == null) {
            return;
        }
        Component shown = display(held, kind);
        float scale = NotificationBox.clampScale(cfg.terminatorHudScale);
        int w = Math.round(font.width(shown) * scale);
        int h = Math.round(font.lineHeight * scale);
        int[] pos = NotificationBox.topLeft(cfg.terminatorHudX, cfg.terminatorHudY,
                graphics.guiWidth(), graphics.guiHeight(), w, h);
        graphics.pose().pushMatrix();
        graphics.pose().translate(pos[0], pos[1]);
        graphics.pose().scale(scale, scale);
        graphics.text(font, shown, 0, 0, 0xFFFFFFFF);
        graphics.pose().popMatrix();
    }

    public static String previewText() {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        String kind = kind(held);
        if (kind == null) {
            kind = SOUL_EATER;
            held = find(inventoryTerminators(), kind);
        }
        return display(held, kind).getString();
    }

    public static int previewColour() {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        String kind = kind(held);
        if (kind == null) {
            kind = SOUL_EATER;
            held = find(inventoryTerminators(), kind);
        }
        TextColor colour = display(held, kind).getStyle().getColor();
        int rgb = colour != null ? colour.getValue() : held.isEmpty() ? ITEM_GOLD : firstColour(held.getHoverName());
        return 0xFF000000 | rgb;
    }

    public static void drawDot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.terminatorIndicatorEnabled || !showsDot()) {
            return;
        }
        String kind = kind(stack);
        if (kind == null) {
            return;
        }
        String corner = cfg.terminatorDotCorner;
        boolean left = ItemProtection.TOP_LEFT.equals(corner) || ItemProtection.BOTTOM_LEFT.equals(corner);
        boolean bottom = ItemProtection.BOTTOM_LEFT.equals(corner) || ItemProtection.BOTTOM_RIGHT.equals(corner);
        int dx = left ? x : x + SLOT - DOT_SIZE;
        int dy = bottom ? y + SLOT - DOT_SIZE : y;
        dot(graphics, dx, dy, dotRgb(kind));
    }

    public static void dot(GuiGraphicsExtractor graphics, int x, int y, int argb) {
        int colour = 0xFF000000 | argb;
        int shine = 0xFF000000 | lighten(argb);
        graphics.fill(x + 2, y, x + 4, y + 1, DOT_EDGE);
        graphics.fill(x + 1, y + 1, x + 5, y + 2, DOT_EDGE);
        graphics.fill(x, y + 2, x + 6, y + 4, DOT_EDGE);
        graphics.fill(x + 1, y + 4, x + 5, y + 5, DOT_EDGE);
        graphics.fill(x + 2, y + 5, x + 4, y + 6, DOT_EDGE);
        graphics.fill(x + 2, y + 1, x + 4, y + 5, colour);
        graphics.fill(x + 1, y + 2, x + 5, y + 4, colour);
        graphics.fill(x + 2, y + 2, x + 3, y + 3, shine);
    }

    private static int lighten(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r += (255 - r) / 2;
        g += (255 - g) / 2;
        b += (255 - b) / 2;
        return (r << 16) | (g << 8) | b;
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.terminatorHudX = defaults.terminatorHudX;
        cfg.terminatorHudY = defaults.terminatorHudY;
        cfg.terminatorHudScale = defaults.terminatorHudScale;
    }

    private static boolean blank(String value) {
        return value == null || value.isEmpty();
    }

    private static String capitalise(String value) {
        if (value.isEmpty()) {
            return value;
        }
        String spaced = value.replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String word : spaced.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            default -> String.valueOf(level);
        };
    }
}
