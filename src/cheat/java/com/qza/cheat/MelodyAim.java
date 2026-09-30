package com.qza.cheat;

import com.mojang.blaze3d.platform.Window;
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
import java.util.regex.Pattern;

public final class MelodyAim {
    private static final Pattern TITLE = Pattern.compile("^Click the button on time!$");

    private static Object screenSeen;
    private static boolean aimed;

    public record Spot(double x, double y, boolean ready) {
        public static final Spot WAIT = new Spot(0, 0, false);

        public Spot(double x, double y) {
            this(x, y, true);
        }
    }

    private MelodyAim() {
    }

    public static boolean isMelody(String title) {
        return title != null && TITLE.matcher(title.trim()).matches();
    }

    public static boolean isLitButton(String path) {
        return "lime_terracotta".equals(path) || "green_terracotta".equals(path);
    }

    public static void tick(Screen screen) {
        if (screen != screenSeen) {
            screenSeen = screen;
            aimed = false;
            OdinTerminal.screenChanged();
        }
        if (aimed || !CheatConfigManager.get().melodyAimEnabled) {
            return;
        }
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return;
        }
        if (container.getTitle() == null
                || !isMelody(com.qza.util.IgnUtil.stripCodes(container.getTitle().getString()))) {
            return;
        }

        int slot = litButton(container);
        if (slot < 0) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        if (window == null) {
            return;
        }

        Spot spot = spot(container, window, slot);
        if (spot != null && spot.ready() && move(client, window, spot)) {
            aimed = true;
        }
    }

    private static int litButton(AbstractContainerScreen<?> screen) {
        List<Slot> slots = screen.getMenu().slots;
        if (slots.isEmpty()) {
            return -1;
        }

        Container chest = slots.get(0).container;
        for (int i = 0; i < slots.size() && slots.get(i).container == chest; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack != null && !stack.isEmpty() && isLitButton(path(stack))) {
                return i;
            }
        }
        return -1;
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
        if (!(screen instanceof ContainerPosAccessor pos)) {
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

    public static void reset() {
        screenSeen = null;
        aimed = false;
    }
}
