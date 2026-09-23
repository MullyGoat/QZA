package com.qza.terminal;

import com.qza.config.ConfigManager;
import com.qza.util.IgnUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;

public final class TerminalOverlay {
    private static boolean forwarding;

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

    public static boolean render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                 int mouseX, int mouseY) {
        TerminalType type = type(screen);
        if (type == null) {
            return false;
        }
        TerminalGrid grid = TerminalGrid.read(screen);
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
                                MouseButtonEvent event, boolean doubleClick) {
        TerminalType type = type(screen);
        if (type == null) {
            return false;
        }
        TerminalGrid grid = TerminalGrid.read(screen);
        if (grid == null) {
            return false;
        }

        TerminalTemplate template = TerminalTemplates.forType(type);
        TerminalLayout layout = TerminalLayout.of(grid, template, screen.width, screen.height);

        int index = layout.indexAt(event.x(), event.y());
        if (index < 0 || index >= grid.cells.size()) {
            return true;
        }

        Slot slot = screen.getMenu().slots.get(index);
        MouseButtonEvent forwarded = new MouseButtonEvent(
                leftPos + slot.x + 8.0, topPos + slot.y + 8.0, event.buttonInfo());

        forwarding = true;
        try {
            screen.mouseClicked(forwarded, doubleClick);
        } finally {
            forwarding = false;
        }
        return true;
    }
}
