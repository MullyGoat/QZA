package com.qza.terminal;

public final class TerminalPalette {
    public static final int COLUMNS = 12;
    public static final int HUE_ROWS = 6;
    public static final int ALPHA_STEPS = 12;

    private static final float[][] LEVELS = {
            {1.00f, 0.32f},
            {1.00f, 0.55f},
            {1.00f, 0.78f},
            {0.85f, 1.00f},
            {0.50f, 1.00f}};

    private static final int[] COLOURS = build();

    private TerminalPalette() {
    }

    public static int size() {
        return COLOURS.length;
    }

    public static int at(int index) {
        return index < 0 || index >= COLOURS.length ? 0 : COLOURS[index];
    }

    public static int alphaAt(int step) {
        int clamped = Math.max(0, Math.min(ALPHA_STEPS - 1, step));
        return Math.round(255f * clamped / (ALPHA_STEPS - 1));
    }

    private static int[] build() {
        int[] out = new int[COLUMNS * HUE_ROWS];

        for (int column = 0; column < COLUMNS; column++) {
            int value = Math.round(255f * column / (COLUMNS - 1));
            out[column] = 0xFF000000 | (value << 16) | (value << 8) | value;
        }

        for (int row = 0; row < LEVELS.length; row++) {
            float saturation = LEVELS[row][0];
            float value = LEVELS[row][1];
            for (int column = 0; column < COLUMNS; column++) {
                out[((row + 1) * COLUMNS) + column] =
                        hsv(column / (float) COLUMNS, saturation, value);
            }
        }
        return out;
    }

    private static int hsv(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6f;
        int sector = (int) h;
        float f = h - sector;

        float p = value * (1f - saturation);
        float q = value * (1f - (saturation * f));
        float t = value * (1f - (saturation * (1f - f)));

        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> {
                r = value;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = value;
                b = p;
            }
            case 2 -> {
                r = p;
                g = value;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = value;
            }
            case 4 -> {
                r = t;
                g = p;
                b = value;
            }
            default -> {
                r = value;
                g = p;
                b = q;
            }
        }
        return 0xFF000000 | (byteOf(r) << 16) | (byteOf(g) << 8) | byteOf(b);
    }

    private static int byteOf(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255f)));
    }
}
