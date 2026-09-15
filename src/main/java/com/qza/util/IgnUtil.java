package com.qza.util;

public final class IgnUtil {
    private IgnUtil() {
    }

    public static String stripCodes(String input) {
        if (input == null || input.indexOf('§') < 0) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '§' && i + 1 < input.length()) {
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static String trailingName(String text) {
        if (text == null) {
            return null;
        }
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && isNameChar(text.charAt(start - 1))) {
            start--;
        }
        String name = text.substring(start, end);
        return name.length() >= 2 && name.length() <= 16 ? name : null;
    }

    public static int nameStart(String text, String name) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end - name.length();
    }

    public static boolean isNameChar(char c) {
        return c == '_'
                || (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z');
    }
}
