package com.qza.tweaks;

import com.qza.config.ConfigManager;

public final class HotbarScrollLock {
    private HotbarScrollLock() {
    }

    public static int nextSlot(double wheel, int selected, int wrapped, int slots) {
        if (!ConfigManager.get().hotbarScrollLock) {
            return wrapped;
        }
        int unwrapped = selected - (int) Math.signum(wheel);
        return unwrapped < 0 || unwrapped >= slots ? selected : wrapped;
    }
}
