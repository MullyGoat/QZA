package com.qza.util;

import net.minecraft.ChatFormatting;

public final class LegacyColours {
    private static final String CODES = "0123456789abcdef";
    private static final int[] RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private LegacyColours() {
    }

    public static boolean isColour(ChatFormatting format) {
        return CODES.indexOf(code(format)) >= 0;
    }

    public static char code(ChatFormatting format) {
        return format.toString().charAt(1);
    }

    public static char codeFor(int rgb) {
        for (int i = 0; i < RGB.length; i++) {
            if (RGB[i] == (rgb & 0xFFFFFF)) {
                return CODES.charAt(i);
            }
        }
        return 0;
    }
}
