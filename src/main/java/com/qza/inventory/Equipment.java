package com.qza.inventory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.qza.QZA;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.mixin.ContainerScreenAccessor;
import com.qza.util.ChatUtil;
import com.qza.util.IgnUtil;
import com.qza.util.SkyBlockArea;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Equipment {
    private static final int SLOTS = 4;
    private static final int X = 77;
    private static final int Y = 8;
    private static final int SHIELD_X = 76;
    private static final int SHIELD_Y = 61;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int SCAN_TICKS = 5;
    private static final String[] NAMES = {"Necklace", "Cloak", "Belt", "Gloves"};
    private static final String DEFAULT_PROFILE = "default";
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Pattern PROFILE = Pattern.compile("^Profile ID: ([0-9a-fA-F-]{32,36})$");
    private static final Pattern STATS_MENU = Pattern.compile(
            "^(?:Your )?(?:Stats & Equipment|Equipment and Stats)$|^(?:\\(\\d+/\\d+\\) )?Loadouts$");
    private static final Pattern SETS_MENU = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Equipment Sets$");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, ItemStack[]> byProfile = new HashMap<>();
    private static String profile = DEFAULT_PROFILE;
    private static boolean loaded;
    private static int scanIn;

    private Equipment() {
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof InventoryScreen) {
                ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> !mouseClicked(current, event));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(Equipment::tick);
    }

    private static Path file() {
        return ConfigManager.qzaDir().resolve("equipment.json");
    }

    public static boolean hidesSlot(Slot slot) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.hideShieldSlot && !cfg.equipmentInInventory) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || slot != player.inventoryMenu.getSlot(InventoryMenu.SHIELD_SLOT)) {
            return false;
        }
        return SkyBlockArea.onSkyBlock();
    }

    public static void onChatMessage(String raw) {
        if (raw == null) {
            return;
        }
        Matcher matcher = PROFILE.matcher(IgnUtil.stripCodes(raw).trim());
        if (!matcher.matches()) {
            return;
        }
        String id = matcher.group(1).toLowerCase(Locale.ROOT);
        if (!id.equals(profile)) {
            profile = id;
            if (loaded) {
                save();
            }
        }
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }
        if (!loaded) {
            load();
        }
        if (!ConfigManager.get().equipmentInInventory || --scanIn > 0) {
            return;
        }
        scanIn = SCAN_TICKS;
        if (!(Mc.screen() instanceof AbstractContainerScreen<?> screen) || screen instanceof InventoryScreen) {
            return;
        }
        String title = IgnUtil.stripCodes(screen.getTitle().getString()).trim();
        ItemStack[] found = null;
        if (STATS_MENU.matcher(title).matches()) {
            found = column(client, screen, 1, 6);
        } else if (SETS_MENU.matcher(title).matches()) {
            int selected = selectedSet(client, screen);
            if (selected >= 0) {
                found = column(client, screen, selected, 4);
            }
        }
        if (found != null) {
            store(found);
        }
    }

    private static List<Slot> containerSlots(Minecraft client, AbstractContainerScreen<?> screen) {
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != client.player.getInventory()) {
                slots.add(slot);
            }
        }
        slots.sort(Comparator.comparingInt(Slot::getContainerSlot));
        return slots;
    }

    private static ItemStack[] column(Minecraft client, AbstractContainerScreen<?> screen, int column, int rows) {
        List<ItemStack> found = new ArrayList<>();
        for (Slot slot : containerSlots(client, screen)) {
            int index = slot.getContainerSlot();
            if (index % 9 != column || index / 9 >= rows) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || itemId(stack).equals("black_stained_glass_pane")) {
                continue;
            }
            String name = IgnUtil.stripCodes(stack.getHoverName().getString()).trim().toLowerCase(Locale.ROOT);
            found.add(name.startsWith("empty") || name.startsWith("slot ") ? ItemStack.EMPTY : stack.copy());
        }
        return found.size() < SLOTS ? null : found.subList(0, SLOTS).toArray(ItemStack[]::new);
    }

    private static int selectedSet(Minecraft client, AbstractContainerScreen<?> screen) {
        for (Slot slot : containerSlots(client, screen)) {
            int index = slot.getContainerSlot();
            if (index >= 36 && index < 45 && itemId(slot.getItem()).equals("lime_dye")) {
                return index % 9;
            }
        }
        return -1;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static void store(ItemStack[] found) {
        ItemStack[] known = byProfile.get(profile);
        if (known != null && same(known, found)) {
            return;
        }
        byProfile.put(profile, found);
        save();
    }

    private static boolean same(ItemStack[] a, ItemStack[] b) {
        for (int i = 0; i < SLOTS; i++) {
            if (!ItemStack.matches(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack[] current() {
        ItemStack[] items = byProfile.get(profile);
        if (items == null) {
            items = new ItemStack[SLOTS];
            Arrays.fill(items, ItemStack.EMPTY);
        }
        return items;
    }

    private static DynamicOps<JsonElement> ops() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? null : client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    private static void load() {
        loaded = true;
        Path path = file();
        DynamicOps<JsonElement> ops = ops();
        if (ops == null || !Files.isRegularFile(path)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("profile") && profile.equals(DEFAULT_PROFILE)) {
                profile = root.get("profile").getAsString();
            }
            JsonObject profiles = root.has("profiles") ? root.getAsJsonObject("profiles") : new JsonObject();
            for (Map.Entry<String, JsonElement> entry : profiles.entrySet()) {
                JsonArray array = entry.getValue().getAsJsonArray();
                ItemStack[] items = new ItemStack[SLOTS];
                for (int i = 0; i < SLOTS; i++) {
                    items[i] = i < array.size()
                            ? ItemStack.OPTIONAL_CODEC.parse(ops, array.get(i)).result().orElse(ItemStack.EMPTY)
                            : ItemStack.EMPTY;
                }
                byProfile.put(entry.getKey(), items);
            }
        } catch (IOException | RuntimeException e) {
            QZA.LOGGER.warn("Could not read equipment.json", e);
        }
    }

    private static void save() {
        DynamicOps<JsonElement> ops = ops();
        if (ops == null) {
            return;
        }
        JsonObject profiles = new JsonObject();
        for (Map.Entry<String, ItemStack[]> entry : byProfile.entrySet()) {
            JsonArray array = new JsonArray();
            for (ItemStack stack : entry.getValue()) {
                array.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).result().orElse(new JsonObject()));
            }
            profiles.add(entry.getKey(), array);
        }
        JsonObject root = new JsonObject();
        root.addProperty("profile", profile);
        root.add("profiles", profiles);
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not save equipment.json", e);
        }
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!(screen instanceof InventoryScreen) || !SkyBlockArea.onSkyBlock()) {
            return;
        }
        QZAConfig cfg = ConfigManager.get();
        ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
        int left = accessor.qzaGuiLeft();
        int top = accessor.qzaGuiTop();
        if (!cfg.equipmentInInventory) {
            if (cfg.hideShieldSlot) {
                graphics.fill(left + SHIELD_X, top + SHIELD_Y, left + SHIELD_X + 18, top + SHIELD_Y + 18, PANEL);
            }
            return;
        }

        ItemStack[] items = current();
        boolean seen = byProfile.containsKey(profile);
        Font font = Minecraft.getInstance().font;
        boolean carrying = !screen.getMenu().getCarried().isEmpty();
        for (int i = 0; i < SLOTS; i++) {
            int x = left + X;
            int y = top + Y + (i * 18);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x - 1, y - 1, 18, 18);
            ItemStack stack = items[i];
            if (!stack.isEmpty()) {
                graphics.item(stack, x, y);
                graphics.itemDecorations(font, stack, x, y);
            }
            if (mouseX < x || mouseX >= x + 16 || mouseY < y || mouseY >= y + 16) {
                continue;
            }
            graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
            if (carrying) {
                continue;
            }
            if (!stack.isEmpty()) {
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            } else {
                graphics.setTooltipForNextFrame(font, Component.literal(seen
                        ? "No " + NAMES[i] + " - click to open /equipment"
                        : "Open /equipment once so QZA can show your equipment"), mouseX, mouseY);
            }
        }
    }

    private static boolean mouseClicked(Screen screen, MouseButtonEvent event) {
        if (!(screen instanceof InventoryScreen inventory) || !ConfigManager.get().equipmentInInventory
                || !SkyBlockArea.onSkyBlock()) {
            return false;
        }
        ContainerScreenAccessor accessor = (ContainerScreenAccessor) inventory;
        int x = accessor.qzaGuiLeft() + X;
        for (int i = 0; i < SLOTS; i++) {
            int y = accessor.qzaGuiTop() + Y + (i * 18);
            if (event.x() >= x && event.x() < x + 16 && event.y() >= y && event.y() < y + 16) {
                ChatUtil.sendCommand("equipment");
                return true;
            }
        }
        return false;
    }
}
