package com.qza.terminal;

public record TerminalCell(int index, boolean filled, int colour, String name,
                           int count, boolean marked) {

    public static TerminalCell empty(int index) {
        return new TerminalCell(index, false, 0, "", 0, false);
    }

    public String label(String mode) {
        if (!filled) {
            return "";
        }
        return switch (mode) {
            case TerminalTemplate.LABEL_COUNT -> count > 1 ? String.valueOf(count) : "";
            case TerminalTemplate.LABEL_INITIAL -> name.isEmpty()
                    ? "" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            case TerminalTemplate.LABEL_NAME -> name;
            default -> "";
        };
    }
}
