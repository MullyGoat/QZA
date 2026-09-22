package com.qza.waypoint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WaypointColour {
    private static final Map<String, Integer> NAMED = new LinkedHashMap<>();

    static {
        NAMED.put("red", 0xFF5555);
        NAMED.put("orange", 0xFFAA00);
        NAMED.put("yellow", 0xFFFF55);
        NAMED.put("lime", 0x55FF55);
        NAMED.put("green", 0x00AA00);
        NAMED.put("aqua", 0x55FFFF);
        NAMED.put("cyan", 0x00AAAA);
        NAMED.put("blue", 0x5555FF);
        NAMED.put("navy", 0x0000AA);
        NAMED.put("purple", 0xAA00AA);
        NAMED.put("magenta", 0xFF55FF);
        NAMED.put("pink", 0xFFAACC);
        NAMED.put("brown", 0xA9714B);
        NAMED.put("white", 0xFFFFFF);
        NAMED.put("gray", 0xAAAAAA);
        NAMED.put("black", 0x202020);
    }

    private static final int DEFAULT = 0x5555FF;

    private WaypointColour() {
    }

    public static List<String> names() {
        return List.copyOf(NAMED.keySet());
    }

    public static boolean known(String colour) {
        return rgb(colour) != null;
    }

    public static String tidy(String colour) {
        if (colour == null) {
            return "blue";
        }
        String text = colour.trim().toLowerCase(Locale.ROOT);
        if (text.equals("grey")) {
            return "gray";
        }
        return text;
    }

    public static String next(String colour) {
        List<String> all = names();
        int at = all.indexOf(tidy(colour));
        return all.get(at < 0 ? 0 : (at + 1) % all.size());
    }

    public static int argb(String colour) {
        Integer rgb = rgb(colour);
        return 0xFF000000 | (rgb == null ? DEFAULT : rgb);
    }

    private static Integer rgb(String colour) {
        if (colour == null) {
            return null;
        }
        String text = tidy(colour);
        Integer named = NAMED.get(text);
        if (named != null) {
            return named;
        }

        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
