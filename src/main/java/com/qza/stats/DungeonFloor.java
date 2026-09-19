package com.qza.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DungeonFloor {
    public static final String DEFAULT = "m7";

    public static final List<String> KEYS = build();

    private DungeonFloor() {
    }

    private static List<String> build() {
        List<String> keys = new ArrayList<>();
        for (int floor = 1; floor <= 7; floor++) {
            keys.add("f" + floor);
        }
        for (int floor = 1; floor <= 7; floor++) {
            keys.add("m" + floor);
        }
        return List.copyOf(keys);
    }

    public static final List<String> NUMBERS = List.of("1", "2", "3", "4", "5", "6", "7");

    public static String key(int floor, boolean master) {
        int safe = Math.max(1, Math.min(7, floor));
        return (master ? "m" : "f") + safe;
    }

    public static String normalise(String key) {
        if (key == null) {
            return DEFAULT;
        }
        String lower = key.toLowerCase(Locale.ROOT).trim();
        return KEYS.contains(lower) ? lower : DEFAULT;
    }

    public static String label(String key) {
        String safe = normalise(key);
        return safe.toUpperCase(Locale.ROOT);
    }

    public static boolean master(String key) {
        return normalise(key).startsWith("m");
    }

    public static int number(String key) {
        String safe = normalise(key);
        return safe.charAt(1) - '0';
    }

    public static String time(long millis) {
        if (millis <= 0) {
            return "none";
        }
        long totalSeconds = millis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    public static String timeFromSeconds(long seconds) {
        if (seconds <= 0) {
            return "any";
        }
        long minutes = seconds / 60L;
        long rest = seconds % 60L;
        return minutes + ":" + (rest < 10 ? "0" : "") + rest;
    }
}
