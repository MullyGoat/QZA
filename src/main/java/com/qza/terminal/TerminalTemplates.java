package com.qza.terminal;

import com.qza.config.ConfigManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class TerminalTemplates {
    public static final String CUSTOM = "custom";

    private static final Map<String, Supplier<TerminalTemplate>> BUILT_IN = new LinkedHashMap<>();

    static {
        BUILT_IN.put("vanilla", TerminalTemplates::vanilla);
        BUILT_IN.put("odin", TerminalTemplates::odin);
        BUILT_IN.put("carbon", TerminalTemplates::carbon);
        BUILT_IN.put("neon", TerminalTemplates::neon);
        BUILT_IN.put("minimal", TerminalTemplates::minimal);
        BUILT_IN.put("frost", TerminalTemplates::frost);
        BUILT_IN.put("midnight", TerminalTemplates::midnight);
        BUILT_IN.put("ember", TerminalTemplates::ember);
        BUILT_IN.put("bubble", TerminalTemplates::bubble);
        BUILT_IN.put("prism", TerminalTemplates::prism);
        BUILT_IN.put("honeycomb", TerminalTemplates::honeycomb);
        BUILT_IN.put("ghost", TerminalTemplates::ghost);
        BUILT_IN.put("arcade", TerminalTemplates::arcade);
        BUILT_IN.put("qza", TerminalTemplates::qza);
    }

    private TerminalTemplates() {
    }

    public static List<String> keys() {
        List<String> out = new ArrayList<>();
        out.add(CUSTOM);
        out.addAll(BUILT_IN.keySet());
        return out;
    }

    public static String label(String key) {
        if (CUSTOM.equals(key)) {
            String name = ConfigManager.get().terminalCustom.name;
            return name == null || name.isBlank() ? "Custom" : name;
        }
        return TerminalTemplate.pretty(key);
    }

    public static TerminalTemplate get(String key) {
        if (CUSTOM.equals(key)) {
            return ConfigManager.get().terminalCustom.tidy();
        }
        Supplier<TerminalTemplate> found = BUILT_IN.get(key);
        return found == null ? odin().tidy() : found.get().tidy();
    }

    public static TerminalTemplate forType(TerminalType type) {
        return get(ConfigManager.get().terminalTemplates
                .getOrDefault(type.key, "odin"));
    }

    private static TerminalTemplate vanilla() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "Vanilla";
        t.shape = TerminalTemplate.SHAPE_SQUARE;
        t.cell = 18;
        t.gap = 2;
        t.pad = 8;
        t.panel = 0xFFC6C6C6;
        t.panelEdge = 0xFF555555;
        t.edgeWidth = 2;
        t.slotEmpty = 0xFF8B8B8B;
        t.slotPlain = 0xFF8B8B8B;
        t.textColour = 0xFFFFFFFF;
        t.markColour = 0xFFFFFFAA;
        t.hoverColour = 0x50FFFFFF;
        t.titleColour = 0xFF404040;
        return t;
    }

    private static TerminalTemplate odin() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "Odin";
        t.shape = TerminalTemplate.SHAPE_ROUNDED;
        t.cell = 26;
        t.gap = 4;
        t.radius = 5;
        t.pad = 10;
        t.panel = 0xE0101216;
        t.panelEdge = 0x00000000;
        t.edgeWidth = 0;
        t.slotEmpty = 0x14FFFFFF;
        t.slotPlain = 0xFF39414D;
        t.markColour = 0xFFFFFFFF;
        t.titleColour = 0xFFDDDDDD;
        return t;
    }

    private static TerminalTemplate carbon() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "Carbon";
        t.shape = TerminalTemplate.SHAPE_NOTCHED;
        t.cell = 24;
        t.gap = 3;
        t.pad = 12;
        t.panel = 0xF01A1C20;
        t.panelEdge = 0xFF3A3F49;
        t.edgeWidth = 1;
        t.slotEmpty = 0x12FFFFFF;
        t.slotPlain = 0xFF444B57;
        t.tint = 0x22000000;
        t.mark = TerminalTemplate.MARK_CORNER;
        t.markColour = 0xFFE8E8E8;
        t.titleColour = 0xFFB8BEC8;
        return t;
    }

    private static TerminalTemplate neon() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "Neon";
        t.shape = TerminalTemplate.SHAPE_ROUNDED;
        t.cell = 28;
        t.gap = 6;
        t.radius = 8;
        t.pad = 14;
        t.panel = 0xF0050508;
        t.panelEdge = 0xFF00E5FF;
        t.edgeWidth = 2;
        t.slotEmpty = 0x1000E5FF;
        t.slotPlain = 0xFF101822;
        t.mark = TerminalTemplate.MARK_GLOW;
        t.markColour = 0xFF00E5FF;
        t.hoverColour = 0x4400E5FF;
        t.textColour = 0xFFEFFFFF;
        t.titleColour = 0xFF00E5FF;
        return t;
    }

    private static TerminalTemplate minimal() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "Minimal";
        t.shape = TerminalTemplate.SHAPE_SQUARE;
        t.label = TerminalTemplate.LABEL_NONE;
        t.cell = 20;
        t.gap = 2;
        t.pad = 4;
        t.panel = 0x00000000;
        t.panelEdge = 0x00000000;
        t.edgeWidth = 0;
        t.slotEmpty = 0x0DFFFFFF;
        t.slotPlain = 0xFF4A515C;
        t.markColour = 0xFFFFFFFF;
        t.showTitle = false;
        return t;
    }

    private static TerminalTemplate frost() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "Frost";
        t.shape = TerminalTemplate.SHAPE_ROUNDED;
        t.cell = 26;
        t.gap = 5;
        t.radius = 9;
        t.pad = 12;
        t.panel = 0xDA0E1C28;
        t.panelEdge = 0xFF8FD4F0;
        t.edgeWidth = 1;
        t.slotEmpty = 0x188FD4F0;
        t.slotPlain = 0xFF23485E;
        t.tint = 0x2288D8FF;
        t.mark = TerminalTemplate.MARK_OUTLINE;
        t.markColour = 0xFFE8FBFF;
        t.hoverColour = 0x408FD4F0;
        t.textColour = 0xFFEAF8FF;
        t.titleColour = 0xFF8FD4F0;
        return t;
    }

    private static TerminalTemplate midnight() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "Midnight";
        t.shape = TerminalTemplate.SHAPE_ROUNDED;
        t.cell = 26;
        t.gap = 4;
        t.radius = 4;
        t.pad = 12;
        t.panel = 0xF00B0E1E;
        t.panelEdge = 0xFF3B3F7A;
        t.edgeWidth = 1;
        t.slotEmpty = 0x186A6FD0;
        t.slotPlain = 0xFF232855;
        t.tint = 0x2A1B1F60;
        t.mark = TerminalTemplate.MARK_GLOW;
        t.markColour = 0xFFA6ADFF;
        t.hoverColour = 0x38A6ADFF;
        t.textColour = 0xFFD9DCFF;
        t.titleColour = 0xFFA6ADFF;
        return t;
    }

    private static TerminalTemplate ember() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "Ember";
        t.shape = TerminalTemplate.SHAPE_NOTCHED;
        t.cell = 26;
        t.gap = 4;
        t.pad = 12;
        t.panel = 0xF01A0C08;
        t.panelEdge = 0xFFCC5522;
        t.edgeWidth = 2;
        t.slotEmpty = 0x18FF7A3C;
        t.slotPlain = 0xFF52281A;
        t.tint = 0x26FF6A22;
        t.mark = TerminalTemplate.MARK_FILL;
        t.markColour = 0x66FFB066;
        t.hoverColour = 0x44FF8844;
        t.textColour = 0xFFFFE6D2;
        t.titleColour = 0xFFFF9955;
        return t;
    }

    private static TerminalTemplate bubble() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "Bubble";
        t.shape = TerminalTemplate.SHAPE_CIRCLE;
        t.cell = 28;
        t.gap = 6;
        t.pad = 14;
        t.panel = 0xE0141821;
        t.panelEdge = 0x00000000;
        t.edgeWidth = 0;
        t.slotEmpty = 0x10FFFFFF;
        t.slotPlain = 0xFF48505E;
        t.mark = TerminalTemplate.MARK_OUTLINE;
        t.markColour = 0xFFFFFFFF;
        t.titleColour = 0xFFE0E4EC;
        return t;
    }

    private static TerminalTemplate prism() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "Prism";
        t.shape = TerminalTemplate.SHAPE_DIAMOND;
        t.label = TerminalTemplate.LABEL_COUNT;
        t.cell = 30;
        t.gap = 2;
        t.pad = 12;
        t.panel = 0xE60D0F14;
        t.panelEdge = 0xFF7A5CC4;
        t.edgeWidth = 1;
        t.slotEmpty = 0x127A5CC4;
        t.slotPlain = 0xFF3C3160;
        t.mark = TerminalTemplate.MARK_GLOW;
        t.markColour = 0xFFD8C8FF;
        t.hoverColour = 0x407A5CC4;
        t.titleColour = 0xFFB69CFF;
        return t;
    }

    private static TerminalTemplate honeycomb() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "Honeycomb";
        t.shape = TerminalTemplate.SHAPE_HEX;
        t.cell = 28;
        t.gap = 3;
        t.pad = 12;
        t.panel = 0xEC15120A;
        t.panelEdge = 0xFFD9A322;
        t.edgeWidth = 1;
        t.slotEmpty = 0x16D9A322;
        t.slotPlain = 0xFF564420;
        t.mark = TerminalTemplate.MARK_OUTLINE;
        t.markColour = 0xFFFFE9A8;
        t.hoverColour = 0x44D9A322;
        t.textColour = 0xFFFFF4D6;
        t.titleColour = 0xFFD9A322;
        return t;
    }

    private static TerminalTemplate ghost() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "Ghost";
        t.shape = TerminalTemplate.SHAPE_ROUNDED;
        t.cell = 26;
        t.gap = 4;
        t.radius = 6;
        t.pad = 10;
        t.panel = 0x40000000;
        t.panelEdge = 0x30FFFFFF;
        t.edgeWidth = 1;
        t.slotEmpty = 0x0AFFFFFF;
        t.slotPlain = 0x60555C68;
        t.tint = 0x50000000;
        t.mark = TerminalTemplate.MARK_OUTLINE;
        t.markColour = 0xC0FFFFFF;
        t.hoverColour = 0x28FFFFFF;
        t.textColour = 0xD0FFFFFF;
        t.titleColour = 0xA0FFFFFF;
        return t;
    }

    private static TerminalTemplate arcade() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "Arcade";
        t.shape = TerminalTemplate.SHAPE_SQUARE;
        t.cell = 32;
        t.gap = 5;
        t.pad = 14;
        t.panel = 0xFF000000;
        t.panelEdge = 0xFFFFE000;
        t.edgeWidth = 3;
        t.slotEmpty = 0x1AFFE000;
        t.slotPlain = 0xFF202020;
        t.mark = TerminalTemplate.MARK_FILL;
        t.markColour = 0x70FFE000;
        t.hoverColour = 0x50FFE000;
        t.textColour = 0xFFFFE000;
        t.titleColour = 0xFFFFE000;
        return t;
    }

    private static TerminalTemplate qza() {
        TerminalTemplate t = new TerminalTemplate();
        t.name = "QZA";
        t.shape = TerminalTemplate.SHAPE_ROUNDED;
        t.cell = 26;
        t.gap = 4;
        t.radius = 6;
        t.pad = 12;
        t.panel = 0xE6140E18;
        t.panelEdge = 0xFFFF55FF;
        t.edgeWidth = 1;
        t.slotEmpty = 0x18FF55FF;
        t.slotPlain = 0xFF3A2145;
        t.tint = 0x24FF55FF;
        t.mark = TerminalTemplate.MARK_GLOW;
        t.markColour = 0xFFFF9BFF;
        t.hoverColour = 0x44FF55FF;
        t.textColour = 0xFFFFE8FF;
        t.titleColour = 0xFFFF55FF;
        return t;
    }
}
