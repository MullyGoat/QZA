package com.qza.terminal;

import java.util.ArrayList;
import java.util.List;

public final class TerminalSamples {
    private static final int ROWS = 6;

    private static final int RED = 0xFFB02E26;
    private static final int LIME = 0xFF80C71F;
    private static final int BLUE = 0xFF3C44AA;
    private static final int YELLOW = 0xFFFED83D;
    private static final int PURPLE = 0xFF8932B8;
    private static final int ORANGE = 0xFFF9801D;
    private static final int GREY = 0xFF9D9D97;
    private static final int PINK = 0xFFF38BAA;

    private static final String[] NAMES = {
            "Sponge", "Slimeball", "Sugar", "Stick", "Bone", "Bucket",
            "Shears", "Saddle", "Snowball", "Book", "Feather", "Emerald"};

    private TerminalSamples() {
    }

    public static TerminalGrid of(TerminalType type) {
        return switch (type) {
            case NUMBERS -> numbers();
            case RUBIX -> rubix();
            case MELODY -> melody();
            case SELECT -> select();
            case STARTS_WITH -> startsWith();
            case PANES -> panes();
        };
    }

    private static List<TerminalCell> blank() {
        List<TerminalCell> cells = new ArrayList<>();
        for (int i = 0; i < ROWS * TerminalGrid.COLUMNS; i++) {
            cells.add(TerminalCell.empty(i));
        }
        return cells;
    }

    private static int at(int row, int column) {
        return (row * TerminalGrid.COLUMNS) + column;
    }

    private static TerminalGrid numbers() {
        List<TerminalCell> cells = blank();
        int value = 1;
        for (int row = 1; row <= 4; row++) {
            for (int column = 2; column <= 6; column++) {
                if (value > 14) {
                    break;
                }
                boolean done = value <= 3;
                cells.set(at(row, column), new TerminalCell(at(row, column), true,
                        done ? LIME : RED, "Pane", value, done));
                value++;
            }
        }
        return TerminalGrid.of(ROWS, cells);
    }

    private static TerminalGrid rubix() {
        int[] palette = {RED, LIME, BLUE, YELLOW, PURPLE, ORANGE};
        List<TerminalCell> cells = blank();
        int n = 0;
        for (int row = 1; row <= 4; row++) {
            for (int column = 2; column <= 6; column++) {
                cells.set(at(row, column), new TerminalCell(at(row, column), true,
                        palette[(n * 3) % palette.length], "Wool", 1, false));
                n++;
            }
        }
        return TerminalGrid.of(ROWS, cells);
    }

    private static TerminalGrid melody() {
        List<TerminalCell> cells = blank();
        for (int row = 1; row <= 4; row++) {
            cells.set(at(row, 1), new TerminalCell(at(row, 1), true, GREY, "Marker", 1, row == 2));
            for (int column = 2; column <= 7; column++) {
                boolean lit = (row + column) % 4 == 0;
                cells.set(at(row, column), new TerminalCell(at(row, column), true,
                        lit ? LIME : GREY, "Note", 1, lit));
            }
        }
        for (int column = 2; column <= 7; column++) {
            cells.set(at(5, column), new TerminalCell(at(5, column), true,
                    column == 4 ? RED : GREY, "Beat", 1, column == 4));
        }
        return TerminalGrid.of(ROWS, cells);
    }

    private static TerminalGrid select() {
        int[] palette = {RED, LIME, BLUE, RED, YELLOW, RED, PURPLE, PINK};
        List<TerminalCell> cells = blank();
        int n = 0;
        for (int row = 1; row <= 4; row++) {
            for (int column = 1; column <= 7; column++) {
                int colour = palette[n % palette.length];
                cells.set(at(row, column), new TerminalCell(at(row, column), true,
                        colour, "Item", 1, colour == RED && n % 3 == 0));
                n++;
            }
        }
        return TerminalGrid.of(ROWS, cells);
    }

    private static TerminalGrid startsWith() {
        List<TerminalCell> cells = blank();
        int n = 0;
        for (int row = 1; row <= 4; row++) {
            for (int column = 1; column <= 7; column++) {
                String name = NAMES[n % NAMES.length];
                cells.set(at(row, column), new TerminalCell(at(row, column), true,
                        0, name, 1, name.startsWith("S") && n % 2 == 0));
                n++;
            }
        }
        return TerminalGrid.of(ROWS, cells);
    }

    private static TerminalGrid panes() {
        List<TerminalCell> cells = blank();
        int n = 0;
        for (int row = 1; row <= 4; row++) {
            for (int column = 2; column <= 6; column++) {
                boolean on = (n * 5 % 7) < 3;
                cells.set(at(row, column), new TerminalCell(at(row, column), true,
                        on ? LIME : RED, "Pane", 1, on));
                n++;
            }
        }
        return TerminalGrid.of(ROWS, cells);
    }
}
