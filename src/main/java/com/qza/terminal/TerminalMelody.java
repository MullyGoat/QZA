package com.qza.terminal;

public final class TerminalMelody {
    private TerminalMelody() {
    }

    public static boolean isGo(int colour) {
        return colour == TerminalGrid.LIME || colour == TerminalGrid.GREEN;
    }

    public static int buttonColumn(TerminalGrid grid) {
        for (int column = grid.columns - 1; column >= 0; column--) {
            for (int row = 0; row < grid.rows; row++) {
                if (grid.cells.get((row * grid.columns) + column).filled()) {
                    return column;
                }
            }
        }
        return -1;
    }

    public static int targetColumn(TerminalGrid grid) {
        int buttons = buttonColumn(grid);
        if (buttons < 0) {
            return -1;
        }
        for (int position = 0; position < grid.cells.size(); position++) {
            if (position % grid.columns >= buttons) {
                continue;
            }
            TerminalCell cell = grid.cells.get(position);
            if (cell.filled() && isGo(cell.colour())) {
                return position % grid.columns;
            }
        }
        return -1;
    }

    public static int targetRow(TerminalGrid grid) {
        int buttons = buttonColumn(grid);
        if (buttons < 0) {
            return -1;
        }
        for (int position = 0; position < grid.cells.size(); position++) {
            if (position % grid.columns >= buttons) {
                continue;
            }
            TerminalCell cell = grid.cells.get(position);
            if (cell.filled() && isGo(cell.colour())) {
                return position / grid.columns;
            }
        }
        return -1;
    }

    public static int markerColumn(TerminalGrid grid) {
        for (int position = 0; position < grid.cells.size(); position++) {
            TerminalCell cell = grid.cells.get(position);
            if (cell.filled() && cell.colour() == TerminalGrid.MAGENTA) {
                return position % grid.columns;
            }
        }
        return -1;
    }

    public static int markerRow(TerminalGrid grid) {
        for (int position = 0; position < grid.cells.size(); position++) {
            TerminalCell cell = grid.cells.get(position);
            if (cell.filled() && cell.colour() == TerminalGrid.MAGENTA) {
                return position / grid.columns;
            }
        }
        return -1;
    }

    public static boolean aligned(TerminalGrid grid) {
        int target = targetColumn(grid);
        int marker = markerColumn(grid);
        if (target < 0 || marker < 0) {
            return true;
        }
        return marker == target;
    }

    public static int buttonAt(TerminalGrid grid) {
        int column = buttonColumn(grid);
        if (column < 0) {
            return -1;
        }

        int row = targetRow(grid);
        if (row >= 0) {
            int position = (row * grid.columns) + column;
            if (grid.cells.get(position).filled()) {
                return position;
            }
        }

        for (int i = 0; i < grid.rows; i++) {
            int position = (i * grid.columns) + column;
            TerminalCell cell = grid.cells.get(position);
            if (cell.filled() && isGo(cell.colour())) {
                return position;
            }
        }
        for (int i = 0; i < grid.rows; i++) {
            int position = (i * grid.columns) + column;
            if (grid.cells.get(position).filled()) {
                return position;
            }
        }
        return -1;
    }
}
