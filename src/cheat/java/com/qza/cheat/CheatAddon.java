package com.qza.cheat;

import com.qza.addon.QZAAddon;
import com.qza.cheat.movement.MovementCopier;
import com.qza.cheat.movement.RecordingsScreen;
import com.qza.compat.Mc;
import com.qza.gui.QZAScreen;
import com.qza.gui.setting.ActionSetting;
import com.qza.gui.setting.Setting;
import com.qza.gui.setting.SliderSetting;
import com.qza.gui.setting.ToggleSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CheatAddon implements QZAAddon {
    private static final String CATEGORY = "Cheats";

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public void resetSettings() {
        CheatConfigManager.reset();
        DeathBow.reset();
        MelodyAim.reset();
        MovementCopier.reset();
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

        settings.add(new ToggleSetting(CATEGORY, "Melody", "Aim Mouse at Melody Button",
                Component.literal("Puts cursor on the first melody button as terminal opens. "
                                + "Nothing is sent to the server")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.melodyAimEnabled,
                v -> {
                    cfg.melodyAimEnabled = v;
                    CheatConfigManager.save();
                }));

        settings.add(new ToggleSetting(CATEGORY, "Starred Mobs", "Starred Mob ESP",
                Component.literal("Shows the ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Starred Mob Highlight")
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" boxes through walls. Turn the highlight on "
                                + "under F7 / M7").withStyle(ChatFormatting.GRAY)),
                () -> cfg.starredMobEsp,
                v -> {
                    cfg.starredMobEsp = v;
                    CheatConfigManager.save();
                }));

        addMovementCopier(settings, cfg);
    }

    private static void addMovementCopier(List<Setting> settings, CheatConfig cfg) {
        String card = "Movement Copier";

        settings.add(new ToggleSetting(CATEGORY, card, "Movement Copier",
                Component.literal("Records your keys, clicks, mouse and hotbar, then plays them back when you "
                                + "teleport onto the block you place the recording on. Record with ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("/qza movement copy start").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" and manage them with ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("/qza recorder").withStyle(ChatFormatting.LIGHT_PURPLE)),
                () -> cfg.movementCopierEnabled,
                v -> {
                    cfg.movementCopierEnabled = v;
                    if (!v) {
                        MovementCopier.reset();
                    }
                    CheatConfigManager.save();
                }));

        settings.add(new ActionSetting(CATEGORY, card, "Record Key",
                Component.literal("Starts a recording after the countdown, finishes it, or stops a playback. ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Delete").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" clears it and ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("esc").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" cancels").withStyle(ChatFormatting.GRAY)),
                () -> MovementCopier.capturing() ? "Press a key..." : MovementCopier.keyLabel(),
                MovementCopier::startCapture)
                .visibleWhen(() -> cfg.movementCopierEnabled));

        settings.add(new ActionSetting(CATEGORY, card, "Recordings",
                Component.literal("Rename, trim, speed up, play or delete your recordings")
                        .withStyle(ChatFormatting.GRAY),
                "Open",
                () -> Mc.setScreen(new RecordingsScreen(new QZAScreen())))
                .visibleWhen(() -> cfg.movementCopierEnabled));

        settings.add(new SliderSetting(CATEGORY, card, "Countdown",
                Component.literal("How long to wait before the recording starts").withStyle(ChatFormatting.GRAY),
                0, 10, 1, "s",
                () -> cfg.movementCountdown,
                v -> {
                    cfg.movementCountdown = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.movementCopierEnabled));

        settings.add(new SliderSetting(CATEGORY, card, "Lag Tolerance",
                Component.literal("How long the server can go quiet before a playback pauses and waits for it")
                        .withStyle(ChatFormatting.GRAY),
                1, 20, 1, " ticks",
                () -> cfg.movementLagTolerance,
                v -> {
                    cfg.movementLagTolerance = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.movementCopierEnabled));

        settings.add(new SliderSetting(CATEGORY, card, "Walk-In Speed",
                Component.literal("The fastest you can be moving when you step into a boss ring for it to start. "
                        + "Slower lines the recording up better").withStyle(ChatFormatting.GRAY),
                1, 30, 1, " blocks/s",
                () -> cfg.movementWalkSpeed,
                v -> {
                    cfg.movementWalkSpeed = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.movementCopierEnabled));

        settings.add(new ToggleSetting(CATEGORY, card, "Highlight Blocks",
                Component.literal("Shows the blocks your recordings are placed on, with their names, and the "
                        + "ring you walk into in boss")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.movementHighlight,
                v -> {
                    cfg.movementHighlight = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.movementCopierEnabled));

        settings.add(new ToggleSetting(CATEGORY, card, "Debug Log",
                Component.literal("Writes what every teleport and playback tick does to ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("config/qza/debug").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(", so a route that goes wrong can be tracked down")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.movementDebug,
                v -> {
                    cfg.movementDebug = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.movementCopierEnabled));
    }
}
