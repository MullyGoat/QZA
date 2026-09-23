package com.qza.terminal;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TerminalGrid {
    public static final int COLUMNS = 9;

    private static final Map<String, Integer> DYES = new LinkedHashMap<>();

    static {
        DYES.put("white", 0xFFF9FFFE);
        DYES.put("orange", 0xFFF9801D);
        DYES.put("magenta", 0xFFC74EBD);
        DYES.put("light_blue", 0xFF3AB3DA);
        DYES.put("yellow", 0xFFFED83D);
        DYES.put("lime", 0xFF80C71F);
        DYES.put("pink", 0xFFF38BAA);
        DYES.put("gray", 0xFF474F52);
        DYES.put("light_gray", 0xFF9D9D97);
        DYES.put("cyan", 0xFF169C9C);
        DYES.put("purple", 0xFF8932B8);
        DYES.put("blue", 0xFF3C44AA);
        DYES.put("brown", 0xFF835432);
        DYES.put("green", 0xFF5E7C16);
        DYES.put("red", 0xFFB02E26);
        DYES.put("black", 0xFF1D1D21);
    }

    public final int rows;
    public final List<TerminalCell> cells;
    public final boolean counts;

    private TerminalGrid(int rows, List<TerminalCell> cells) {
        this.rows = rows;
        this.cells = cells;

        boolean varied = false;
        for (TerminalCell cell : cells) {
            if (cell.filled() && cell.count() > 1) {
                varied = true;
                break;
            }
        }
        this.counts = varied;
    }

    public static TerminalGrid of(int rows, List<TerminalCell> cells) {
        return new TerminalGrid(rows, cells);
    }

    public static TerminalGrid read(AbstractContainerScreen<?> screen, TerminalType type) {
        List<Slot> slots = screen.getMenu().slots;
        if (slots.isEmpty()) {
            return null;
        }

        int container = chestSlots(slots);
        if (container < COLUMNS || container % COLUMNS != 0) {
            return null;
        }

        int rows = container / COLUMNS;
        List<TerminalCell> cells = new ArrayList<>(container);
        for (int i = 0; i < container; i++) {
            cells.add(cell(i, slots.get(i).getItem(), type));
        }
        return new TerminalGrid(rows, cells);
    }

    private static int chestSlots(List<Slot> slots) {
        Container chest = slots.get(0).container;
        int count = 0;
        while (count < slots.size() && slots.get(count).container == chest) {
            count++;
        }
        return count;
    }

    private static TerminalCell cell(int index, ItemStack stack, TerminalType type) {
        if (stack == null || stack.isEmpty()) {
            return TerminalCell.empty(index);
        }

        String path = path(stack);
        if (filler(path, stack)) {
            return TerminalCell.empty(index);
        }

        return new TerminalCell(index, true, colour(path), stripped(stack),
                stack.getCount(), done(path, stack, type));
    }

    private static boolean done(String path, ItemStack stack, TerminalType type) {
        if (stack.hasFoil()) {
            return true;
        }
        if (type != TerminalType.NUMBERS && type != TerminalType.PANES) {
            return false;
        }
        return path.startsWith("lime_") || path.startsWith("green_");
    }

    private static boolean filler(String path, ItemStack stack) {
        if (!path.equals("black_stained_glass_pane")) {
            return false;
        }
        return stripped(stack).isBlank();
    }

    private static String stripped(ItemStack stack) {
        return com.qza.util.IgnUtil.stripCodes(stack.getHoverName().getString()).trim();
    }

    private static String path(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.getPath();
    }

    public TerminalGrid onlyNext(int shown) {
        if (shown <= 0) {
            return this;
        }

        List<Integer> pending = new ArrayList<>();
        for (TerminalCell cell : cells) {
            if (cell.filled() && !cell.marked()) {
                pending.add(cell.count());
            }
        }
        pending.sort(null);

        int cut = pending.size() <= shown
                ? Integer.MAX_VALUE : pending.get(shown - 1);

        List<TerminalCell> out = new ArrayList<>(cells.size());
        for (TerminalCell cell : cells) {
            boolean keep = cell.filled() && !cell.marked() && cell.count() <= cut;
            out.add(keep ? cell : cell.hidden());
        }
        return new TerminalGrid(rows, out);
    }

    public static int colour(String path) {
        String text = path == null ? "" : path.toLowerCase(Locale.ROOT);
        int best = 0;
        Integer found = null;
        for (Map.Entry<String, Integer> entry : DYES.entrySet()) {
            String dye = entry.getKey();
            if (text.startsWith(dye + "_") && dye.length() > best) {
                best = dye.length();
                found = entry.getValue();
            }
        }
        return found == null ? 0 : found;
    }
}
