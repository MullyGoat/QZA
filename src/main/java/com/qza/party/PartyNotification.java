package com.qza.party;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import com.qza.notify.NotificationGate;
import com.qza.util.IgnUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class PartyNotification {
    public static final double MIN_DURATION = 1.0;
    public static final double MAX_DURATION = 10.0;

    private static final String MARKER = " has invited you to join ";

    private static String inviter;
    private static long shownAt;

    private PartyNotification() {
    }

    public static void onChatMessage(String raw) {
        if (!ConfigManager.get().partyInviteNotifyEnabled || !NotificationGate.allowsParty()) {
            return;
        }

        String message = IgnUtil.stripCodes(raw);
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
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.partyInviteNotifyEnabled || inviter == null) {
            return;
        }

        long duration = durationMs();
        long elapsed = System.currentTimeMillis() - shownAt;
        if (elapsed < 0 || elapsed >= duration) {
            inviter = null;
            return;
        }

        String text = NotificationBox.fit(font, textFor(inviter));
        float scale = NotificationBox.clampScale(cfg.partyNotifyScale);
        int boxW = Math.round(NotificationBox.width(font, text) * scale);
        int boxH = Math.round(NotificationBox.height(font) * scale);
        int[] pos = NotificationBox.topLeft(cfg.partyNotifyX, cfg.partyNotifyY,
                graphics.guiWidth(), graphics.guiHeight(), boxW, boxH);

        NotificationBox.draw(graphics, font, text, pos[0], pos[1], scale,
                1.0 - (elapsed / (double) duration),
                NotificationBox.alphaFor(elapsed, duration),
                NotificationBox.PARTY_TEXT);
    }

    private static String inviterName(String prefix) {
        String name = IgnUtil.trailingName(prefix);
        if (name == null) {
            return null;
        }

        int lead = IgnUtil.nameStart(prefix, name);
        while (lead > 0 && Character.isWhitespace(prefix.charAt(lead - 1))) {
            lead--;
        }
        if (lead > 0 && prefix.charAt(lead - 1) == ':') {
            return null;
        }

        return name;
    }
}
