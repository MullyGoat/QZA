package com.qza.terminal;

import java.util.ArrayList;
import java.util.List;

public final class TerminalSamples {
    private static final int ROWS = 6;

    private static final int RED = TerminalGrid.RED;
    private static final int LIME = TerminalGrid.LIME;
    private static final int MAGENTA = TerminalGrid.MAGENTA;
    private static final int WHITE = 0xFFF9FFFE;
    private static final int BLUE = 0xFF3C44AA;
    private static final int YELLOW = 0xFFFED83D;
    private static final int PURPLE = 0xFF8932B8;
    private static final int ORANGE = 0xFFF9801D;
    private static final int CYAN = 0xFF169C9C;
    private static final int BROWN = 0xFF835432;

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
        for (int i = 0; i < ROWS * TerminalGrid.CHEST_COLUMNS; i++) {
            cells.add(TerminalCell.empty(i));
        }
        return cells;
    }

    private static int at(int row, int column) {
        return (row * TerminalGrid.CHEST_COLUMNS) + column;
    }

    private static void put(List<TerminalCell> cells, int row, int column,
                            int colour, String name, int count, boolean marked,
                            boolean button) {
        int index = at(row, column);
        cells.set(index, new TerminalCell(index, true, colour, name, count, marked, button));
    }

    private static TerminalGrid numbers() {
        List<TerminalCell> cells = blank();
        int value = 1;
        for (int row = 1; row <= 3; row++) {
            for (int column = 2; column <= 6; column++) {
                if (value > 14) {
                    break;
                }
                boolean done = value <= 3;
                put(cells, row, column, done ? LIME : RED, "Pane", value, done, false);
                value++;
            }
        }
        return TerminalGrid.of(ROWS, cells).crop();
    }

    private static TerminalGrid rubix() {
        int[] palette = {RED, LIME, BLUE, YELLOW, PURPLE, ORANGE};
        List<TerminalCell> cells = blank();
        int n = 0;
        for (int row = 1; row <= 3; row++) {
            for (int column = 2; column <= 6; column++) {
                put(cells, row, column, palette[(n * 3) % palette.length], "Wool", 1,
                        false, false);
                n++;
            }
        }
        return TerminalGrid.of(ROWS, cells).crop();
    }

    private static TerminalGrid melody() {
        int magentaColumn = 2;
        int limeColumn = 4;
        int currentRow = 1;

        List<TerminalCell> cells = blank();

        put(cells, 0, magentaColumn, MAGENTA, "Marker", 1, false, false);
        put(cells, 5, magentaColumn, MAGENTA, "Marker", 1, false, false);

        for (int row = 1; row <= 4; row++) {
            for (int column = 1; column <= 5; column++) {
                if (row != currentRow) {
                    put(cells, row, column, WHITE, "Note", 1, false, false);
                } else if (column == limeColumn) {
                    put(cells, row, column, LIME, "Target", 1, true, false);
                } else {
                    put(cells, row, column, RED, "Note", 1, false, false);
                }
            }
            put(cells, row, 7, row == currentRow ? LIME : RED, "Button", 1,
                    row == currentRow, true);
        }

        return TerminalGrid.of(ROWS, cells).crop();
    }

    private static TerminalGrid select() {
        int[] palette = {ORANGE, MAGENTA, LIME, CYAN, BROWN, PURPLE, LIME,
                LIME, BLUE, YELLOW, RED, LIME, WHITE, ORANGE};
        List<TerminalCell> cells = blank();
        int n = 0;
        for (int row = 1; row <= 4; row++) {
            for (int column = 1; column <= 7; column++) {
                int colour = palette[n % palette.length];
                put(cells, row, column, colour, "Item", 1, colour == LIME && n % 2 == 0,
                        false);
                n++;
            }
        }
        return TerminalGrid.of(ROWS, cells).crop();
    }

    private static TerminalGrid startsWith() {
        List<TerminalCell> cells = blank();
        int n = 0;
        for (int row = 1; row <= 4; row++) {
            for (int column = 1; column <= 7; column++) {
                String name = NAMES[n % NAMES.length];
                put(cells, row, column, 0, name, 1,
                        name.startsWith("S") && n % 2 == 0, false);
                n++;
            }
        }
        return TerminalGrid.of(ROWS, cells).crop();
    }

    private static TerminalGrid panes() {
        List<TerminalCell> cells = blank();
        int n = 0;
        for (int row = 1; row <= 3; row++) {
            for (int column = 2; column <= 6; column++) {
                boolean on = (n * 5 % 7) < 3;
                put(cells, row, column, on ? LIME : RED, "Pane", 1, on, false);
                n++;
            }
        }
        return TerminalGrid.of(ROWS, cells).crop();
    }
}
