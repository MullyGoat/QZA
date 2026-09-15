package com.qza.party;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class PartyNotification {
    public static final double MIN_DURATION = 1.0;
    public static final double MAX_DURATION = 10.0;
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 3.0;

    private static final String MARKER = " has invited you to join ";
    private static final long FADE_IN_MS = 120L;
    private static final long FADE_OUT_MS = 320L;

    private static final int BG_TOP = 0xEE1B1D22;
    private static final int BG_BOTTOM = 0xEE101114;
    private static final int BORDER = 0xFFFF55FF;
    private static final int BORDER_SOFT = 0x66FF55FF;
    private static final int LABEL = 0xFF8ED6FF;
    private static final int TRACK = 0x33FFFFFF;
    private static final int BAR = 0xFFFF6BFF;

    private static final int PAD_X = 11;
    private static final int PAD_TOP = 8;
    private static final int GAP = 7;
    private static final int BAR_H = 2;
    private static final int PAD_BOTTOM = 7;
    private static final int MIN_W = 130;

    private static String inviter;
    private static long shownAt;

    private PartyNotification() {
    }

    public static void onChatMessage(String raw) {
        if (!ConfigManager.get().partyInviteNotifyEnabled) {
            return;
        }

        String message = stripCodes(raw);
        int marker = message.indexOf(MARKER);
        if (marker < 0) {
            return;
        }
        if (!message.substring(marker + MARKER.length()).contains("party")) {
            return;
        }

        String ign = inviterName(message.substring(0, marker));
        if (ign != null) {
            show(ign);
        }
    }

    public static void show(String ign) {
        inviter = ign;
        shownAt = System.currentTimeMillis();
    }

    public static void clear() {
        inviter = null;
    }

    public static String textFor(String ign) {
        return ign + " invited you to join their party!";
    }

    public static float scale() {
        double value = ConfigManager.get().partyNotifyScale;
        return (float) Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    public static long durationMs() {
        double seconds = ConfigManager.get().partyNotifyDuration;
        return Math.round(Math.max(MIN_DURATION, Math.min(MAX_DURATION, seconds)) * 1000.0);
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.partyNotifyX = defaults.partyNotifyX;
        cfg.partyNotifyY = defaults.partyNotifyY;
        cfg.partyNotifyScale = defaults.partyNotifyScale;
        ConfigManager.save();
    }

    public static int boxWidth(Font font, String text) {
        return Math.max(MIN_W, font.width(text) + (PAD_X * 2));
    }

    public static int boxHeight(Font font) {
        return PAD_TOP + font.lineHeight + GAP + BAR_H + PAD_BOTTOM;
    }

    public static int[] topLeft(int screenW, int screenH, int boxW, int boxH) {
        QZAConfig cfg = ConfigManager.get();
        int x = (int) Math.round((cfg.partyNotifyX * screenW) - (boxW / 2.0));
        int y = (int) Math.round((cfg.partyNotifyY * screenH) - (boxH / 2.0));
        return new int[]{clamp(x, screenW - boxW), clamp(y, screenH - boxH)};
    }

    public static void setCentre(double centreX, double centreY, int screenW, int screenH) {
        QZAConfig cfg = ConfigManager.get();
        cfg.partyNotifyX = fraction(centreX, screenW);
        cfg.partyNotifyY = fraction(centreY, screenH);
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        if (!ConfigManager.get().partyInviteNotifyEnabled || inviter == null) {
            return;
        }

        long duration = durationMs();
        long elapsed = System.currentTimeMillis() - shownAt;
        if (elapsed < 0 || elapsed >= duration) {
            inviter = null;
            return;
        }

        String text = textFor(inviter);
        float scale = scale();
        int boxW = Math.round(boxWidth(font, text) * scale);
        int boxH = Math.round(boxHeight(font) * scale);
        int[] pos = topLeft(graphics.guiWidth(), graphics.guiHeight(), boxW, boxH);

        double remaining = 1.0 - (elapsed / (double) duration);
        draw(graphics, font, text, pos[0], pos[1], scale, remaining, alphaFor(elapsed, duration));
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, String text,
                            int x, int y, float scale, double progress, float alpha) {
        int w = boxWidth(font, text);
        int h = boxHeight(font);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);

        graphics.fillGradient(0, 0, w, h, fade(BG_TOP, alpha), fade(BG_BOTTOM, alpha));
        border(graphics, w, h, alpha);

        graphics.centeredText(font, text, w / 2, PAD_TOP, fade(LABEL, alpha));

        int barY = PAD_TOP + font.lineHeight + GAP;
        int barW = w - (PAD_X * 2);
        graphics.fill(PAD_X, barY, PAD_X + barW, barY + BAR_H, fade(TRACK, alpha));

        int filled = (int) Math.round(barW * Math.max(0.0, Math.min(1.0, progress)));
        if (filled > 0) {
            graphics.fill(PAD_X, barY, PAD_X + filled, barY + BAR_H, fade(BAR, alpha));
        }

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

    private static float alphaFor(long elapsed, long duration) {
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

    private static int fade(int argb, float alpha) {
        float clamped = Math.max(0f, Math.min(1f, alpha));
        int a = Math.round(((argb >>> 24) & 0xFF) * clamped);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static double fraction(double pixels, int size) {
        if (size <= 0) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, pixels / size));
    }

    private static int clamp(int value, int max) {
        if (max <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(value, max));
    }

    private static String inviterName(String prefix) {
        int end = prefix.length();
        while (end > 0 && Character.isWhitespace(prefix.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && isNameChar(prefix.charAt(start - 1))) {
            start--;
        }

        String name = prefix.substring(start, end);
        if (name.length() < 2 || name.length() > 16) {
            return null;
        }

        int lead = start;
        while (lead > 0 && Character.isWhitespace(prefix.charAt(lead - 1))) {
            lead--;
        }
        if (lead > 0 && prefix.charAt(lead - 1) == ':') {
            return null;
        }

        return name;
    }

    private static boolean isNameChar(char c) {
        return c == '_'
                || (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z');
    }

    private static String stripCodes(String input) {
        if (input.indexOf('§') < 0) {
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
}
