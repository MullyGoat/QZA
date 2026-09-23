package com.qza.notify;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class NotificationBox {
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 3.0;

    public static final int PARTY_TEXT = 0xFF8ED6FF;
    public static final int CHAT_TEXT = 0xFF7BE87B;

    private static final long FADE_IN_MS = 120L;
    private static final long FADE_OUT_MS = 320L;

    private static final int BG_TOP = 0xEE1B1D22;
    private static final int BG_BOTTOM = 0xEE101114;
    private static final int BORDER = 0xFFFF55FF;
    private static final int BORDER_SOFT = 0x66FF55FF;
    private static final int TRACK = 0x33FFFFFF;
    private static final int BAR = 0xFFFF6BFF;

    private static final int PAD_X = 11;
    private static final int PAD_TOP = 8;
    private static final int GAP = 7;
    private static final int BAR_H = 2;
    private static final int PAD_BOTTOM = 7;
    private static final int MIN_W = 130;
    private static final int MAX_W = 320;

    private NotificationBox() {
    }

    public static String fit(Font font, String text) {
        int room = MAX_W - (PAD_X * 2);
        if (text == null) {
            return "";
        }
        if (font.width(text) <= room) {
            return text;
        }
        String shown = text;
        while (shown.length() > 1 && font.width(shown + "...") > room) {
            shown = shown.substring(0, shown.length() - 1);
        }
        return shown + "...";
    }

    public static int width(Font font, String text) {
        return Math.max(MIN_W, Math.min(MAX_W, font.width(text) + (PAD_X * 2)));
    }

    public static int height(Font font) {
        return PAD_TOP + font.lineHeight + GAP + BAR_H + PAD_BOTTOM;
    }

    public static float clampScale(double scale) {
        return (float) Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    public static int[] topLeft(double fx, double fy, int screenW, int screenH, int boxW, int boxH) {
        int x = (int) Math.round((fx * screenW) - (boxW / 2.0));
        int y = (int) Math.round((fy * screenH) - (boxH / 2.0));
        return new int[]{clamp(x, screenW - boxW), clamp(y, screenH - boxH)};
    }

    public static double fraction(double pixels, int size) {
        if (size <= 0) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, pixels / size));
    }

    public static float alphaFor(long elapsed, long duration) {
        long fadeIn = Math.min(FADE_IN_MS, duration / 4);
        long fadeOut = Math.min(FADE_OUT_MS, duration / 4);

        if (fadeIn > 0 && elapsed < fadeIn) {
            return elapsed / (float) fadeIn;
        }
        long left = duration - elapsed;
        if (fadeOut > 0 && left < fadeOut) {
            return Math.max(0f, left / (float) fadeOut);
        }
        return 1f;
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, String text,
                            int x, int y, float scale, double progress, float alpha,
                            int textColour) {
        int w = width(font, text);
        int h = height(font);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);

        graphics.fillGradient(0, 0, w, h, fade(BG_TOP, alpha), fade(BG_BOTTOM, alpha));
        border(graphics, w, h, alpha);

        graphics.centeredText(font, text, w / 2, PAD_TOP, fade(textColour, alpha));

        int barY = PAD_TOP + font.lineHeight + GAP;
        int barW = w - (PAD_X * 2);
        graphics.fill(PAD_X, barY, PAD_X + barW, barY + BAR_H, fade(TRACK, alpha));

        int filled = (int) Math.round(barW * Math.max(0.0, Math.min(1.0, progress)));
        if (filled > 0) {
            graphics.fill(PAD_X, barY, PAD_X + filled, barY + BAR_H, fade(BAR, alpha));
        }

        graphics.pose().popMatrix();
    }

    public static void drawPlain(GuiGraphicsExtractor graphics, Font font, String text,
                                 int x, int y, float scale, int colour) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, colour);
        graphics.pose().popMatrix();
    }

    private static void border(GuiGraphicsExtractor graphics, int w, int h, float alpha) {
        int hard = fade(BORDER, alpha);
        graphics.fill(0, 0, w, 1, hard);
        graphics.fill(0, h - 1, w, h, hard);
        graphics.fill(0, 1, 1, h - 1, hard);
        graphics.fill(w - 1, 1, w, h - 1, hard);

        int soft = fade(BORDER_SOFT, alpha);
        graphics.fill(-1, -1, w + 1, 0, soft);
        graphics.fill(-1, h, w + 1, h + 1, soft);
        graphics.fill(-1, 0, 0, h, soft);
        graphics.fill(w, 0, w + 1, h, soft);
    }

    private static int fade(int argb, float alpha) {
        float clamped = Math.max(0f, Math.min(1f, alpha));
        int a = Math.round(((argb >>> 24) & 0xFF) * clamped);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int clamp(int value, int max) {
        if (max <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(value, max));
    }
}
