package com.qza.cheat.movement;

import com.qza.config.ConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public abstract class CopierScreen extends Screen {
    protected static final int PANEL_BG = 0x55000000;
    protected static final int BOX_BG = 0x66000000;
    protected static final int BOX_BORDER = 0x40FFFFFF;
    protected static final int DIVIDER = 0xFFC8D4DC;
    protected static final int TEXT = 0xFFFFFFFF;
    protected static final int TEXT_DIM = 0xFFAAAAAA;
    protected static final int TEXT_FAINT = 0xFF8A8A8A;
    protected static final int PINK = 0xFFFF55FF;
    protected static final int BAD = 0xFFE05555;
    protected static final int GOOD = 0xFF55FF55;

    protected static final float TITLE_SCALE = 1.5f;
    protected static final int FIELD_H = 16;

    protected CopierScreen() {
        super(Component.literal("QZA"));
    }

    static float scale() {
        double pct = ConfigManager.get().guiScale;
        return (float) Math.max(0.5, Math.min(1.5, pct / 100.0));
    }

    float offsetX() {
        return this.width * (1f - scale()) / 2f;
    }

    float offsetY() {
        return this.height * (1f - scale()) / 2f;
    }

    protected int logicalX(double mouseX) {
        return (int) Math.round((mouseX - offsetX()) / scale());
    }

    protected int logicalY(double mouseY) {
        return (int) Math.round((mouseY - offsetY()) / scale());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float s = scale();
        int mx = logicalX(mouseX);
        int my = logicalY(mouseY);

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        draw(graphics, mx, my);
        super.extractRenderState(graphics, mx, my, delta);

        graphics.pose().popMatrix();
    }

    protected abstract void draw(GuiGraphicsExtractor graphics, int mx, int my);

    protected void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String title) {
        graphics.fill(x, y, x + w, y + h, PANEL_BG);
        outline(graphics, x, y, w, h, 0x33FFFFFF);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + (w / 2f), y + 9);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, title, 0, 0, PINK);
        graphics.pose().popMatrix();

        graphics.fill(x + 8, y + 28, x + w - 8, y + 29, DIVIDER);
    }

    protected static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    protected static boolean inside(double x, double y, int[] r) {
        return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
    }

    protected void box(GuiGraphicsExtractor graphics, int x, int y, int w, boolean ok) {
        graphics.fill(x, y, x + w, y + FIELD_H, BOX_BG);
        outline(graphics, x, y, w, FIELD_H, ok ? BOX_BORDER : BAD);
    }

    protected void button(GuiGraphicsExtractor graphics, int[] r, String label, int mx, int my) {
        boolean hovered = inside(mx, my, r);
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xAA3C5A70 : 0x66223140);
        outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFAFD4EC : 0xFF6A8CA8);
        graphics.centeredText(this.font, label, r[0] + (r[2] / 2), r[1] + ((r[3] - 8) / 2), TEXT);
    }

    protected void dangerButton(GuiGraphicsExtractor graphics, int[] r, String label, int mx, int my) {
        boolean hovered = inside(mx, my, r);
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? 0xAA8B2E2E : 0x66401E1E);
        outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFFF9A9A : 0xFFA85C5C);
        graphics.centeredText(this.font, label, r[0] + (r[2] / 2), r[1] + ((r[3] - 8) / 2),
                hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
    }

    protected String trim(String label, int maxWidth) {
        if (maxWidth <= 0 || this.font.width(label) <= maxWidth) {
            return label;
        }
        String shown = label;
        while (shown.length() > 1 && this.font.width(shown + "...") > maxWidth) {
            shown = shown.substring(0, shown.length() - 1);
        }
        return shown + "...";
    }

    protected static String seconds(double value) {
        return String.format(Locale.ROOT, "%.2fs", value);
    }

    protected MouseButtonEvent toLogical(MouseButtonEvent event) {
        float s = scale();
        float ox = offsetX();
        float oy = offsetY();
        if (s == 1f && ox == 0f && oy == 0f) {
            return event;
        }
        return new MouseButtonEvent((event.x() - ox) / s, (event.y() - oy) / s, event.buttonInfo());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent local = toLogical(event);
        if (clicked(local)) {
            return true;
        }
        return super.mouseClicked(local, doubleClick);
    }

    protected boolean clicked(MouseButtonEvent local) {
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        MouseButtonEvent local = toLogical(event);
        if (dragged(local)) {
            return true;
        }
        float s = scale();
        return super.mouseDragged(local, deltaX / s, deltaY / s);
    }

    protected boolean dragged(MouseButtonEvent local) {
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent local = toLogical(event);
        released(local);
        return super.mouseReleased(local);
    }

    protected void released(MouseButtonEvent local) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
