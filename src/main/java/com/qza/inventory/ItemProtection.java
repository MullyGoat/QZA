package com.qza.inventory;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.chat.ChatKeybind;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.gui.QZAScreen;
import com.qza.itemlist.ItemListOverlay;
import com.qza.keybind.CommandKeybinds;
import com.qza.mixin.ContainerScreenAccessor;
import com.qza.mixin.CustomDataAccessor;
import com.qza.util.ChatUtil;
import com.qza.util.DungeonState;
import com.qza.util.SkyBlockArea;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ItemProtection {
    public static final Identifier STAR = Identifier.fromNamespaceAndPath("qza", "item_protection");

    public static final String TOP_LEFT = "top_left";
    public static final String BOTTOM_LEFT = "bottom_left";
    public static final String TOP_RIGHT = "top_right";
    public static final String BOTTOM_RIGHT = "bottom_right";
    public static final List<String> CORNERS = List.of(TOP_LEFT, BOTTOM_LEFT, TOP_RIGHT, BOTTOM_RIGHT);

    private static final int SLOT = 16;
    private static final int STAR_SIZE = 6;

    private static final int OUTSIDE_SLOT = -999;
    private static final int OFFHAND_SLOT = 40;
    private static final int HOTBAR_SLOTS = 9;
    private static final long WARN_MS = 1500L;

    private static final List<String> OWN_MENU_STARTS = List.of("Storage", "Rift Storage", "Ender Chest", "Chest",
            "Large Chest", "Trapped Chest", "Wardrobe", "Pets", "Your Equipment", "Weapon Rack", "Armor Stand",
            "Accessory Bag", "Fishing Bag", "Potion Bag", "Quiver", "Sack of Sacks");
    private static final List<String> OWN_MENU_WORDS = List.of("Backpack", "Equipment", "Armor Sets", "Loadout",
            "Shelves");
    private static final List<String> AUCTION_MENUS = List.of("Create Auction", "Create BIN Auction");

    private static boolean capturing;
    private static List<String> cachedFrom;
    private static int cachedSize = -1;
    private static Set<String> cached = Set.of();
    private static long warnedAt;

    private ItemProtection() {
    }

    public static void init() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!level.isClientSide() || !(entity instanceof ItemFrame frame) || !frame.getItem().isEmpty()) {
                return InteractionResult.PASS;
            }
            String area = SkyBlockArea.current();
            if (!area.equals("Private Island") && !area.equals("Garden")) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getItemInHand(hand);
            if (!blocking() || !isProtected(held)) {
                return InteractionResult.PASS;
            }
            warn(held, "put in an item frame");
            return InteractionResult.FAIL;
        });
    }

    public static boolean enabled() {
        return ConfigManager.get().itemProtectionEnabled;
    }

    private static boolean blocking() {
        return enabled() && SkyBlockArea.onSkyBlock();
    }

    public static String uuid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return "";
        }
        CompoundTag tag = ((CustomDataAccessor) (Object) data).qzaTag();
        return tag == null ? "" : tag.getStringOr("uuid", "");
    }

    public static String skyblockId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return "";
        }
        CompoundTag tag = ((CustomDataAccessor) (Object) data).qzaTag();
        return tag == null ? "" : tag.getStringOr("id", "");
    }

    private static Set<String> protectedSet() {
        List<String> list = ConfigManager.get().protectedItems;
        if (list == null) {
            return Set.of();
        }
        if (list != cachedFrom || list.size() != cachedSize) {
            cached = new HashSet<>(list);
            cachedFrom = list;
            cachedSize = list.size();
        }
        return cached;
    }

    public static boolean isProtected(ItemStack stack) {
        if (!enabled()) {
            return false;
        }
        String id = uuid(stack);
        return !id.isEmpty() && protectedSet().contains(id);
    }

    public static int count() {
        return protectedSet().size();
    }

    public static void toggle(ItemStack stack) {
        QZAConfig cfg = ConfigManager.get();
        if (!SkyBlockArea.onSkyBlock()) {
            ChatUtil.error("Items can only be protected on SkyBlock.");
            return;
        }
        if (stack == null || stack.isEmpty()) {
            ChatUtil.error("Hold an item, or hover one in your inventory, to protect it.");
            return;
        }
        String id = uuid(stack);
        if (id.isEmpty()) {
            ChatUtil.error("That item can't be protected because it has no SkyBlock UUID.");
            return;
        }
        if (cfg.protectedItems == null) {
            cfg.protectedItems = new ArrayList<>();
        }
        boolean added = !cfg.protectedItems.remove(id);
        if (added) {
            cfg.protectedItems.add(id);
        }
        cachedSize = -1;
        ConfigManager.save();
        if (!cfg.itemProtectionMessages) {
            return;
        }
        String name = stack.getHoverName().getString();
        if (added) {
            ChatUtil.success("Protected " + name + ".");
        } else {
            ChatUtil.info(name + " is no longer protected.");
        }
    }

    public static void toggleHeld() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            toggle(player.getMainHandItem());
        }
    }

    public static String keyLabel() {
        return CommandKeybinds.keyLabel(ConfigManager.get().itemProtectionKey);
    }

    private static boolean bound() {
        String key = ConfigManager.get().itemProtectionKey;
        return key != null && !key.isBlank();
    }

    public static boolean capturing() {
        return capturing;
    }

    public static void startCapture() {
        capturing = true;
    }

    public static boolean onKey(KeyEvent event, int action) {
        QZAConfig cfg = ConfigManager.get();
        if (capturing) {
            if (!(Mc.screen() instanceof QZAScreen)) {
                capturing = false;
                return false;
            }
            if (action != InputConstants.PRESS) {
                return false;
            }
            capturing = false;
            int code = event.key();
            if (code != InputConstants.KEY_ESCAPE) {
                cfg.itemProtectionKey = code == InputConstants.KEY_DELETE || code == InputConstants.KEY_BACKSPACE
                        ? "" : InputConstants.getKey(event).getName();
                ConfigManager.save();
            }
            ChatKeybind.swallowNextChar(event);
            return true;
        }

        if (action != InputConstants.PRESS || !enabled() || !bound()
                || !InputConstants.getKey(event).getName().equals(cfg.itemProtectionKey)) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || Mc.overlay() != null) {
            return false;
        }
        Screen screen = Mc.screen();
        if (screen == null) {
            toggle(client.player.getMainHandItem());
            return false;
        }
        if (!(screen instanceof AbstractContainerScreen<?> container) || typing(screen)) {
            return false;
        }
        Slot slot = ((ContainerScreenAccessor) container).qzaHoveredSlot();
        if (slot == null || !slot.hasItem()) {
            return false;
        }
        if (slot.container != client.player.getInventory() && !ownMenu(screen.getTitle().getString())) {
            ChatUtil.error("You can only protect items in your own inventory or storage.");
            return false;
        }
        toggle(slot.getItem());
        return false;
    }

    private static boolean typing(Screen screen) {
        if (ItemListOverlay.INSTANCE.searchFocused()) {
            return true;
        }
        return screen.getFocused() instanceof EditBox box && box.isFocused();
    }

    private static boolean ownMenu(String title) {
        for (String start : OWN_MENU_STARTS) {
            if (title.startsWith(start)) {
                return true;
            }
        }
        for (String word : OWN_MENU_WORDS) {
            if (title.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public static boolean blockDrop(ItemStack held) {
        if (!blocking() || !isProtected(held)) {
            return false;
        }
        warn(held, "dropped");
        return true;
    }

    public static boolean blockClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button,
                                     ContainerInput input) {
        if (!blocking()) {
            return false;
        }
        AbstractContainerMenu menu = screen.getMenu();
        if (slotId == OUTSIDE_SLOT) {
            ItemStack carried = menu.getCarried();
            if (isProtected(carried)) {
                warn(carried, "dropped");
                return true;
            }
            return false;
        }
        if (slot == null) {
            return false;
        }
        ItemStack stack = slot.getItem();
        if (input == ContainerInput.THROW) {
            if (isProtected(stack)) {
                warn(stack, "dropped");
                return true;
            }
            return false;
        }
        String risk = riskyMenu(screen.getTitle().getString(), menu, slot);
        if (risk == null) {
            return false;
        }
        if (isProtected(stack)) {
            warn(stack, risk);
            return true;
        }
        if (input == ContainerInput.SWAP) {
            ItemStack swapped = hotbarItem(button);
            if (isProtected(swapped)) {
                warn(swapped, risk);
                return true;
            }
        }
        return false;
    }

    private static ItemStack hotbarItem(int button) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || ((button < 0 || button >= HOTBAR_SLOTS) && button != OFFHAND_SLOT)) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(button);
    }

    private static String riskyMenu(String title, AbstractContainerMenu menu, Slot slot) {
        if (title.equals("Salvage Items")) {
            return "salvaged";
        }
        if (title.startsWith("You  ")) {
            return "traded";
        }
        if (title.endsWith("Auction House") || AUCTION_MENUS.contains(title)) {
            return "auctioned";
        }
        if (title.equals("Chest") && DungeonState.inDungeon()) {
            return "put in a dungeon chest";
        }
        if (menu instanceof ChestMenu && sellMenu(menu) && !sellButton(slot.getItem())) {
            return "sold";
        }
        return null;
    }

    private static boolean sellMenu(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.hasItem() && sellButton(slot.getItem())) {
                return true;
            }
        }
        return false;
    }

    private static boolean sellButton(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String name = stack.getHoverName().getString();
        if (name.equals("Sell Item") || name.equals("Sell Inventory")) {
            return true;
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return false;
        }
        for (Component line : lore.lines()) {
            if (line.getString().toLowerCase(Locale.ROOT).contains("buyback")) {
                return true;
            }
        }
        return false;
    }

    private static void warn(ItemStack stack, String what) {
        if (!ConfigManager.get().itemProtectionMessages) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - warnedAt < WARN_MS) {
            return;
        }
        warnedAt = now;
        ChatUtil.error(stack.getHoverName().getString() + " is protected, so it wasn't " + what + ".");
    }

    public static void drawStar(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.itemProtectionStar || !isProtected(stack)) {
            return;
        }
        String corner = cfg.itemProtectionCorner;
        boolean left = TOP_LEFT.equals(corner) || BOTTOM_LEFT.equals(corner);
        boolean bottom = BOTTOM_LEFT.equals(corner) || BOTTOM_RIGHT.equals(corner);
        int starX = left ? x : x + SLOT - STAR_SIZE;
        int starY = bottom ? y + SLOT - STAR_SIZE : y;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, STAR, starX, starY, STAR_SIZE, STAR_SIZE);
    }

    public static String cornerLabel(String corner) {
        return switch (corner == null ? TOP_RIGHT : corner) {
            case TOP_LEFT -> "Top Left";
            case BOTTOM_LEFT -> "Bottom Left";
            case BOTTOM_RIGHT -> "Bottom Right";
            default -> "Top Right";
        };
    }
}
