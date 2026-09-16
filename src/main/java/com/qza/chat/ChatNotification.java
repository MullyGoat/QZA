package com.qza.chat;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import com.qza.notify.NotificationGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public final class ChatNotification {
    public static final double MIN_DURATION = 1.0;
    public static final double MAX_DURATION = 20.0;

    public static final String MODE_RINGER = "ringer";
    public static final String MODE_SILENT = "silent";
    public static final String MODE_DND = "dnd";

    private static String text;
    private static long shownAt;

    private ChatNotification() {
    }

    public static void show(String ign, String message) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.chatNotifyEnabled || MODE_DND.equals(cfg.chatNotifyMode)
                || !NotificationGate.allowsMessages()) {
            return;
        }

        text = ign + ": " + message;
        shownAt = System.currentTimeMillis();

        if (MODE_RINGER.equals(cfg.chatNotifyMode)) {
            ding();
        }
    }

    public static void clear() {
        text = null;
    }

    public static long durationMs() {
        double seconds = ConfigManager.get().chatNotifyDuration;
        return Math.round(Math.max(MIN_DURATION, Math.min(MAX_DURATION, seconds)) * 1000.0);
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.chatNotifyX = defaults.chatNotifyX;
        cfg.chatNotifyY = defaults.chatNotifyY;
        cfg.chatNotifyScale = defaults.chatNotifyScale;
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.chatNotifyEnabled || text == null) {
            return;
        }

        long duration = durationMs();
        long elapsed = System.currentTimeMillis() - shownAt;
        if (elapsed < 0 || elapsed >= duration) {
            text = null;
            return;
        }

        String shown = NotificationBox.fit(font, text);
        float scale = NotificationBox.clampScale(cfg.chatNotifyScale);
        int boxW = Math.round(NotificationBox.width(font, shown) * scale);
        int boxH = Math.round(NotificationBox.height(font) * scale);
        int[] pos = NotificationBox.topLeft(cfg.chatNotifyX, cfg.chatNotifyY,
                graphics.guiWidth(), graphics.guiHeight(), boxW, boxH);

        NotificationBox.draw(graphics, font, shown, pos[0], pos[1], scale,
                1.0 - (elapsed / (double) duration),
                NotificationBox.alphaFor(elapsed, duration),
                NotificationBox.CHAT_TEXT);
    }

    private static void ding() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.getSoundManager() != null) {
                client.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.0f));
            }
        });
    }
}
