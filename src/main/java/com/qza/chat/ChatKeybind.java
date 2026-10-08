package com.qza.chat;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;

public final class ChatKeybind {
    public static final int NONE = -1;

    private static final long SWALLOW_WINDOW_MS = 200L;

    private static boolean capturing;
    private static long swallowUntil;

    private ChatKeybind() {
    }

    public static void swallowNextChar() {
        swallowUntil = System.currentTimeMillis() + SWALLOW_WINDOW_MS;
    }

    public static boolean consumeSwallow() {
        if (swallowUntil == 0) {
            return false;
        }
        boolean fresh = System.currentTimeMillis() <= swallowUntil;
        swallowUntil = 0;
        return fresh;
    }

    public static int key() {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.qzaChatEnabled) {
            return NONE;
        }
        return cfg.openChatWithT ? InputConstants.KEY_T : cfg.chatKeyCode;
    }

    public static boolean capturing() {
        return capturing;
    }

    public static void arm() {
        capturing = true;
    }

    public static void cancel() {
        capturing = false;
    }

    public static void capture(int keyCode) {
        capturing = false;
        if (keyCode == InputConstants.KEY_ESCAPE) {
            return;
        }
        QZAConfig cfg = ConfigManager.get();
        cfg.chatKeyCode = keyCode == InputConstants.KEY_DELETE ? NONE : keyCode;
        ConfigManager.save();
    }

    public static String label() {
        int code = ConfigManager.get().chatKeyCode;
        return code == NONE ? "Not set" : nameOf(code);
    }

    public static String nameOf(int keyCode) {
        try {
            return Mc.keyName(keyCode);
        } catch (Exception e) {
            return "Key " + keyCode;
        }
    }
}
