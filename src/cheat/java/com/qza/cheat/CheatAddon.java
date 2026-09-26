package com.qza.cheat;

import com.qza.addon.QZAAddon;
import com.qza.gui.setting.Setting;
import com.qza.gui.setting.SliderSetting;
import com.qza.gui.setting.ToggleSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CheatAddon implements QZAAddon {
    private static final String CATEGORY = "Extras";

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public void resetSettings() {
        CheatConfigManager.reset();
        DeathBow.reset();
    }

    @Override
    public void addSettings(List<Setting> settings) {
        CheatConfig cfg = CheatConfigManager.get();

        settings.add(new ToggleSetting(CATEGORY, "Death Bow", "Death Bow",
                Component.literal("Swaps your hotbar slot and armor after you shoot a Death Bow")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.deathBowEnabled,
                v -> {
                    cfg.deathBowEnabled = v;
                    if (!v) {
                        DeathBow.reset();
                    }
                    CheatConfigManager.save();
                }));

        settings.add(new ToggleSetting(CATEGORY, "Death Bow", "Auto Swap Hotbar",
                Component.literal("Switches to the hotbar slot below the moment the bow is released")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.deathBowHotbarSwap,
                v -> {
                    cfg.deathBowHotbarSwap = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.deathBowEnabled));

        settings.add(new SliderSetting(CATEGORY, "Death Bow", "Hotbar Slot",
                Component.literal("Hotbar slot to switch to").withStyle(ChatFormatting.GRAY),
                1, 8, 1, "",
                () -> cfg.deathBowHotbarSlot,
                v -> {
                    cfg.deathBowHotbarSlot = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.deathBowEnabled && cfg.deathBowHotbarSwap));

        settings.add(new ToggleSetting(CATEGORY, "Death Bow", "Auto Raider Swap",
                Component.literal("Runs ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("/wd").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" after the bow is released and equips the "
                                + "wardrobe set below").withStyle(ChatFormatting.GRAY)),
                () -> cfg.deathBowRaiderSwap,
                v -> {
                    cfg.deathBowRaiderSwap = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.deathBowEnabled));

        settings.add(new SliderSetting(CATEGORY, "Death Bow", "Raider Slot",
                Component.literal("Wardrobe slot your Raider armor is in")
                        .withStyle(ChatFormatting.GRAY),
                1, 9, 1, "",
                () -> cfg.deathBowRaiderSlot,
                v -> {
                    cfg.deathBowRaiderSlot = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.deathBowEnabled && cfg.deathBowRaiderSwap));

        settings.add(new SliderSetting(CATEGORY, "Death Bow", "Close Delay",
                Component.literal("How long the wardrobe stays open after equipping. 0 closes it "
                        + "straight away").withStyle(ChatFormatting.GRAY),
                0, 20, 1, " ticks",
                () -> cfg.deathBowCloseDelay,
                v -> {
                    cfg.deathBowCloseDelay = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.deathBowEnabled && cfg.deathBowRaiderSwap));
    }
}
