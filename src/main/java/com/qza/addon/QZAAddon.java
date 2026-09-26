package com.qza.addon;

import com.qza.gui.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

// Lets another build of QZA add a settings tab. The normal jar registers none.
public interface QZAAddon {
    String ENTRYPOINT = "qza:addon";

    String category();

    void addSettings(List<Setting> settings);

    default void resetSettings() {
    }

    static List<QZAAddon> all() {
        return FabricLoader.getInstance().getEntrypoints(ENTRYPOINT, QZAAddon.class);
    }
}
