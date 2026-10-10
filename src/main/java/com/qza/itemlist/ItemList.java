package com.qza.itemlist;

import com.qza.config.ConfigManager;
import com.qza.mixin.ContainerScreenAccessor;
import com.qza.util.IgnUtil;
import com.qza.util.SkyBlockArea;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ItemList {
    private static final String[] TERMINALS = {
            "Click in order!", "Select all the", "What starts with", "Change all to same color!",
            "Correct all the panes!", "Click the button on time!"};

    private static Screen attached;

    private ItemList() {
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> attach(screen));
        if (ConfigManager.get().itemListEnabled) {
            ItemRepo.ensureLoaded();
        }
    }

    private static void attach(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container) || !shouldShow(container)) {
            return;
        }
        ItemRepo.ensureLoaded();
        ItemListOverlay overlay = ItemListOverlay.INSTANCE;
        attached = screen;
        ScreenMouseEvents.allowMouseClick(screen).register((current, event) ->
                !enabled() || !overlay.mouseClicked(current, event));
        ScreenMouseEvents.allowMouseRelease(screen).register((current, event) ->
                !overlay.mouseReleased());
        ScreenMouseEvents.allowMouseScroll(screen).register((current, x, y, horizontal, vertical) ->
                !enabled() || !overlay.mouseScrolled(x, y, vertical));
        ScreenKeyboardEvents.allowKeyPress(screen).register((current, event) ->
                !enabled() || !overlay.keyPressed(current, event, hoveredSlotItem(container),
                        typingElsewhere(current)));
        ScreenEvents.remove(screen).register(current -> {
            overlay.closed();
            if (attached == current) {
                attached = null;
            }
        });
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                              int mouseX, int mouseY, float delta) {
        if (screen == attached && enabled()) {
            ItemListOverlay.INSTANCE.render(screen, graphics, mouseX, mouseY, delta, containerRight(screen));
        }
    }

    public static boolean charTyped(Screen screen, CharacterEvent event) {
        return screen != null && screen == attached && enabled() && ItemListOverlay.INSTANCE.charTyped(event);
    }

    static boolean typingElsewhere(Screen screen) {
        GuiEventListener focused = screen.getFocused();
        if (focused instanceof EditBox box) {
            return box.isFocused();
        }
        return focused instanceof RecipeBookComponent<?> book && book.isFocused();
    }

    private static boolean enabled() {
        return ConfigManager.get().itemListEnabled;
    }

    private static boolean shouldShow(AbstractContainerScreen<?> screen) {
        if (!enabled() || !SkyBlockArea.onSkyBlock()) {
            return false;
        }
        if (screen instanceof InventoryScreen && !ConfigManager.get().itemListInventory) {
            return false;
        }
        String title = screen.getTitle() == null ? "" : IgnUtil.stripCodes(screen.getTitle().getString());
        for (String terminal : TERMINALS) {
            if (title.startsWith(terminal)) {
                return false;
            }
        }
        return true;
    }

    private static int containerRight(AbstractContainerScreen<?> screen) {
        if (screen instanceof ContainerScreenAccessor accessor) {
            return accessor.qzaGuiLeft() + accessor.qzaGuiWidth() + 4;
        }
        return screen.width / 2 + 92;
    }

    private static RepoItem hoveredSlotItem(AbstractContainerScreen<?> screen) {
        if (!(screen instanceof ContainerScreenAccessor accessor)) {
            return null;
        }
        Slot slot = accessor.qzaHoveredSlot();
        if (slot == null || !slot.hasItem()) {
            return null;
        }
        return ItemRepo.item(skyblockId(slot.getItem()));
    }

    static String skyblockId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr("id", "").replace(':', '-');
    }
}
