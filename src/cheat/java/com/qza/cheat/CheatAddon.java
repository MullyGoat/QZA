package com.qza.cheat;

import com.qza.addon.QZAAddon;
import com.qza.cheat.click.AutoClicker;
import com.qza.cheat.click.AutoClickerScreen;
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
import java.util.Locale;

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
        AutoClicker.reset();
        BloodClick.reset();
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
                                + "under Dungeons").withStyle(ChatFormatting.GRAY)),
                () -> cfg.starredMobEsp,
                v -> {
                    cfg.starredMobEsp = v;
                    CheatConfigManager.save();
                }));

        settings.add(new ToggleSetting(CATEGORY, "BloodCamp Triggerbot", "BloodCamp Triggerbot",
                Component.literal("Left clicks a blood mob 1 tick after it spawns if you're aiming at it while "
                        + "it's in the air, so it dies right away, and clicks again a few ticks later if it's "
                        + "still alive. Never "
                        + "clicks a mob on the floor, and only clicks the Watcher's mobs. It's a real left click")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.bloodClickEnabled,
                v -> {
                    cfg.bloodClickEnabled = v;
                    if (!v) {
                        BloodClick.reset();
                    }
                    CheatConfigManager.save();
                }));

        settings.add(new SliderSetting(CATEGORY, "BloodCamp Triggerbot", "Range",
                Component.literal("How far away a blood mob you're looking at can be and still get clicked")
                        .withStyle(ChatFormatting.GRAY),
                BloodClick.MIN_RANGE, BloodClick.MAX_RANGE, 1, " blocks",
                () -> cfg.bloodClickRange,
                v -> {
                    cfg.bloodClickRange = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.bloodClickEnabled));

        settings.add(new ToggleSetting(CATEGORY, "BloodCamp Triggerbot", "Only Whitelisted Items",
                Component.literal("Only clicks while you're holding one of the items you add below, so "
                        + "anything else in your hand never clicks").withStyle(ChatFormatting.GRAY),
                () -> cfg.bloodClickWhitelist,
                v -> {
                    cfg.bloodClickWhitelist = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.bloodClickEnabled));

        settings.add(new ActionSetting(CATEGORY, "BloodCamp Triggerbot", "Held Item",
                Component.literal("Adds the item in your hand to the whitelist, or takes it off. It goes by the "
                        + "item's own SkyBlock id, so another copy of the same item won't click")
                        .withStyle(ChatFormatting.GRAY),
                BloodClick::heldLabel,
                BloodClick::toggleHeld)
                .visibleWhen(() -> cfg.bloodClickEnabled && cfg.bloodClickWhitelist));

        settings.add(new ActionSetting(CATEGORY, "BloodCamp Triggerbot", "Items",
                Component.literal("See the whitelisted items, or take one off the list")
                        .withStyle(ChatFormatting.GRAY),
                "Open",
                () -> Mc.setScreen(new BloodClickScreen(new QZAScreen())))
                .visibleWhen(() -> cfg.bloodClickEnabled && cfg.bloodClickWhitelist));

        settings.add(new ToggleSetting(CATEGORY, "BloodCamp Triggerbot", "Debug Log",
                Component.literal("Writes what it sees in blood (the Watcher, skulls, each mob, where you aim and "
                                + "every click) to ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("config/qza/debug").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(", so a run where it didn't click can be tracked down")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.bloodClickDebug,
                v -> {
                    cfg.bloodClickDebug = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.bloodClickEnabled));

        addAutoClicker(settings, cfg);
        addMovementCopier(settings, cfg);
    }

    private static void addAutoClicker(List<Setting> settings, CheatConfig cfg) {
        String card = "Auto Clicker";

        settings.add(new ToggleSetting(CATEGORY, card, "Auto Clicker",
                Component.literal("Clicks for you while you hold left click, but only with the exact "
                                + "items you set it up on, so everything else swings normally. The gap "
                                + "between clicks is a different random length every time")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.autoClickerEnabled,
                v -> {
                    cfg.autoClickerEnabled = v;
                    if (!v) {
                        AutoClicker.reset();
                    }
                    CheatConfigManager.save();
                }));

        settings.add(new SliderSetting(CATEGORY, card, "Clicks",
                Component.literal("How fast to click the item you bind next. Each item keeps its own "
                        + "speed, so this only sets the one you're about to add")
                        .withStyle(ChatFormatting.GRAY),
                AutoClicker.MIN_CPS, AutoClicker.MAX_CPS, 1, " a second",
                () -> cfg.autoClickerCps,
                v -> {
                    cfg.autoClickerCps = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.autoClickerEnabled));

        settings.add(new ActionSetting(CATEGORY, card, "Held Item",
                Component.literal("Turns the auto clicker on or off for the item in your hand. It goes "
                                + "by the item's own SkyBlock id, so another copy of the same item "
                                + "won't click").withStyle(ChatFormatting.GRAY),
                AutoClicker::heldLabel,
                AutoClicker::toggleHeld)
                .visibleWhen(() -> cfg.autoClickerEnabled));

        settings.add(new ActionSetting(CATEGORY, card, "Items",
                Component.literal("Change how fast each item clicks, or take one off the list")
                        .withStyle(ChatFormatting.GRAY),
                "Open",
                () -> Mc.setScreen(new AutoClickerScreen(new QZAScreen())))
                .visibleWhen(() -> cfg.autoClickerEnabled));
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

        settings.add(new ToggleSetting(CATEGORY, card, "Place On Start Block",
                Component.literal("When you save a recording, puts it on the block you were standing on when it "
                        + "started, so it shows as a highlighted block, or a ring in boss. Discarded recordings aren't "
                        + "placed").withStyle(ChatFormatting.GRAY),
                () -> cfg.movementPlaceStart,
                v -> {
                    cfg.movementPlaceStart = v;
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

        settings.add(new SliderSetting(CATEGORY, card, "Start Turn Speed",
                Component.literal("How fast your camera turns to the recording's starting view before it plays. "
                        + "1x is the normal speed, higher is faster").withStyle(ChatFormatting.GRAY),
                0.25, 4, 0.05, "x",
                () -> cfg.movementTurnSpeed,
                v -> {
                    cfg.movementTurnSpeed = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.movementCopierEnabled));

        settings.add(new ToggleSetting(CATEGORY, card, "Start Turn Overshoot",
                Component.literal("Lets the starting turn go a little past or short of the view and then correct "
                        + "back, like a hand on a mouse. Turn it off to land straight on the view")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.movementTurnOvershoot,
                v -> {
                    cfg.movementTurnOvershoot = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.movementCopierEnabled));

        settings.add(new SliderSetting(CATEGORY, card, "Overshoot Amount",
                Component.literal("How many degrees the starting turn goes past the view before it comes back. "
                        + "Auto goes past by about 2-7% of the turn, and sometimes stops a little short instead. It "
                        + "never goes past by more than half the turn, and turns under 6 degrees don't overshoot")
                        .withStyle(ChatFormatting.GRAY),
                0, 15, 0.5, "",
                () -> cfg.movementOvershootDegrees,
                v -> {
                    cfg.movementOvershootDegrees = v;
                    CheatConfigManager.save();
                },
                v -> v <= 0 ? "Auto" : String.format(Locale.ROOT, "%.1f°", v))
                .visibleWhen(() -> cfg.movementCopierEnabled && cfg.movementTurnOvershoot));

        settings.add(new ToggleSetting(CATEGORY, card, "Match Recorded Speed",
                Component.literal("If you're faster than when you recorded, lets go of your movement keys for a "
                        + "tick whenever you get ahead of the route on the ground, so the route and its jumps land the "
                        + "same. It can't make you faster, so "
                        + "record at the lowest speed you'll have").withStyle(ChatFormatting.GRAY),
                () -> cfg.movementMatchSpeed,
                v -> {
                    cfg.movementMatchSpeed = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.movementCopierEnabled));

        settings.add(new SliderSetting(CATEGORY, card, "Your Speed",
                Component.literal("Your speed stat while playing a recording. Auto reads it every tick, so "
                        + "blessings and speed boosts mid-route are matched too. A number you set stays fixed for the "
                        + "whole route, so only set one if Auto shows the wrong speed").withStyle(ChatFormatting.GRAY),
                0, 1000, 5, "",
                () -> cfg.movementPlaySpeed,
                v -> {
                    cfg.movementPlaySpeed = v;
                    CheatConfigManager.save();
                },
                v -> v <= 0 ? "Auto" : String.valueOf((int) Math.round(v)))
                .visibleWhen(() -> cfg.movementCopierEnabled && cfg.movementMatchSpeed));

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

        settings.add(new ToggleSetting(CATEGORY, card, "Highlight Through Walls",
                Component.literal("Shows the highlighted blocks, rings and names through walls. Change a "
                        + "recording's colour in its Edit page")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.movementThroughWalls,
                v -> {
                    cfg.movementThroughWalls = v;
                    CheatConfigManager.save();
                })
                .visibleWhen(() -> cfg.movementCopierEnabled && cfg.movementHighlight));

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
