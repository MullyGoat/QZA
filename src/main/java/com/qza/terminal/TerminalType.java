package com.qza.terminal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum TerminalType {
    NUMBERS("numbers", "Click in Order", "^Click in order!$", "Click in order!"),
    RUBIX("rubix", "Same Colour", "^Change all to same color!$", "Change all to same color!"),
    MELODY("melody", "Melody", "^Click the button on time!$", "Click the button on time!"),
    SELECT("select", "Select All", "^Select all the ([\\w ]+) items!$",
            "Select all the RED items!"),
    STARTS_WITH("startswith", "Starts With", "^What starts with: '(\\w)'\\?$",
            "What starts with: 'G'?"),
    PANES("panes", "Panes", "^Correct all the panes!$", "Correct all the panes!");

    public final String key;
    public final String label;
    public final String sampleTitle;

    private final Pattern pattern;

    TerminalType(String key, String label, String regex, String sampleTitle) {
        this.key = key;
        this.label = label;
        this.pattern = Pattern.compile(regex);
        this.sampleTitle = sampleTitle;
    }

    public static TerminalType of(String title) {
        if (title == null) {
            return null;
        }
        String text = title.trim();
        for (TerminalType type : values()) {
            if (type.pattern.matcher(text).matches()) {
                return type;
            }
        }
        return null;
    }

    public static TerminalType byKey(String key) {
        for (TerminalType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return null;
    }

    public String labelFor(String templateLabel) {
        if (this != STARTS_WITH) {
            return templateLabel;
        }
        return TerminalTemplate.LABEL_NAME.equals(templateLabel)
                || TerminalTemplate.LABEL_INITIAL.equals(templateLabel)
                ? templateLabel : TerminalTemplate.LABEL_INITIAL;
    }

    public String argument(String title) {
        if (title == null) {
            return "";
        }
        Matcher matcher = pattern.matcher(title.trim());
        if (!matcher.matches() || matcher.groupCount() < 1) {
            return "";
        }
        String found = matcher.group(1);
        return found == null ? "" : found;
    }
}
