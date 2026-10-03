package com.qza.itemlist;

public record Ingredient(String id, double count) {
    public static final String COINS = "SKYBLOCK_COIN";

    public static Ingredient parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        int colon = text.lastIndexOf(':');
        if (colon > 0 && colon < text.length() - 1) {
            try {
                return new Ingredient(text.substring(0, colon), Double.parseDouble(text.substring(colon + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return new Ingredient(text, 1);
    }

    public boolean isCoins() {
        return COINS.equals(id);
    }

    public String countText() {
        if (count >= 1_000_000) {
            return trim(count / 1_000_000) + "m";
        }
        if (count >= 10_000) {
            return trim(count / 1_000) + "k";
        }
        return trim(count);
    }

    public String fullCount() {
        if (count == Math.rint(count)) {
            return String.format("%,d", (long) count);
        }
        return String.format("%,.2f", count);
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        String text = String.format("%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
