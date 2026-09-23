package com.qza.terminal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class TerminalPainter {
    private TerminalPainter() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, TerminalGrid grid,
                            TerminalTemplate template, TerminalLayout layout,
                            String title, int hovered) {
        draw(graphics, font, grid, template, layout, title, hovered, template.label);
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, TerminalGrid grid,
                            TerminalTemplate template, TerminalLayout layout,
                            String title, int hovered, String labelMode) {
        panel(graphics, template, layout);

        if (template.showTitle && title != null && !title.isBlank()) {
            graphics.centeredText(font, fit(font, title, layout.width - 8),
                    layout.x + (layout.width / 2), layout.y + layout.pad, template.titleColour);
        }

        boolean anyMarked = false;
        for (TerminalCell cell : grid.cells) {
            if (cell.marked()) {
                anyMarked = true;
                break;
            }
        }

        for (int position = 0; position < grid.cells.size(); position++) {
            int row = position / grid.columns;
            int column = position % grid.columns;
            if (row >= layout.rows) {
                continue;
            }
            cell(graphics, font, template, layout, grid.cells.get(position),
                    layout.cellX(column), layout.cellY(row),
                    position == hovered, anyMarked, grid.counts, labelMode);
        }
    }

    private static void panel(GuiGraphicsExtractor graphics, TerminalTemplate template,
                              TerminalLayout layout) {
        if (alpha(template.panel) > 0) {
            graphics.fill(layout.x, layout.y, layout.x + layout.width,
                    layout.y + layout.height, template.panel);
        }
        int width = template.edgeWidth;
        if (width <= 0 || alpha(template.panelEdge) == 0) {
            return;
        }
        int x = layout.x;
        int y = layout.y;
        int w = layout.width;
        int h = layout.height;
        graphics.fill(x, y, x + w, y + width, template.panelEdge);
        graphics.fill(x, y + h - width, x + w, y + h, template.panelEdge);
        graphics.fill(x, y + width, x + width, y + h - width, template.panelEdge);
        graphics.fill(x + w - width, y + width, x + w, y + h - width, template.panelEdge);
    }

    private static void cell(GuiGraphicsExtractor graphics, Font font, TerminalTemplate template,
                             TerminalLayout layout, TerminalCell cell, int x, int y,
                             boolean hovered, boolean anyMarked, boolean counts,
                             String labelMode) {
        int size = layout.cell;
        int base = baseColour(template, cell);

        if (cell.marked() && TerminalTemplate.MARK_GLOW.equals(template.mark)) {
            shape(graphics, x - 2, y - 2, size + 4, template.shape,
                    template.radius + 1, fade(template.markColour, 0.4f));
        }

        shape(graphics, x, y, size, template.shape, template.radius, base);

        if (cell.marked()) {
            mark(graphics, template, x, y, size, base);
        } else if (cell.filled() && anyMarked
                && TerminalTemplate.MARK_DIM.equals(template.mark)) {
            shape(graphics, x, y, size, template.shape, template.radius, 0x90000000);
        }

        if (hovered && alpha(template.hoverColour) > 0) {
            shape(graphics, x, y, size, template.shape, template.radius, template.hoverColour);
        }

        String label = cell.label(labelMode, counts);
        if (label.isEmpty()) {
            return;
        }
        String shown = fit(font, label, size - 2);
        if (shown.isEmpty()) {
            return;
        }
        int textY = y + ((size - font.lineHeight) / 2) + 1;
        graphics.centeredText(font, shown, x + (size / 2), textY, template.textColour);
    }

    private static void mark(GuiGraphicsExtractor graphics, TerminalTemplate template,
                             int x, int y, int size, int base) {
        switch (template.mark) {
            case TerminalTemplate.MARK_FILL -> shape(graphics, x, y, size, template.shape,
                    template.radius, template.markColour);
            case TerminalTemplate.MARK_CORNER -> {
                int n = Math.max(3, size / 5);
                graphics.fill(x + size - n - 1, y + 1, x + size - 1, y + 1 + n,
                        template.markColour);
            }
            case TerminalTemplate.MARK_OUTLINE -> {
                int w = Math.max(1, size / 12);
                shape(graphics, x, y, size, template.shape, template.radius,
                        template.markColour);
                shape(graphics, x + w, y + w, size - (w * 2), template.shape,
                        Math.max(0, template.radius - w), base);
            }
            default -> {
            }
        }
    }

    private static int baseColour(TerminalTemplate template, TerminalCell cell) {
        if (!cell.filled()) {
            return template.slotEmpty;
        }
        if (!template.itemColour || cell.colour() == 0) {
            return template.slotPlain;
        }
        return over(cell.colour(), template.tint);
    }

    public static void shape(GuiGraphicsExtractor graphics, int x, int y, int size,
                             String shape, int radius, int colour) {
        if (size <= 0 || alpha(colour) == 0) {
            return;
        }
        for (int row = 0; row < size; row++) {
            int inset = inset(row, size, shape, radius);
            if ((inset * 2) >= size) {
                continue;
            }
            graphics.fill(x + inset, y + row, x + size - inset, y + row + 1, colour);
        }
    }

    private static int inset(int row, int size, String shape, int radius) {
        return switch (shape == null ? TerminalTemplate.SHAPE_SQUARE : shape) {
            case TerminalTemplate.SHAPE_NOTCHED -> {
                int n = Math.max(1, size / 8);
                yield (row < n || row >= size - n) ? n : 0;
            }
            case TerminalTemplate.SHAPE_CIRCLE -> arc(row, size, size / 2.0);
            case TerminalTemplate.SHAPE_ROUNDED -> rounded(row, size, radius);
            case TerminalTemplate.SHAPE_DIAMOND -> taper(row, size, 0.0);
            case TerminalTemplate.SHAPE_HEX -> taper(row, size, 0.45);
            default -> 0;
        };
    }

    private static int arc(int row, int size, double r) {
        double dy = (row + 0.5) - r;
        double half = Math.sqrt(Math.max(0.0, (r * r) - (dy * dy)));
        return (int) Math.round(r - half);
    }

    private static int rounded(int row, int size, int radius) {
        if (radius <= 0) {
            return 0;
        }
        int limit = Math.min(radius, size / 2);
        int from = row < limit ? row : (row >= size - limit ? size - 1 - row : -1);
        if (from < 0) {
            return 0;
        }
        double dy = limit - (from + 0.5);
        double dx = Math.sqrt(Math.max(0.0, (limit * limit) - (dy * dy)));
        return (int) Math.round(limit - dx);
    }

    private static int taper(int row, int size, double flat) {
        double mid = (size - 1) / 2.0;
        if (mid <= 0) {
            return 0;
        }
        double t = Math.abs(row - mid) / mid;
        double k = flat >= 1.0 ? 0.0 : Math.max(0.0, (t - flat) / (1.0 - flat));
        return (int) Math.round((size / 2.0) * k);
    }

    public static String fit(Font font, String text, int room) {
        if (text == null || room <= 0) {
            return "";
        }
        if (font.width(text) <= room) {
            return text;
        }
        String shown = text;
        while (!shown.isEmpty() && font.width(shown) > room) {
            shown = shown.substring(0, shown.length() - 1);
        }
        return shown;
    }

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    public static int fade(int argb, float factor) {
        int a = Math.round(alpha(argb) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static int over(int base, int layer) {
        int la = alpha(layer);
        if (la == 0) {
            return base;
        }
        float f = la / 255f;
        int r = blend((base >> 16) & 0xFF, (layer >> 16) & 0xFF, f);
        int g = blend((base >> 8) & 0xFF, (layer >> 8) & 0xFF, f);
        int b = blend(base & 0xFF, layer & 0xFF, f);
        return (alpha(base) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blend(int base, int layer, float factor) {
        return Math.max(0, Math.min(255, Math.round((base * (1 - factor)) + (layer * factor))));
    }
}
