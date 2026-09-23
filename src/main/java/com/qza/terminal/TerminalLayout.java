package com.qza.terminal;

public final class TerminalLayout {
    public static final int TITLE_H = 16;

    public final int x;
    public final int y;
    public final int width;
    public final int height;
    public final int rows;
    public final int cell;
    public final int gap;
    public final int pad;
    public final int titleH;

    private TerminalLayout(int x, int y, int width, int height, int rows,
                           int cell, int gap, int pad, int titleH) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rows = rows;
        this.cell = cell;
        this.gap = gap;
        this.pad = pad;
        this.titleH = titleH;
    }

    public static TerminalLayout of(TerminalGrid grid, TerminalTemplate template,
                                    int screenW, int screenH) {
        return at(grid, template, -1, -1, screenW, screenH);
    }

    public static TerminalLayout at(TerminalGrid grid, TerminalTemplate template,
                                    int left, int top, int screenW, int screenH) {
        int cell = template.cell;
        int gap = template.gap;
        int pad = template.pad;
        int titleH = template.showTitle ? TITLE_H : 0;

        int gridW = (TerminalGrid.COLUMNS * cell) + ((TerminalGrid.COLUMNS - 1) * gap);
        int gridH = (grid.rows * cell) + (Math.max(0, grid.rows - 1) * gap);

        int width = gridW + (pad * 2);
        int height = gridH + (pad * 2) + titleH;

        int x = left < 0 ? (screenW - width) / 2 : left;
        int y = top < 0 ? (screenH - height) / 2 : top;

        return new TerminalLayout(x, y, width, height, grid.rows, cell, gap, pad, titleH);
    }

    public int cellX(int column) {
        return x + pad + (column * (cell + gap));
    }

    public int cellY(int row) {
        return y + pad + titleH + (row * (cell + gap));
    }

    public int indexAt(double mouseX, double mouseY) {
        for (int row = 0; row < rows; row++) {
            int top = cellY(row);
            if (mouseY < top || mouseY >= top + cell) {
                continue;
            }
            for (int column = 0; column < TerminalGrid.COLUMNS; column++) {
                int left = cellX(column);
                if (mouseX >= left && mouseX < left + cell) {
                    return (row * TerminalGrid.COLUMNS) + column;
                }
            }
        }
        return -1;
    }
}
