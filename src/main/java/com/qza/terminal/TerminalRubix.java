package com.qza.terminal;

import java.util.ArrayList;
import java.util.List;

public final class TerminalRubix {
    public static final int CYCLE = 5;

    private static final int[] ORDER = {
            0xFFF9801D,
            0xFFFED83D,
            0xFF5E7C16,
            0xFF3C44AA,
            0xFFB02E26};

    private TerminalRubix() {
    }

    public static int indexOf(int colour) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == colour) {
                return i;
            }
        }
        return -1;
    }

    public static int step(int from, int to) {
        int forward = Math.floorMod(to - from, CYCLE);
        return forward > CYCLE / 2 ? forward - CYCLE : forward;
    }

    public static int cost(List<Integer> panes, int target) {
        int total = 0;
        for (int pane : panes) {
            total += Math.abs(step(pane, target));
        }
        return total;
    }

    public static int best(List<Integer> panes) {
        if (panes.isEmpty()) {
            return -1;
        }
        int bestTarget = 0;
        int bestCost = Integer.MAX_VALUE;
        for (int target = 0; target < CYCLE; target++) {
            int cost = cost(panes, target);
            if (cost < bestCost) {
                bestCost = cost;
                bestTarget = target;
            }
        }
        return bestTarget;
    }

    public static List<String> hints(TerminalGrid grid) {
        List<Integer> panes = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>(grid.cells.size());

        for (TerminalCell cell : grid.cells) {
            int index = cell.filled() ? indexOf(cell.colour()) : -1;
            indexes.add(index);
            if (index >= 0) {
                panes.add(index);
            }
        }

        List<String> out = new ArrayList<>(grid.cells.size());
        int target = best(panes);
        if (target < 0) {
            for (int i = 0; i < grid.cells.size(); i++) {
                out.add("");
            }
            return out;
        }

        for (int index : indexes) {
            if (index < 0) {
                out.add("");
                continue;
            }
            int step = step(index, target);
            out.add(step == 0 ? "" : (step > 0 ? "+" + step : String.valueOf(step)));
        }
        return out;
    }
}
