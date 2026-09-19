package com.qza.shitter;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ShitterHighlight {
    private static final TextColor BROWN = TextColor.fromRgb(0xA9714B);

    private static final String MARK = "(X)";
    private static final Style MARK_STYLE = Style.EMPTY
            .withColor(TextColor.fromRgb(0xFF5555))
            .withBold(true);

    private ShitterHighlight() {
    }

    public static List<Component> highlight(List<Component> lines) {
        if (lines == null || lines.isEmpty()
                || ShitterList.size() == 0 || !inPartyFinder()) {
            return lines;
        }

        List<String> names = listedNames();
        if (names.isEmpty()) {
            return lines;
        }

        List<Component> out = new ArrayList<>(lines);
        boolean found = false;

        for (int i = 0; i < out.size(); i++) {
            Component line = out.get(i);
            if (line == null) {
                continue;
            }
            Component painted = paint(line, names);
            if (painted != null) {
                out.set(i, painted);
                found = true;
            }
        }

        if (!found) {
            return lines;
        }

        if (out.get(0) != null) {
            out.set(0, Component.empty().append(out.get(0))
                    .append(Component.literal(" " + MARK).withStyle(MARK_STYLE)));
        }
        return out;
    }

    private static List<String> listedNames() {
        List<String> names = new ArrayList<>();
        for (ShitterEntry entry : ShitterList.all()) {
            if (entry.name != null && !entry.name.isBlank()) {
                names.add(entry.name);
            }
        }
        return names;
    }

    private static boolean inPartyFinder() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return false;
        }
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        return title.toLowerCase(Locale.ROOT).contains("party finder");
    }

    static Component paint(Component line, List<String> names) {
        List<Part> parts = new ArrayList<>();
        line.visit((style, text) -> {
            expand(text, style, parts);
            return Optional.empty();
        }, Style.EMPTY);

        StringBuilder joined = new StringBuilder();
        for (Part part : parts) {
            joined.append(part.text());
        }
        String full = joined.toString();
        if (full.isEmpty()) {
            return null;
        }

        boolean[] named = new boolean[full.length()];
        boolean hit = false;

        int at = 0;
        while (at < full.length()) {
            int[] found = findName(full, at, names);
            if (found == null) {
                break;
            }
            hit = true;
            for (int i = found[0]; i < found[1]; i++) {
                named[i] = true;
            }
            at = found[1];
        }
        if (!hit) {
            return null;
        }

        MutableComponent out = Component.empty();
        int offset = 0;

        for (Part part : parts) {
            String text = part.text();
            int i = 0;

            while (i < text.length()) {
                boolean brown = named[offset + i];
                int end = i + 1;
                while (end < text.length() && named[offset + end] == brown) {
                    end++;
                }

                out.append(Component.literal(text.substring(i, end))
                        .withStyle(brown ? part.style().withColor(BROWN) : part.style()));
                i = end;
            }

            offset += text.length();
        }

        return out;
    }

    private static void expand(String text, Style base, List<Part> into) {
        Style current = base;
        StringBuilder run = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ChatFormatting.PREFIX_CODE || i + 1 >= text.length()) {
                run.append(c);
                continue;
            }

            ChatFormatting code = ChatFormatting.getByCode(text.charAt(i + 1));
            if (code == null) {
                run.append(c);
                continue;
            }

            if (!run.isEmpty()) {
                into.add(new Part(run.toString(), current));
                run.setLength(0);
            }

            if (code == ChatFormatting.RESET) {
                current = base;
            } else if (code.isColor()) {
                current = base.withColor(TextColor.fromLegacyFormat(code));
            } else {
                current = current.applyFormat(code);
            }
            i++;
        }

        if (!run.isEmpty()) {
            into.add(new Part(run.toString(), current));
        }
    }

    static int[] findName(String text, int from, List<String> names) {
        int best = -1;
        int bestEnd = -1;

        for (String name : names) {
            int at = indexOfWord(text, name, from);
            if (at >= 0 && (best < 0 || at < best)) {
                best = at;
                bestEnd = at + name.length();
            }
        }

        return best < 0 ? null : new int[]{best, bestEnd};
    }

    static int indexOfWord(String text, String name, int from) {
        String haystack = text.toLowerCase(Locale.ROOT);
        String needle = name.toLowerCase(Locale.ROOT);

        int at = haystack.indexOf(needle, Math.max(0, from));
        while (at >= 0) {
            boolean startOk = at == 0 || !isNameChar(haystack.charAt(at - 1));
            int end = at + needle.length();
            boolean endOk = end >= haystack.length() || !isNameChar(haystack.charAt(end));
            if (startOk && endOk) {
                return at;
            }
            at = haystack.indexOf(needle, at + 1);
        }
        return -1;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private record Part(String text, Style style) {
    }
}
