package com.qza.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TerminalRoles {
    private TerminalRoles() {
    }

    public static List<Integer> of(TerminalGrid grid, TerminalType type, String argument,
                                   TerminalTemplate template) {
        if (template == null || !template.roleColours || grid == null) {
            return null;
        }
        return switch (type) {
            case RUBIX -> rubix(grid, template);
            case NUMBERS -> numbers(grid, template);
            case SELECT -> matching(grid, TerminalGrid.named(argument), template.selectColour);
            case STARTS_WITH -> starting(grid, argument, template.startsWithColour);
            case MELODY -> melody(grid, template);
            default -> null;
        };
    }

    private static List<Integer> blank(TerminalGrid grid) {
        List<Integer> out = new ArrayList<>(grid.cells.size());
        for (int i = 0; i < grid.cells.size(); i++) {
            out.add(0);
        }
        return out;
    }

    private static List<Integer> rubix(TerminalGrid grid, TerminalTemplate template) {
        List<String> hints = TerminalRubix.hints(grid);
        List<Integer> out = blank(grid);
        for (int i = 0; i < out.size() && i < hints.size(); i++) {
            out.set(i, switch (hints.get(i)) {
                case "+1" -> template.rubixPlus1;
                case "+2" -> template.rubixPlus2;
                case "-1" -> template.rubixMinus1;
                case "-2" -> template.rubixMinus2;
                default -> 0;
            });
        }
        return out;
    }

    private static List<Integer> numbers(TerminalGrid grid, TerminalTemplate template) {
        List<Integer> pending = new ArrayList<>();
        for (TerminalCell cell : grid.cells) {
            if (cell.filled() && !cell.marked()) {
                pending.add(cell.count());
            }
        }
        pending.sort(null);

        int[] steps = {
                pending.size() > 0 ? pending.get(0) : Integer.MIN_VALUE,
                pending.size() > 1 ? pending.get(1) : Integer.MIN_VALUE,
                pending.size() > 2 ? pending.get(2) : Integer.MIN_VALUE};
        int[] colours = {template.order1, template.order2, template.order3};

        List<Integer> out = blank(grid);
        for (int i = 0; i < out.size(); i++) {
            TerminalCell cell = grid.cells.get(i);
            if (!cell.filled() || cell.marked()) {
                continue;
            }
            for (int rank = 0; rank < steps.length; rank++) {
                if (cell.count() == steps[rank]) {
                    out.set(i, colours[rank]);
                    break;
                }
            }
        }
        return out;
    }

    private static List<Integer> matching(TerminalGrid grid, int wanted, int colour) {
        if (wanted == 0) {
            return null;
        }
        List<Integer> out = blank(grid);
        for (int i = 0; i < out.size(); i++) {
            TerminalCell cell = grid.cells.get(i);
            if (cell.filled() && !cell.marked() && cell.colour() == wanted) {
                out.set(i, colour);
            }
        }
        return out;
    }

    private static List<Integer> starting(TerminalGrid grid, String letter, int colour) {
        if (letter == null || letter.isBlank()) {
            return null;
        }
        String wanted = letter.trim().substring(0, 1).toUpperCase(Locale.ROOT);

        List<Integer> out = blank(grid);
        for (int i = 0; i < out.size(); i++) {
            TerminalCell cell = grid.cells.get(i);
            if (!cell.filled() || cell.marked() || cell.name().isEmpty()) {
                continue;
            }
            if (cell.name().substring(0, 1).toUpperCase(Locale.ROOT).equals(wanted)) {
                out.set(i, colour);
            }
        }
        return out;
    }

    private static List<Integer> melody(TerminalGrid grid, TerminalTemplate template) {
        List<Integer> out = blank(grid);
        int ready = TerminalMelody.aligned(grid) ? TerminalMelody.buttonAt(grid) : -1;

        for (int i = 0; i < out.size(); i++) {
            TerminalCell cell = grid.cells.get(i);
            if (!cell.filled()) {
                continue;
            }
            if (cell.colour() == TerminalGrid.MAGENTA) {
                out.set(i, template.melodyMarker);
            } else if (i == ready) {
                out.set(i, template.melodyReady);
            }
        }
        return out;
    }
}
