package com.qza.terminal;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.IgnUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;

public final class TerminalOverlay {
    private static boolean forwarding;
    private static double lastX;
    private static double lastY;
    private static boolean pressed;

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

    public static TerminalGrid shown(AbstractContainerScreen<?> screen, TerminalType type) {
        TerminalGrid grid = TerminalGrid.read(screen, type);
        if (grid == null) {
            return null;
        }
        return limit(grid.crop(), type, type.argument(title(screen)));
    }

    public static TerminalGrid limit(TerminalGrid grid, TerminalType type, String argument) {
        QZAConfig cfg = ConfigManager.get();
        return switch (type) {
            case NUMBERS -> cfg.terminalNumbersLimit
                    ? grid.onlyNext(Math.max(1, cfg.terminalNumbersShown)) : grid;
            case SELECT -> cfg.terminalSelectFilter
                    ? grid.onlyColour(TerminalGrid.named(argument)) : grid;
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
        TerminalGrid grid = shown(screen, type);
        if (grid == null) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.font == null) {
            return false;
        }

        TerminalTemplate template = TerminalTemplates.forType(type);
        TerminalLayout layout = TerminalLayout.of(grid, template, screen.width, screen.height);

        TerminalPainter.draw(graphics, client.font, grid, template, layout,
                title(screen), layout.indexAt(mouseX, mouseY));
        return true;
    }

    public static boolean click(AbstractContainerScreen<?> screen, int leftPos, int topPos,
                                MouseButtonEvent event) {
        TerminalType type = type(screen);
        if (type == null) {
            return false;
        }
        TerminalGrid grid = shown(screen, type);
        if (grid == null) {
            return false;
        }

        pressed = true;

        TerminalTemplate template = TerminalTemplates.forType(type);
        TerminalLayout layout = TerminalLayout.of(grid, template, screen.width, screen.height);

        int position = layout.indexAt(event.x(), event.y());
        if (position < 0 || position >= grid.cells.size()) {
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
    }
}
