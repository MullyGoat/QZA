package com.qza.terminal;

import java.util.List;
import java.util.Locale;

public class TerminalTemplate {
    public static final String SHAPE_SQUARE = "square";
    public static final String SHAPE_ROUNDED = "rounded";
    public static final String SHAPE_CIRCLE = "circle";
    public static final String SHAPE_DIAMOND = "diamond";
    public static final String SHAPE_NOTCHED = "notched";
    public static final String SHAPE_HEX = "hex";

    public static final String LABEL_NONE = "none";
    public static final String LABEL_COUNT = "count";
    public static final String LABEL_INITIAL = "initial";
    public static final String LABEL_NAME = "name";

    public static final String MARK_OUTLINE = "outline";
    public static final String MARK_GLOW = "glow";
    public static final String MARK_FILL = "fill";
    public static final String MARK_CORNER = "corner";
    public static final String MARK_DIM = "dim";

    public static final int MIN_CELL = 10;
    public static final int MAX_CELL = 48;
    public static final int MAX_GAP = 16;
    public static final int MAX_PAD = 32;
    public static final int MAX_BORDER = 4;

    public String name = "Custom";

    public String shape = SHAPE_ROUNDED;
    public String label = LABEL_COUNT;
    public String mark = MARK_OUTLINE;

    public int cell = 26;
    public int gap = 4;
    public int radius = 6;
    public int pad = 10;

    public int panel = 0xE0111318;
    public int panelEdge = 0xFF2C323C;
    public int edgeWidth = 1;

    public int slotEmpty = 0x1AFFFFFF;
    public int slotPlain = 0xFF3A424E;
    public boolean itemColour = true;
    public int tint = 0;

    public int textColour = 0xFFFFFFFF;
    public boolean textShadow = true;

    public int markColour = 0xFFFFFFFF;
    public int hoverColour = 0x38FFFFFF;

    public String background = "";

    public boolean showTitle = true;
    public int titleColour = 0xFFFF55FF;

    public boolean alwaysOrderNumbers = true;

    public boolean roleColours = false;

    public int rubixPlus1 = 0xFF5B8CFF;
    public int rubixPlus2 = 0xFF1E3F8C;
    public int rubixMinus1 = 0xFFE09020;
    public int rubixMinus2 = 0xFF8A5A10;

    public int order1 = 0xFFFF3FA0;
    public int order2 = 0xFF2A2ACC;
    public int order3 = 0xFF4A4AE8;

    public int selectColour = 0xFF29C4F0;
    public int startsWithColour = 0xFF22E8E8;

    public int melodyMarker = 0xFFC000C0;
    public int melodyReady = 0xFF3CFF3C;

    public static List<String> shapes() {
        return List.of(SHAPE_SQUARE, SHAPE_ROUNDED, SHAPE_CIRCLE, SHAPE_DIAMOND,
                SHAPE_NOTCHED, SHAPE_HEX);
    }

    public static List<String> labels() {
        return List.of(LABEL_NONE, LABEL_COUNT, LABEL_INITIAL, LABEL_NAME);
    }

    public static List<String> marks() {
        return List.of(MARK_OUTLINE, MARK_GLOW, MARK_FILL, MARK_CORNER, MARK_DIM);
    }

    public static String pretty(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = value.replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase(Locale.ROOT);
    }

    public void copyFrom(TerminalTemplate from) {
        if (from == null || from == this) {
            return;
        }
        String kept = name;
        TerminalTemplate source = from.copy();

        shape = source.shape;
        label = source.label;
        mark = source.mark;
        cell = source.cell;
        gap = source.gap;
        radius = source.radius;
        pad = source.pad;
        panel = source.panel;
        panelEdge = source.panelEdge;
        edgeWidth = source.edgeWidth;
        slotEmpty = source.slotEmpty;
        slotPlain = source.slotPlain;
        itemColour = source.itemColour;
        tint = source.tint;
        textColour = source.textColour;
        textShadow = source.textShadow;
        markColour = source.markColour;
        hoverColour = source.hoverColour;
        background = source.background;
        showTitle = source.showTitle;
        titleColour = source.titleColour;
        alwaysOrderNumbers = source.alwaysOrderNumbers;
        roleColours = source.roleColours;
        rubixPlus1 = source.rubixPlus1;
        rubixPlus2 = source.rubixPlus2;
        rubixMinus1 = source.rubixMinus1;
        rubixMinus2 = source.rubixMinus2;
        order1 = source.order1;
        order2 = source.order2;
        order3 = source.order3;
        selectColour = source.selectColour;
        startsWithColour = source.startsWithColour;
        melodyMarker = source.melodyMarker;
        melodyReady = source.melodyReady;

        name = kept;
        tidy();
    }

    public TerminalTemplate copy() {
        TerminalTemplate out = new TerminalTemplate();
        out.name = name;
        out.shape = shape;
        out.label = label;
        out.mark = mark;
        out.cell = cell;
        out.gap = gap;
        out.radius = radius;
        out.pad = pad;
        out.panel = panel;
        out.panelEdge = panelEdge;
        out.edgeWidth = edgeWidth;
        out.slotEmpty = slotEmpty;
        out.slotPlain = slotPlain;
        out.itemColour = itemColour;
        out.tint = tint;
        out.textColour = textColour;
        out.textShadow = textShadow;
        out.markColour = markColour;
        out.hoverColour = hoverColour;
        out.background = background;
        out.showTitle = showTitle;
        out.titleColour = titleColour;
        out.alwaysOrderNumbers = alwaysOrderNumbers;
        out.roleColours = roleColours;
        out.rubixPlus1 = rubixPlus1;
        out.rubixPlus2 = rubixPlus2;
        out.rubixMinus1 = rubixMinus1;
        out.rubixMinus2 = rubixMinus2;
        out.order1 = order1;
        out.order2 = order2;
        out.order3 = order3;
        out.selectColour = selectColour;
        out.startsWithColour = startsWithColour;
        out.melodyMarker = melodyMarker;
        out.melodyReady = melodyReady;
        return out;
    }

    public TerminalTemplate tidy() {
        shape = pick(shape, shapes(), SHAPE_ROUNDED);
        label = pick(label, labels(), LABEL_COUNT);
        mark = pick(mark, marks(), MARK_OUTLINE);
        cell = clamp(cell, MIN_CELL, MAX_CELL);
        gap = clamp(gap, 0, MAX_GAP);
        radius = clamp(radius, 0, cell / 2);
        pad = clamp(pad, 0, MAX_PAD);
        edgeWidth = clamp(edgeWidth, 0, MAX_BORDER);
        if (name == null || name.isBlank()) {
            name = "Custom";
        }
        if (background == null) {
            background = "";
        }
        return this;
    }

    private static String pick(String value, List<String> allowed, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        return allowed.contains(text) ? text : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
