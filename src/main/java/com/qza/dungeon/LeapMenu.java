package com.qza.dungeon;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.stats.DungeonClass;
import com.qza.util.ChatUtil;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import com.qza.util.PlayerFaces;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LeapMenu {
    public static final double MIN_SCALE = 50.0;
    public static final double MAX_SCALE = 150.0;

    public static final String ORDER_CLASS = "class";
    public static final String ORDER_NAME = "name";
    public static final String ORDER_ODIN = "odin";
    public static final String ORDER_CUSTOM = "custom";
    public static final List<String> ORDERS = List.of(ORDER_CLASS, ORDER_NAME, ORDER_ODIN, ORDER_CUSTOM);

    public static final List<String> CLASSES = List.of(DungeonClass.ARCHER, DungeonClass.BERSERK,
            DungeonClass.HEALER, DungeonClass.MAGE, DungeonClass.TANK);

    public static final int BOX_W = 166;
    public static final int BOX_H = 64;
    public static final int GAP = 40;
    private static final int HEAD = 50;
    private static final int PAD = 7;
    private static final float NAME_SCALE = 1.35f;

    private static final int BG = 0xBE212121;
    private static final int BG_HOVER = 0xBE434343;
    private static final int DOOR_BG = 0xBE5C5C5C;
    private static final int DOOR_BG_HOVER = 0xBE7C7C7C;
    private static final int DEAD_BG = 0xBE4F1A1A;
    private static final int DEAD_BG_HOVER = 0xBE6E2A2A;
    private static final int EDGE = 0x40FFFFFF;
    private static final int DEAD_TEXT = 0xFFFF5555;
    private static final int DOOR_TEXT = 0xFFE0E0E0;
    private static final int UNKNOWN = 0xFFFFFFFF;
    private static final long TEAM_REFRESH_MS = 250L;
    private static final long DRAWN_MS = 250L;

    private static final Pattern HEAD_NAME = Pattern.compile("^(?:\\[[^]]+] )?(\\w{1,16})");

    public record Entry(String name, String dungeonClass, boolean dead, int slot) {
    }

    public record Tile(PlayerSkin skin, String title, int titleColour, String subtitle, int subtitleColour,
                       boolean dead, boolean door) {
    }

    private static final Entry[] shown = new Entry[4];
    private static long teamAt;
    private static Screen drawnScreen;
    private static long drawnAt;
    private static Screen warnedScreen;

    private LeapMenu() {
    }

    public static boolean active(Screen screen) {
        if (!ConfigManager.get().leapMenuEnabled || !(screen instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
        if (!title.contains("spirit leap") && !title.contains("teleport to player")) {
            return false;
        }
        return DungeonState.inDungeon();
    }

    public static boolean drawing(Screen screen) {
        return drawnScreen == screen && System.currentTimeMillis() - drawnAt < DRAWN_MS;
    }

    public static void covered(Screen screen) {
        if (warnedScreen == screen) {
            return;
        }
        warnedScreen = screen;
        ChatUtil.error("Another mod's leap menu is drawn over QZA's Leap Menu, so QZA left your click to it. "
                + "Turn off NoammAddons' or Odin's Leap Menu to use QZA's.");
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        drawnScreen = screen;
        drawnAt = now;
        if (now - teamAt > TEAM_REFRESH_MS) {
            teamAt = now;
            DungeonTeam.scan();
        }
        QZAConfig cfg = ConfigManager.get();
        String self = DungeonTeam.selfClass();
        Entry[] arranged = arrange(entries(screen.getMenu()), cfg.leapMenuOrder, self == null ? null : preset(self));
        System.arraycopy(arranged, 0, shown, 0, 4);

        Font font = Minecraft.getInstance().font;
        boolean any = false;
        for (Entry entry : shown) {
            any |= entry != null;
        }
        if (!any) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(screen.width / 2f, screen.height / 2f - 9);
            graphics.pose().scale(2f, 2f);
            graphics.centeredText(font, "No players found", 0, 0, DEAD_TEXT);
            graphics.pose().popMatrix();
            return;
        }

        String door = cfg.leapMenuDoorOpener ? DungeonTeam.doorOpener() : null;
        Tile[] tiles = new Tile[4];
        for (int i = 0; i < 4; i++) {
            Entry entry = shown[i];
            if (entry != null) {
                boolean opener = door != null && door.equalsIgnoreCase(entry.name());
                tiles[i] = tile(entry, opener);
            }
        }
        drawTiles(graphics, font, screen.width, screen.height, tiles, quadrant(mouseX, mouseY, screen.width,
                screen.height), -1);
    }

    public static boolean click(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
        QZAConfig cfg = ConfigManager.get();
        if (cfg.leapMenuLeftClickOnly && button != 0) {
            return true;
        }
        int q = quadrant(mouseX, mouseY, screen.width, screen.height);
        Entry entry = q < 0 ? null : shown[q];
        if (entry == null) {
            return true;
        }
        if (entry.dead()) {
            ChatUtil.error(entry.name() + " is dead!");
            return true;
        }
        if (entry.slot() < 0) {
            ChatUtil.error(entry.name() + " isn't in the leap menu.");
            return true;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode == null || client.player == null) {
            return true;
        }
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        client.gameMode.handleContainerInput(screen.getMenu().containerId, entry.slot(), 0, ContainerInput.PICKUP,
                client.player);
        return true;
    }

    public static int quadrant(double mouseX, double mouseY, int width, int height) {
        double cx = width / 2.0;
        double cy = height / 2.0;
        if (mouseX == cx || mouseY == cy) {
            return -1;
        }
        return (mouseY > cy ? 2 : 0) + (mouseX > cx ? 1 : 0);
    }

    public static float scale(int width, int height) {
        double pct = Math.max(MIN_SCALE, Math.min(MAX_SCALE, ConfigManager.get().leapMenuScale));
        float s = (float) (pct / 100.0);
        float fitW = (width - 16f) / (BOX_W * 2 + GAP);
        float fitH = (height - 16f) / (BOX_H * 2 + GAP);
        return Math.max(0.2f, Math.min(s, Math.min(fitW, fitH)));
    }

    public static float gridWidth(float s) {
        return (BOX_W * 2 + GAP) * s;
    }

    public static float gridHeight(float s) {
        return (BOX_H * 2 + GAP) * s;
    }

    public static int[] boxRect(int index, int width, int height) {
        float s = scale(width, height);
        return boxRect(index, (width - gridWidth(s)) / 2f, (height - gridHeight(s)) / 2f, s);
    }

    public static int[] boxRect(int index, float left, float top, float s) {
        float x = left + (index % 2) * (BOX_W + GAP) * s;
        float y = top + (index / 2) * (BOX_H + GAP) * s;
        return new int[]{Math.round(x), Math.round(y), Math.round(BOX_W * s), Math.round(BOX_H * s)};
    }

    public static void drawTiles(GuiGraphicsExtractor graphics, Font font, int width, int height, Tile[] tiles,
                                 int hovered, int hidden) {
        float s = scale(width, height);
        for (int i = 0; i < 4; i++) {
            if (tiles[i] == null || i == hidden) {
                continue;
            }
            int[] r = boxRect(i, width, height);
            drawTile(graphics, font, tiles[i], r[0], r[1], s, i == hovered);
        }
    }

    public static void drawTile(GuiGraphicsExtractor graphics, Font font, Tile tile, float x, float y, float s,
                                boolean hovered) {
        boolean tint = tile.dead() && ConfigManager.get().leapMenuTintDead;
        int bg = tint ? (hovered ? DEAD_BG_HOVER : DEAD_BG)
                : tile.door() ? (hovered ? DOOR_BG_HOVER : DOOR_BG)
                : (hovered ? BG_HOVER : BG);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(s, s);
        graphics.fill(0, 0, BOX_W, BOX_H, bg);
        outline(graphics, 0, 0, BOX_W, BOX_H, EDGE);

        int headX = PAD + 3;
        int headY = (BOX_H - HEAD) / 2;
        graphics.fill(headX - 1, headY - 1, headX + HEAD + 1, headY + HEAD + 1, tile.titleColour());
        PlayerFaceExtractor.extractRenderState(graphics, tile.skin(), headX, headY, HEAD);

        int textX = headX + HEAD + 7;
        int room = BOX_W - textX - PAD;
        boolean subtitle = !tile.subtitle().isEmpty();
        int textY = (BOX_H - Math.round(font.lineHeight * NAME_SCALE) - (subtitle ? font.lineHeight + 3 : 0)) / 2;
        graphics.pose().pushMatrix();
        graphics.pose().translate(textX, textY);
        graphics.pose().scale(NAME_SCALE, NAME_SCALE);
        graphics.text(font, font.plainSubstrByWidth(tile.title(), Math.round(room / NAME_SCALE)), 0, 0,
                tile.titleColour());
        graphics.pose().popMatrix();
        if (subtitle) {
            graphics.text(font, font.plainSubstrByWidth(tile.subtitle(), room), textX,
                    textY + Math.round(font.lineHeight * NAME_SCALE) + 3, tile.subtitleColour());
        }
        if (tile.door()) {
            String tag = "LAST DOOR";
            graphics.pose().pushMatrix();
            graphics.pose().translate(BOX_W - PAD, PAD - 2);
            graphics.pose().scale(0.75f, 0.75f);
            graphics.text(font, tag, -font.width(tag), 0, DOOR_TEXT);
            graphics.pose().popMatrix();
        }
        graphics.pose().popMatrix();
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    public static Tile tile(Entry entry, boolean door) {
        int colour = colour(entry.dungeonClass());
        String subtitle = entry.dead() ? "DEAD"
                : entry.dungeonClass() == null ? "Unknown" : DungeonClass.label(entry.dungeonClass());
        return new Tile(PlayerFaces.skinFor(entry.name()), entry.name(), colour, subtitle,
                entry.dead() ? DEAD_TEXT : colour, entry.dead(), door);
    }

    public static int colour(String dungeonClass) {
        if (dungeonClass == null) {
            return UNKNOWN;
        }
        return switch (dungeonClass) {
            case DungeonClass.ARCHER -> 0xFFFFAA00;
            case DungeonClass.BERSERK -> 0xFFE03A3A;
            case DungeonClass.HEALER -> 0xFFFF55FF;
            case DungeonClass.MAGE -> 0xFF55FFFF;
            case DungeonClass.TANK -> 0xFF00AA00;
            default -> UNKNOWN;
        };
    }

    public static String label(String order) {
        return switch (order) {
            case ORDER_NAME -> "A-Z Name";
            case ORDER_ODIN -> "Odin Sorting";
            case ORDER_CUSTOM -> "Custom Sorting";
            default -> "A-Z Class";
        };
    }

    private static List<Entry> entries(AbstractContainerMenu menu) {
        Map<String, Integer> heads = new LinkedHashMap<>();
        Map<String, String> headNames = new HashMap<>();
        int chestSlots = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < chestSlots; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) {
                continue;
            }
            Matcher m = HEAD_NAME.matcher(IgnUtil.stripCodes(stack.getHoverName().getString()).trim());
            if (m.find()) {
                String key = m.group(1).toLowerCase(Locale.ROOT);
                heads.putIfAbsent(key, i);
                headNames.putIfAbsent(key, m.group(1));
            }
        }

        List<Entry> entries = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (DungeonTeam.Teammate mate : DungeonTeam.teammates()) {
            String key = mate.name().toLowerCase(Locale.ROOT);
            seen.add(key);
            entries.add(new Entry(mate.name(), mate.dungeonClass(), mate.dead(), heads.getOrDefault(key, -1)));
        }
        for (Map.Entry<String, Integer> head : heads.entrySet()) {
            if (seen.add(head.getKey())) {
                entries.add(new Entry(headNames.get(head.getKey()), null, false, head.getValue()));
            }
        }
        return entries;
    }

    public static Entry[] arrange(List<Entry> entries, String order, List<String> custom) {
        Comparator<Entry> byName = Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT));
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(byName);
        Entry[] slots = new Entry[4];
        String mode = order == null ? ORDER_CLASS : order;
        if (ORDER_CUSTOM.equals(mode) && custom == null) {
            mode = ORDER_ODIN;
        }
        switch (mode) {
            case ORDER_NAME -> fillInOrder(sorted, slots);
            case ORDER_ODIN -> {
                sorted.sort(Comparator.comparingInt(e -> odinPriority(e.dungeonClass())));
                List<Entry> spare = new ArrayList<>();
                for (Entry entry : sorted) {
                    int quadrant = odinQuadrant(entry.dungeonClass());
                    if (quadrant >= 0 && slots[quadrant] == null) {
                        slots[quadrant] = entry;
                    } else {
                        spare.add(entry);
                    }
                }
                fillGaps(spare, slots);
            }
            case ORDER_CUSTOM -> {
                List<Entry> spare = new ArrayList<>();
                for (Entry entry : sorted) {
                    int quadrant = entry.dungeonClass() == null ? -1 : custom.indexOf(entry.dungeonClass());
                    if (quadrant >= 0 && slots[quadrant] == null) {
                        slots[quadrant] = entry;
                    } else {
                        spare.add(entry);
                    }
                }
                fillGaps(spare, slots);
            }
            default -> {
                sorted.sort(Comparator.comparingInt(e -> classRank(e.dungeonClass())));
                fillInOrder(sorted, slots);
            }
        }
        return slots;
    }

    public static List<String> others(String playing) {
        List<String> others = new ArrayList<>(CLASSES);
        others.remove(playing);
        return others;
    }

    public static List<String> defaultPreset(String playing) {
        List<String> others = others(playing);
        others.sort(Comparator.comparingInt(LeapMenu::odinPriority));
        String[] slots = new String[4];
        List<String> spare = new ArrayList<>();
        for (String dungeonClass : others) {
            int quadrant = odinQuadrant(dungeonClass);
            if (quadrant >= 0 && slots[quadrant] == null) {
                slots[quadrant] = dungeonClass;
            } else {
                spare.add(dungeonClass);
            }
        }
        for (int i = 0; i < 4 && !spare.isEmpty(); i++) {
            if (slots[i] == null) {
                slots[i] = spare.remove(0);
            }
        }
        return new ArrayList<>(List.of(slots));
    }

    public static List<String> preset(String playing) {
        List<String> saved = presets().get(playing);
        if (saved != null && saved.size() == 4 && new HashSet<>(saved).equals(new HashSet<>(others(playing)))) {
            return new ArrayList<>(saved);
        }
        return defaultPreset(playing);
    }

    public static void savePreset(String playing, List<String> slots) {
        presets().put(playing, new ArrayList<>(slots));
        ConfigManager.save();
    }

    public static void resetPreset(String playing) {
        presets().remove(playing);
        ConfigManager.save();
    }

    private static Map<String, List<String>> presets() {
        QZAConfig cfg = ConfigManager.get();
        if (cfg.leapMenuCustomOrders == null) {
            cfg.leapMenuCustomOrders = new LinkedHashMap<>();
        }
        List<String> old = cfg.leapMenuCustomOrder;
        if (old != null && !old.isEmpty()) {
            boolean edited = old.size() == 5 && new HashSet<>(old).containsAll(CLASSES) && !old.equals(CLASSES);
            if (edited && cfg.leapMenuCustomOrders.isEmpty()) {
                for (String playing : CLASSES) {
                    List<String> slots = new ArrayList<>(old.subList(0, 4));
                    int own = slots.indexOf(playing);
                    if (own >= 0) {
                        slots.set(own, old.get(4));
                    }
                    cfg.leapMenuCustomOrders.put(playing, slots);
                }
            }
            cfg.leapMenuCustomOrder = new ArrayList<>();
            ConfigManager.save();
        }
        return cfg.leapMenuCustomOrders;
    }

    private static void fillInOrder(List<Entry> sorted, Entry[] slots) {
        for (int i = 0; i < Math.min(4, sorted.size()); i++) {
            slots[i] = sorted.get(i);
        }
    }

    private static void fillGaps(List<Entry> spare, Entry[] slots) {
        for (int i = 0; i < 4 && !spare.isEmpty(); i++) {
            if (slots[i] == null) {
                slots[i] = spare.remove(0);
            }
        }
    }

    private static int classRank(String dungeonClass) {
        int rank = dungeonClass == null ? -1 : CLASSES.indexOf(dungeonClass);
        return rank < 0 ? CLASSES.size() : rank;
    }

    private static int odinQuadrant(String dungeonClass) {
        if (dungeonClass == null) {
            return -1;
        }
        return switch (dungeonClass) {
            case DungeonClass.ARCHER -> 0;
            case DungeonClass.BERSERK -> 1;
            case DungeonClass.HEALER -> 2;
            case DungeonClass.MAGE, DungeonClass.TANK -> 3;
            default -> -1;
        };
    }

    private static int odinPriority(String dungeonClass) {
        if (dungeonClass == null) {
            return 3;
        }
        return switch (dungeonClass) {
            case DungeonClass.BERSERK -> 0;
            case DungeonClass.TANK -> 1;
            default -> 2;
        };
    }
}
