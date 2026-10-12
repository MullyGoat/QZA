package com.qza.inventory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.qza.QZA;
import com.qza.config.ConfigManager;
import com.qza.mixin.ContainerScreenAccessor;
import com.qza.util.IgnUtil;
import com.qza.util.SkyBlockArea;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StoragePreview {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final Pattern ENDER_CHEST = Pattern.compile("Ender Chest.*\\((\\d+)/\\d+\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BACKPACK = Pattern.compile("Backpack.*\\(Slot #(\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BACKPACK_SIZE = Pattern.compile("has (\\d+) slots", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROFILE = Pattern.compile("^Profile ID: ([0-9a-fA-F-]{32,36})$");
    private static final String STORAGE = "Storage";
    private static final String DEFAULT_PROFILE = "default";
    private static final boolean SKYBLOCKER = FabricLoader.getInstance().isModLoaded("skyblocker");
    private static final Gson GSON = new Gson();

    private static final int PAGES = 27;
    private static final int ENDER_PAGES = 9;
    private static final int ROW = 9;
    private static final int SLOT = 18;
    private static final int PANEL_W = 176;
    private static final int TOP_H = 17;
    private static final int BOTTOM_V = 215;
    private static final int BOTTOM_H = 7;
    private static final int TITLE_COLOUR = 0xFF404040;

    private static final Map<String, Page[]> byProfile = new HashMap<>();
    private static String profile = DEFAULT_PROFILE;
    private static boolean loaded;
    private static CompletableFuture<Void> writing = CompletableFuture.completedFuture(null);

    private StoragePreview() {
    }

    private record Page(String name, ItemStack[] items) {
        int rows() {
            return Math.max(0, (items.length - ROW) / ROW);
        }
    }

    public static boolean available() {
        return !SKYBLOCKER;
    }

    private static boolean active() {
        return available() && ConfigManager.get().storagePreview && SkyBlockArea.onSkyBlock();
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof AbstractContainerScreen<?> container) {
                ScreenEvents.remove(screen).register(closed -> onClose(container));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!loaded && client.level != null) {
                load();
            }
        });
    }

    public static void onChatMessage(String raw) {
        if (raw == null) {
            return;
        }
        Matcher matcher = PROFILE.matcher(IgnUtil.stripCodes(raw).trim());
        if (matcher.matches()) {
            profile = account() + "/" + matcher.group(1).toLowerCase(Locale.ROOT).replace("-", "");
        }
    }

    private static String account() {
        Minecraft client = Minecraft.getInstance();
        return client.getUser() == null || client.getUser().getProfileId() == null
                ? DEFAULT_PROFILE : client.getUser().getProfileId().toString().replace("-", "");
    }

    private static Page[] pages() {
        return byProfile.computeIfAbsent(profile, key -> new Page[PAGES]);
    }

    private static String title(AbstractContainerScreen<?> screen) {
        return IgnUtil.stripCodes(screen.getTitle().getString()).trim();
    }

    private static void onClose(AbstractContainerScreen<?> screen) {
        if (!active()) {
            return;
        }
        String title = title(screen);
        int index = pageFromTitle(title);
        boolean changed = false;
        if (index >= 0) {
            pages()[index] = new Page(title, containerItems(screen));
            changed = true;
        }
        if (title.equals(STORAGE)) {
            changed |= readStorageMenu(screen);
        }
        if (changed) {
            save();
        }
    }

    private static ItemStack[] containerItems(AbstractContainerScreen<?> screen) {
        Minecraft client = Minecraft.getInstance();
        int size = 0;
        for (Slot slot : screen.getMenu().slots) {
            if (client.player == null || slot.container != client.player.getInventory()) {
                size = Math.max(size, slot.getContainerSlot() + 1);
            }
        }
        ItemStack[] items = new ItemStack[size];
        Arrays.fill(items, ItemStack.EMPTY);
        for (Slot slot : screen.getMenu().slots) {
            if (client.player == null || slot.container != client.player.getInventory()) {
                items[slot.getContainerSlot()] = slot.getItem().copy();
            }
        }
        return items;
    }

    private static boolean readStorageMenu(AbstractContainerScreen<?> screen) {
        List<Slot> slots = screen.getMenu().slots;
        if (slots.size() < 45) {
            return false;
        }
        Page[] pages = pages();
        boolean changed = false;
        for (int i = ROW; i < ROW * 2; i++) {
            int index = i - ROW;
            if (!itemId(slots.get(i).getItem()).equals("red_stained_glass_pane") && pages[index] == null) {
                pages[index] = empty(ROW * 2);
                changed = true;
            }
        }
        for (int i = ROW * 3; i < ROW * 5; i++) {
            int index = i - (ROW * 2);
            ItemStack stack = slots.get(i).getItem();
            if (itemId(stack).equals("brown_stained_glass_pane") && pages[index] != null) {
                pages[index] = null;
                changed = true;
            }
            if (pages[index] == null) {
                int size = backpackSize(stack);
                if (size > 0) {
                    pages[index] = empty(size + ROW);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static Page empty(int size) {
        ItemStack[] items = new ItemStack[size];
        Arrays.fill(items, ItemStack.EMPTY);
        return new Page("", items);
    }

    private static int backpackSize(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return 0;
        }
        for (Component line : lore.lines()) {
            Matcher matcher = BACKPACK_SIZE.matcher(IgnUtil.stripCodes(line.getString()));
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return 0;
    }

    static int pageFromTitle(String title) {
        Matcher ender = ENDER_CHEST.matcher(title);
        if (ender.find()) {
            return Integer.parseInt(ender.group(1)) - 1;
        }
        Matcher backpack = BACKPACK.matcher(title);
        if (backpack.find()) {
            int index = Integer.parseInt(backpack.group(1)) + ENDER_PAGES - 1;
            return index < PAGES ? index : -1;
        }
        return -1;
    }

    private static int pageFromSlot(int slot) {
        if (slot >= ROW && slot < ROW * 2) {
            return slot - ROW;
        }
        if (slot >= ROW * 3 && slot < ROW * 5) {
            return slot - (ROW * 2);
        }
        return -1;
    }

    public static boolean draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (!active() || client.player == null || client.hasShiftDown()
                || !screen.getMenu().getCarried().isEmpty()) {
            return false;
        }
        Slot hovered = ((ContainerScreenAccessor) screen).qzaHoveredSlot();
        if (hovered == null || !hovered.hasItem() || hovered.container == client.player.getInventory()
                || !title(screen).equals(STORAGE)) {
            return false;
        }
        int index = pageFromSlot(hovered.getContainerSlot());
        Page page = index < 0 ? null : pages()[index];
        if (page == null) {
            return false;
        }

        int rows = page.rows();
        int x = mouseX + PANEL_W + 8 >= screen.width ? mouseX - PANEL_W - 12 : mouseX + 8;
        int y = Math.max(0, mouseY - 16);
        graphics.nextStratum();
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0f, 0f, PANEL_W, (rows * SLOT) + TOP_H, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y + (rows * SLOT) + TOP_H, 0f, BOTTOM_V,
                PANEL_W, BOTTOM_H, 256, 256);
        Font font = client.font;
        graphics.text(font, page.name(), x + 8, y + 6, TITLE_COLOUR, false);
        for (int i = ROW; i < page.items().length; i++) {
            ItemStack stack = page.items()[i];
            if (stack.isEmpty()) {
                continue;
            }
            int itemX = x + 8 + (((i - ROW) % ROW) * SLOT);
            int itemY = y + SLOT + (((i - ROW) / ROW) * SLOT);
            graphics.item(stack, itemX, itemY);
            graphics.itemDecorations(font, stack, itemX, itemY);
        }
        return true;
    }

    private static Path file() {
        return ConfigManager.qzaDir().resolve("storage-preview.json");
    }

    private static DynamicOps<JsonElement> ops() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? null : client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    private static void load() {
        DynamicOps<JsonElement> ops = ops();
        if (ops == null) {
            return;
        }
        loaded = true;
        Path path = file();
        if (!Files.isRegularFile(path)) {
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
                Page[] pages = new Page[PAGES];
                for (int i = 0; i < PAGES && i < array.size(); i++) {
                    if (array.get(i).isJsonObject()) {
                        pages[i] = readPage(ops, array.get(i).getAsJsonObject());
                    }
                }
                byProfile.put(entry.getKey(), pages);
            }
        } catch (IOException | RuntimeException e) {
            QZA.LOGGER.warn("Could not read storage-preview.json", e);
        }
    }

    private static Page readPage(DynamicOps<JsonElement> ops, JsonObject json) {
        JsonArray array = json.getAsJsonArray("items");
        ItemStack[] items = new ItemStack[array.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = ItemStack.OPTIONAL_CODEC.parse(ops, array.get(i)).result().orElse(ItemStack.EMPTY);
        }
        return new Page(json.has("name") ? json.get("name").getAsString() : "", items);
    }

    private static void save() {
        DynamicOps<JsonElement> ops = ops();
        if (ops == null) {
            return;
        }
        JsonObject profiles = new JsonObject();
        for (Map.Entry<String, Page[]> entry : byProfile.entrySet()) {
            JsonArray array = new JsonArray();
            for (Page page : entry.getValue()) {
                if (page == null) {
                    array.add(JsonNull.INSTANCE);
                    continue;
                }
                JsonObject json = new JsonObject();
                json.addProperty("name", page.name());
                JsonArray items = new JsonArray();
                for (ItemStack stack : page.items()) {
                    items.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).result().orElse(new JsonObject()));
                }
                json.add("items", items);
                array.add(json);
            }
            profiles.add(entry.getKey(), array);
        }
        JsonObject root = new JsonObject();
        root.addProperty("profile", profile);
        root.add("profiles", profiles);
        String text = GSON.toJson(root);
        Path path = file();
        writing = writing.thenRunAsync(() -> {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, text, StandardCharsets.UTF_8);
            } catch (IOException e) {
                QZA.LOGGER.warn("Could not save storage-preview.json", e);
            }
        });
    }
}
