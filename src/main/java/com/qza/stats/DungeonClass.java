package com.qza.stats;

import java.util.List;
import java.util.Locale;

public final class DungeonClass {
    public static final String HEALER = "healer";
    public static final String MAGE = "mage";
    public static final String BERSERK = "berserk";
    public static final String ARCHER = "archer";
    public static final String TANK = "tank";

    public static final List<String> ALL = List.of(HEALER, MAGE, BERSERK, ARCHER, TANK);

    private DungeonClass() {
    }

    public static String label(String raw) {
        if (raw == null || raw.isBlank()) {
            return "no class";
        }
        String lower = raw.toLowerCase(Locale.ROOT).trim();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public static String of(String raw) {
        if (raw == null) {
            return null;
        }
        String word = raw.toLowerCase(Locale.ROOT).trim();
        return switch (word) {
            case "healer", "heal" -> HEALER;
            case "mage" -> MAGE;
            case "berserk", "berserker", "bers", "berz", "zerk" -> BERSERK;
            case "archer", "arch" -> ARCHER;
            case "tank" -> TANK;
            default -> null;
        };
    }
}
