package com.qza.cheat;

import com.mojang.blaze3d.platform.Window;
import com.qza.QZA;
import com.qza.cheat.mixin.ContainerPosAccessor;
import com.qza.cheat.mixin.MouseHandlerAccessor;
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
    private static final double MOVED = 1.0;

    private static Object screenSeen;
    private static int aimedSlot = -1;
    private static Spot aimedSpot;

    public record Spot(double x, double y, boolean ready) {
        public static final Spot WAIT = new Spot(0, 0, false);

        public Spot(double x, double y) {
            this(x, y, true);
        }

        boolean near(Spot other) {
            return other != null && Math.abs(x - other.x) < MOVED && Math.abs(y - other.y) < MOVED;
        }
    }

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

        for (int row = rows - 1; row >= 0; row--) {
            if (cells[(row * COLUMNS) + buttons] == GO) {
                return (row * COLUMNS) + buttons;
            }
        }

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < buttons; column++) {
                if (cells[(row * COLUMNS) + column] == GO) {
                    return cells[(row * COLUMNS) + buttons] != NONE ? (row * COLUMNS) + buttons : -1;
                }
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
        if (screen != screenSeen) {
            screenSeen = screen;
            aimedSlot = -1;
            aimedSpot = null;
            OdinTerminal.screenChanged();
        }
        if (!CheatConfigManager.get().melodyAimEnabled) {
            return;
        }
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return;
        }
        if (container.getTitle() == null
                || !isMelody(com.qza.util.IgnUtil.stripCodes(container.getTitle().getString()))) {
            return;
        }

        int slot = activeButton(container);
        if (slot < 0) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        if (window == null) {
            return;
        }

        Spot spot = spot(container, window, slot);
        if (spot == null || !spot.ready()) {
            return;
        }
        if (slot == aimedSlot && spot.near(aimedSpot)) {
            return;
        }

        if (move(client, window, spot)) {
            aimedSlot = slot;
            aimedSpot = spot;
        }
    }

    private static int activeButton(AbstractContainerScreen<?> screen) {
        List<Slot> slots = screen.getMenu().slots;
        if (slots.isEmpty()) {
            return -1;
        }

        Container chest = slots.get(0).container;
        int size = 0;
        while (size < slots.size() && slots.get(size).container == chest) {
            size++;
        }
        if (size < COLUMNS || size % COLUMNS != 0) {
            return -1;
        }

        int[] cells = new int[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = slots.get(i).getItem();
            cells[i] = stack == null || stack.isEmpty()
                    ? NONE : classify(path(stack), name(stack));
        }
        return buttonSlot(cells, size / COLUMNS);
    }

    private static Spot spot(AbstractContainerScreen<?> screen, Window window, int slot) {
        Spot odin = OdinTerminal.melodySpot(screen, slot);
        if (odin != null) {
            return odin;
        }
        Spot noamm = NoammTerminal.melodySpot(window, slot);
        if (noamm != null) {
            return noamm;
        }
        if (!(screen instanceof ContainerPosAccessor pos) || slot >= screen.getMenu().slots.size()) {
            return null;
        }
        Slot target = screen.getMenu().slots.get(slot);
        return new Spot(pos.qzaLeftPos() + target.x + 8.0, pos.qzaTopPos() + target.y + 8.0);
    }

    private static boolean move(Minecraft client, Window window, Spot spot) {
        int guiWidth = window.getGuiScaledWidth();
        int guiHeight = window.getGuiScaledHeight();
        if (guiWidth <= 0 || guiHeight <= 0) {
            return false;
        }

        double x = spot.x() * window.getScreenWidth() / guiWidth;
        double y = spot.y() * window.getScreenHeight() / guiHeight;
        GLFW.glfwSetCursorPos(window.handle(), x, y);
        if (client.mouseHandler instanceof MouseHandlerAccessor mouse) {
            mouse.qzaSetXpos(x);
            mouse.qzaSetYpos(y);
        }
        return true;
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
        screenSeen = null;
        aimedSlot = -1;
        aimedSpot = null;
    }
}
