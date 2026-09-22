package com.qza.waypoint;

import java.util.Locale;

public final class WaypointSize {
    public static final int MAX = 51;

    private WaypointSize() {
    }

    public record Size(int width, int height, int depth) {
        public String text() {
            return height == 1 ? width + "x" + depth : width + "x" + height + "x" + depth;
        }
    }

    public static Size parse(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.trim().toLowerCase(Locale.ROOT).split("x");

        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int value = number(parts[i]);
            if (value < 1 || value > MAX) {
                return null;
            }
            numbers[i] = value;
        }

        return switch (numbers.length) {
            case 1 -> new Size(numbers[0], 1, numbers[0]);
            case 2 -> new Size(numbers[0], 1, numbers[1]);
            case 3 -> new Size(numbers[0], numbers[1], numbers[2]);
            default -> null;
        };
    }

    private static int number(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.length() > 3) {
            return -1;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return -1;
            }
        }
        return Integer.parseInt(trimmed);
    }
}
