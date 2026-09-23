package com.qza.terminal;

import java.util.Locale;

public record TerminalCell(int index, boolean filled, int colour, String name,
                           int count, boolean marked, boolean button) {

    public static TerminalCell empty(int index) {
        return new TerminalCell(index, false, 0, "", 0, false, false);
    }

    public TerminalCell hidden() {
        return empty(index);
    }

    public TerminalCell recoloured(int argb) {
        return new TerminalCell(index, filled, argb, name, count, marked, button);
    }

    public String label(String mode, boolean counts) {
        if (!filled) {
            return "";
        }
        return switch (mode) {
            case TerminalTemplate.LABEL_COUNT -> counts && count > 0
                    ? String.valueOf(count) : "";
            case TerminalTemplate.LABEL_INITIAL -> name.isEmpty()
                    ? "" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            case TerminalTemplate.LABEL_NAME -> name;
            default -> "";
        };
    }
}
