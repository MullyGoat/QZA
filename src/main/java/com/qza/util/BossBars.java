package com.qza.util;

import com.qza.compat.Mc;
import com.qza.mixin.BossEventAccessor;
import com.qza.mixin.BossOverlayAccessor;
import net.minecraft.client.gui.components.LerpingBossEvent;

public final class BossBars {
    private BossBars() {
    }

    public static Float progress(String name) {
        if (!(Mc.bossOverlay() instanceof BossOverlayAccessor overlay)) {
            return null;
        }
        for (LerpingBossEvent event : overlay.qzaEvents().values()) {
            if (IgnUtil.stripCodes(event.getName().getString()).contains(name)) {
                return event instanceof BossEventAccessor target ? target.qzaTargetPercent() : event.getProgress();
            }
        }
        return null;
    }
}
