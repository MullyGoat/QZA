package com.qza.terminal;

import com.mojang.blaze3d.platform.Window;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.IgnUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class TerminalOverlay {
    private static boolean forwarding;
    private static double lastX;
    private static double lastY;
    private static boolean pressed;
    private static Object aimedAt;
    private static Object openedScreen;
    private static long openedAt;

    private TerminalOverlay() {
    }

    private static String title(AbstractContainerScreen<?> screen) {
        if (screen.getTitle() == null) {
            return "";
        }
        return IgnUtil.stripCodes(screen.getTitle().getString()).trim();
    }

    private static TerminalType type(AbstractContainerScreen<?> screen) {
        if (forwarding || !ConfigManager.get().terminalGuiEnabled) {
            return null;
        }
        return TerminalType.of(title(screen));
    }

    public static TerminalGrid raw(AbstractContainerScreen<?> screen, TerminalType type) {
        TerminalGrid grid = TerminalGrid.read(screen, type);
        return grid == null ? null : grid.crop();
    }

    public static TerminalGrid shown(AbstractContainerScreen<?> screen, TerminalType type) {
        TerminalGrid grid = raw(screen, type);
        if (grid == null) {
            return null;
        }
        return limit(grid, type, type.argument(title(screen)));
    }

    public static TerminalGrid limit(TerminalGrid grid, TerminalType type, String argument) {
        QZAConfig cfg = ConfigManager.get();
        return switch (type) {
            case NUMBERS -> cfg.terminalNumbersLimit
                    ? grid.onlyNext(Math.max(1, cfg.terminalNumbersShown)) : grid;
            case PANES -> cfg.terminalHideDone ? grid.onlyPending() : grid;
            case SELECT -> cfg.terminalHideDone
                    ? grid.onlyColour(TerminalGrid.named(argument)) : grid;
            case STARTS_WITH -> cfg.terminalHideDone
                    ? grid.onlyInitial(argument) : grid;
            case MELODY -> cfg.terminalMelodyHold ? grid.holdMelody() : grid;
            default -> grid;
        };
    }

    public static boolean render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                 int mouseX, int mouseY) {
        TerminalType type = type(screen);
        if (type == null) {
            return false;
        }
        TerminalGrid source = raw(screen, type);
        if (source == null) {
            return false;
        }
        TerminalGrid grid = limit(source, type, type.argument(title(screen)));

        Minecraft client = Minecraft.getInstance();
        if (client.font == null) {
            return false;
        }

        TerminalTemplate template = TerminalTemplates.forType(type);
        TerminalLayout layout = TerminalLayout.of(grid, template, screen.width, screen.height);

        TerminalPainter.draw(graphics, client.font, grid, template, layout,
                title(screen), layout.indexAt(mouseX, mouseY),
                type.labelFor(template.label), hints(source, type),
                TerminalRoles.of(source, type, type.argument(title(screen)), template));

        aim(screen, type, source, layout);
        return true;
    }

    public static List<String> hints(TerminalGrid grid, TerminalType type) {
        if (type != TerminalType.RUBIX || !ConfigManager.get().terminalRubixHints) {
            return null;
        }
        return TerminalRubix.hints(grid);
    }

    public static int aimAt(TerminalGrid grid) {
        return TerminalMelody.buttonAt(grid);
    }

    public static boolean allowed(TerminalGrid grid, TerminalType type,
                                  int position, int button) {
        List<String> hints = hints(grid, type);
        if (hints == null || position < 0 || position >= hints.size()) {
            return true;
        }
        String hint = hints.get(position);
        if (hint.startsWith("+")) {
            return button == 0;
        }
        if (hint.startsWith("-")) {
            return button != 0;
        }
        return true;
    }

    public static void tick(Object screen) {
        if (aimedAt != null && screen != aimedAt) {
            aimedAt = null;
        }
        if (screen != openedScreen) {
            openedScreen = screen;
            openedAt = System.currentTimeMillis();
        }
    }

    private static boolean guarded() {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.terminalFirstClickProt || openedAt == 0L) {
            return false;
        }
        long wait = Math.max(0, Math.min(2000, cfg.terminalFirstClickMs));
        return System.currentTimeMillis() - openedAt < wait;
    }

    private static void aim(AbstractContainerScreen<?> screen, TerminalType type,
                            TerminalGrid grid, TerminalLayout layout) {
        if (type != TerminalType.MELODY || !ConfigManager.get().terminalMelodyAim) {
            return;
        }
        if (screen == aimedAt) {
            return;
        }

        int position = aimAt(grid);
        if (position < 0) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        if (window == null) {
            return;
        }

        aimedAt = screen;

        double scale = Math.max(1, window.getGuiScale());
        double x = (layout.cellX(position % grid.columns) + (layout.cell / 2.0)) * scale;
        double y = (layout.cellY(position / grid.columns) + (layout.cell / 2.0)) * scale;
        long handle = window.handle();

        client.execute(() -> GLFW.glfwSetCursorPos(handle, x, y));
    }

    public static boolean click(AbstractContainerScreen<?> screen, int leftPos, int topPos,
                                MouseButtonEvent event) {
        TerminalType type = type(screen);
        if (type == null) {
            return false;
        }
        TerminalGrid source = raw(screen, type);
        if (source == null) {
            return false;
        }
        TerminalGrid grid = limit(source, type, type.argument(title(screen)));

        pressed = true;

        if (guarded()) {
            return true;
        }

        TerminalTemplate template = TerminalTemplates.forType(type);
        TerminalLayout layout = TerminalLayout.of(grid, template, screen.width, screen.height);

        int position = layout.indexAt(event.x(), event.y());
        if (position < 0 || position >= grid.cells.size()) {
            return true;
        }
        if (!allowed(source, type, position, event.button())) {
            return true;
        }
        TerminalCell cell = grid.cells.get(position);
        if (cell.index() < 0 || cell.index() >= screen.getMenu().slots.size()) {
            return true;
        }

        Slot slot = screen.getMenu().slots.get(cell.index());
        lastX = leftPos + slot.x + 8.0;
        lastY = topPos + slot.y + 8.0;

        forward(screen, event, true);
        return true;
    }

    public static boolean release(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
        if (type(screen) == null) {
            return false;
        }
        if (!pressed) {
            return true;
        }
        pressed = false;
        forward(screen, event, false);
        return true;
    }

    public static boolean drag(AbstractContainerScreen<?> screen) {
        return type(screen) != null;
    }

    private static void forward(AbstractContainerScreen<?> screen, MouseButtonEvent event,
                                boolean down) {
        MouseButtonEvent at = new MouseButtonEvent(lastX, lastY, event.buttonInfo());
        forwarding = true;
        try {
            if (down) {
                screen.mouseClicked(at, false);
            } else {
                screen.mouseReleased(at);
            }
        } finally {
            forwarding = false;
        }
    }

    public static void reset() {
        forwarding = false;
        pressed = false;
        aimedAt = null;
    }
}
