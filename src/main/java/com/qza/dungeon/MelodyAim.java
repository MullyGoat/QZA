package com.qza.dungeon;

import com.mojang.blaze3d.platform.Window;
import com.qza.QZA;
import com.qza.config.ConfigManager;
import com.qza.mixin.ContainerPosAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MelodyAim {
    public static final int COLUMNS = 9;
    public static final int NONE = 0;
    public static final int PLAIN = 1;
    public static final int GO = 2;

    private static final Pattern TITLE = Pattern.compile("^Click the button on time!$");

    private static Object aimed;

    private MelodyAim() {
    }

    public static boolean isMelody(String title) {
        return title != null && TITLE.matcher(title.trim()).matches();
    }

    public static int buttonSlot(int[] cells, int rows) {
        if (cells == null || rows <= 0 || cells.length < rows * COLUMNS) {
            return -1;
        }

        int buttons = -1;
        for (int column = COLUMNS - 1; column >= 0 && buttons < 0; column--) {
            for (int row = 0; row < rows; row++) {
                if (cells[(row * COLUMNS) + column] != NONE) {
                    buttons = column;
                    break;
                }
            }
        }
        if (buttons < 0) {
            return -1;
        }

        int target = -1;
        for (int row = 0; row < rows && target < 0; row++) {
            for (int column = 0; column < buttons; column++) {
                if (cells[(row * COLUMNS) + column] == GO) {
                    target = row;
                    break;
                }
            }
        }

        if (target >= 0 && cells[(target * COLUMNS) + buttons] != NONE) {
            return (target * COLUMNS) + buttons;
        }
        for (int row = 0; row < rows; row++) {
            if (cells[(row * COLUMNS) + buttons] == GO) {
                return (row * COLUMNS) + buttons;
            }
        }
        for (int row = 0; row < rows; row++) {
            if (cells[(row * COLUMNS) + buttons] != NONE) {
                return (row * COLUMNS) + buttons;
            }
        }
        return -1;
    }

    public static int classify(String path, String name) {
        if (path == null || path.isEmpty()) {
            return NONE;
        }
        String text = path.toLowerCase(Locale.ROOT);
        if (text.equals("black_stained_glass_pane") && (name == null || name.isBlank())) {
            return NONE;
        }
        return text.startsWith("lime_") || text.startsWith("green_") ? GO : PLAIN;
    }

    public static void tick(Screen screen) {
        if (aimed != null && screen != aimed) {
            aimed = null;
        }
        if (!ConfigManager.get().melodyAimEnabled) {
            return;
        }
        if (!(screen instanceof AbstractContainerScreen<?> container) || screen == aimed) {
            return;
        }
        if (container.getTitle() == null
                || !isMelody(com.qza.util.IgnUtil.stripCodes(container.getTitle().getString()))) {
            return;
        }
        aim(container);
    }

    private static void aim(AbstractContainerScreen<?> screen) {
        List<Slot> slots = screen.getMenu().slots;
        if (slots.isEmpty()) {
            return;
        }

        Container chest = slots.get(0).container;
        int size = 0;
        while (size < slots.size() && slots.get(size).container == chest) {
            size++;
        }
        if (size < COLUMNS || size % COLUMNS != 0) {
            return;
        }

        int[] cells = new int[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = slots.get(i).getItem();
            cells[i] = stack == null || stack.isEmpty()
                    ? NONE : classify(path(stack), name(stack));
        }

        int slot = buttonSlot(cells, size / COLUMNS);
        if (slot < 0) {
            return;
        }

        float[] at = OdinTerminal.screenPos(slot);
        if (at == null) {
            at = vanillaPos(screen, slots.get(slot));
        }
        if (at == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        if (window == null) {
            return;
        }

        aimed = screen;
        double scale = Math.max(1, window.getGuiScale());
        double x = at[0] * scale;
        double y = at[1] * scale;
        long handle = window.handle();
        client.execute(() -> GLFW.glfwSetCursorPos(handle, x, y));
    }

    private static float[] vanillaPos(AbstractContainerScreen<?> screen, Slot slot) {
        if (!(screen instanceof ContainerPosAccessor pos)) {
            return null;
        }
        return new float[]{pos.qzaLeftPos() + slot.x + 8f, pos.qzaTopPos() + slot.y + 8f};
    }

    private static String path(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.getPath();
    }

    private static String name(ItemStack stack) {
        try {
            return com.qza.util.IgnUtil.stripCodes(stack.getHoverName().getString()).trim();
        } catch (Throwable e) {
            QZA.LOGGER.warn("Could not read a melody slot name", e);
            return "";
        }
    }

    public static void reset() {
        aimed = null;
    }
}
