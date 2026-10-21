package com.qza.gui.setting;

import com.qza.addon.QZAAddon;
import com.qza.chat.ChatFocus;
import com.qza.chat.ChatHistory;
import com.qza.chat.ChatKeybind;
import com.qza.chat.ChatNotification;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.custom.CustomItems;
import com.qza.discord.DiscordAlert;
import com.qza.dungeon.CrystalTimer;
import com.qza.dungeon.LeapMenu;
import com.qza.dungeon.LeapNotification;
import com.qza.dungeon.NecronLeap;
import com.qza.dungeon.PYTimer;
import com.qza.dungeon.RelicTimer;
import com.qza.dungeon.StormTimer;
import com.qza.dungeon.TerminalTracker;
import com.qza.dungeon.WitherKey;
import com.qza.gui.CommandKeybindScreen;
import com.qza.gui.CommandShortcutScreen;
import com.qza.gui.CustomItemScreen;
import com.qza.gui.LeapOrderScreen;
import com.qza.gui.MusicNamesScreen;
import com.qza.gui.QZAChatScreen;
import com.qza.gui.WaypointScreen;
import com.qza.inventory.ItemProtection;
import com.qza.inventory.StoragePreview;
import com.qza.inventory.TerminatorIndicator;
import com.qza.gui.TerminatorScreen;
import com.qza.gui.setting.NumberSetting.Field;
import com.qza.itemlist.ItemRepo;
import com.qza.media.MusicOverlay;
import com.qza.music.MusicAliases;
import com.qza.music.MusicLibrary;
import com.qza.music.MusicManager;
import com.qza.notify.NotificationGate;
import com.qza.party.PartyNotification;
import com.qza.price.PriceTooltip;
import com.qza.render.ItemGlow;
import com.qza.search.MarketSearch;
import com.qza.shitter.ShitterListPage;
import com.qza.stats.DungeonFloor;
import com.qza.timer.ClockDisplay;
import com.qza.tweaks.CommandShortcuts;
import com.qza.tweaks.FullBright;
import com.qza.tweaks.ItemInspect;
import com.qza.tweaks.ItemStars;
import com.qza.tweaks.PlayerSize;
import com.qza.tweaks.TooltipScale;
import com.qza.util.ChatUtil;
import com.qza.waypoint.WaypointColour;
import com.qza.waypoint.WaypointEditor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public final class SettingsRegistry {
    private static final List<QZAAddon> ADDONS = QZAAddon.all();

    public static final List<String> CATEGORIES = withAddons(List.of(
            "Shitter List",
            "Dungeons",
            "Chat",
            "Auto Check Stats",
            "Notifications",
            "Inventory",
            "Miscellaneous"));

    private static final Map<String, String> CARDS = Map.ofEntries(
            Map.entry("Dungeons/Track Selection", "Terminal Music"),
            Map.entry("Dungeons/Music Library", "Terminal Music"),
            Map.entry("Dungeons/Music Playback", "Terminal Music"),
            Map.entry("Chat/Open Chat", "QZA Chat"),
            Map.entry("Chat/History", "QZA Chat"),
            Map.entry("Auto Check Stats/Stats", "Auto Check Stats"),
            Map.entry("Auto Check Stats/Requirements", "Auto Check Stats"),
            Map.entry("Notifications/Dungeon Runs", "Dungeon Only Notifications"),
            Map.entry("Notifications/Party", "Party Invite Alert"),
            Map.entry("Notifications/QZA Chat", "Message Alert"),
            Map.entry("Notifications/Discord", "Party Full Alert"),
            Map.entry("Miscellaneous/Interface", "GUI Scale"));

    private SettingsRegistry() {
    }

    private static List<String> withAddons(List<String> base) {
        List<String> categories = new ArrayList<>(base);
        for (QZAAddon addon : ADDONS) {
            categories.add(categories.size() - 1, addon.category());
        }
        return List.copyOf(categories);
    }

    public static List<Setting> build() {
        QZAConfig cfg = ConfigManager.get();
        List<Setting> settings = new ArrayList<>();

        String shitter = "Shitter List";

        settings.add(new ToggleSetting(shitter, "Auto-Kick", "ShitterList Toggle",
                Component.literal("If disabled, players on the list will NOT be kicked. ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Do ").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("/qzahelp").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" for commands").withStyle(ChatFormatting.GREEN)),
                () -> cfg.shitterListEnabled,
                v -> {
                    cfg.shitterListEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(shitter, "Auto-Kick", "Dungeon Groups Only",
                Component.literal("Only kicks shitters when joining through party finder, ignores normal party joins if toggled on")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.restrictToDungeonGroups,
                v -> {
                    cfg.restrictToDungeonGroups = v;
                    ConfigManager.save();
                }));

        settings.add(new ActionSetting(shitter, "The List", "Open Shitter List",
                Component.literal("Displays list of shitters").withStyle(ChatFormatting.GRAY),
                "Open",
                () -> {
                    Mc.setScreen(null);
                    ShitterListPage.print(1);
                }));

        String f7 = "Dungeons";

        settings.add(new ToggleSetting(f7, "Terminal Music", "Terminal Music Toggle",
                Component.literal("Plays music during terminal phase").withStyle(ChatFormatting.GRAY),
                () -> cfg.terminalMusicEnabled,
                v -> {
                    cfg.terminalMusicEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(f7, "Track Selection", "Shuffle Mode",
                Component.literal("Picks a random song every run")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.shuffleMode,
                v -> {
                    cfg.shuffleMode = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalMusicEnabled));

        settings.add(new DropdownSetting(f7, "Track Selection", "Chosen Track",
                Component.literal("Plays chosen song during terminal phase")
                        .withStyle(ChatFormatting.GRAY),
                SettingsRegistry::trackNames,
                () -> cfg.selectedTrack,
                v -> {
                    cfg.selectedTrack = v;
                    ConfigManager.save();
                },
                MusicAliases::display,
                MusicLibrary::reload,
                "(no music)",
                170,
                () -> {
                    MusicLibrary.reload();
                    Mc.setScreen(new MusicNamesScreen());
                })
                .visibleWhen(() -> cfg.terminalMusicEnabled && !cfg.shuffleMode));

        settings.add(new ActionSetting(f7, "Music Library", "Add Music",
                Component.literal("Opens QZA's music folder - Only drag and drop ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(".mp3").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(".ogg").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" or ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(".wav").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" files to play").withStyle(ChatFormatting.GRAY)),
                "Open Folder",
                MusicLibrary::openFolder)
                .visibleWhen(() -> cfg.terminalMusicEnabled));

        settings.add(new ActionSetting(f7, "Music Library", "Reload Playlist",
                Component.literal("Re-scan the folder after adding files.").withStyle(ChatFormatting.GRAY),
                "Refresh",
                () -> {
                    int found = MusicLibrary.reload().size();
                    ChatUtil.success("Found " + found + " track" + (found == 1 ? "" : "s") + ".");
                })
                .visibleWhen(() -> cfg.terminalMusicEnabled));

        settings.add(new ActionSetting(f7, "Music Library", "Test Playback",
                Component.literal("Tests the output of a song in the folder")
                        .withStyle(ChatFormatting.GRAY),
                () -> MusicManager.get().isPlaying() ? "Stop" : "Play",
                () -> MusicManager.get().toggleTestPlayback())
                .visibleWhen(() -> cfg.terminalMusicEnabled));

        settings.add(new SliderSetting(f7, "Music Playback", "Volume",
                Component.literal("Independent of Minecraft's own music slider.")
                        .withStyle(ChatFormatting.GRAY),
                0, 100, 1, "%",
                () -> cfg.musicVolume,
                v -> {
                    cfg.musicVolume = v;
                    MusicManager.get().applySettings();
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalMusicEnabled));

        settings.add(new SliderSetting(f7, "Music Playback", "Fade Length",
                Component.literal("The fade in and fade out duration of songs")
                        .withStyle(ChatFormatting.GRAY),
                0, 5000, 100, "ms",
                () -> cfg.fadeMillis,
                v -> {
                    cfg.fadeMillis = v;
                    MusicManager.get().applySettings();
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalMusicEnabled));

        settings.add(new ToggleSetting(f7, "Waypoints", "Waypoints",
                Component.literal("Easily highlight blocks around Skyblock")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.waypointsEnabled,
                v -> {
                    cfg.waypointsEnabled = v;
                    if (!v) {
                        WaypointEditor.stop();
                    }
                    ConfigManager.save();
                }));

        settings.add(new ActionSetting(f7, "Waypoints", "Edit Waypoints",
                Component.literal("View and change the coords, colour, size and name of "
                                + "every waypoint").withStyle(ChatFormatting.GRAY),
                "Open",
                () -> Mc.setScreen(new WaypointScreen()))
                .visibleWhen(() -> cfg.waypointsEnabled));

        settings.add(new ActionSetting(f7, "Waypoints", "Manual Waypoint Add",
                Component.literal("Closes the menu and lets you right click blocks to mark "
                                + "them. Right click a marked block to clear it. Press ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Esc").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" to finish.").withStyle(ChatFormatting.GRAY)),
                () -> WaypointEditor.active() ? "Stop" : "Add",
                () -> {
                    Mc.setScreen(null);
                    WaypointEditor.toggle();
                })
                .visibleWhen(() -> cfg.waypointsEnabled));

        settings.add(new ToggleSetting(f7, "Waypoints", "Display name of waypoint",
                Component.literal("Displays name of waypoint above highlighted blocks")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.waypointShowNames,
                v -> {
                    cfg.waypointShowNames = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.waypointsEnabled));

        settings.add(new ToggleSetting(f7, "Waypoints", "Dungeons Only",
                Component.literal("Only shows waypoints inside a dungeon, so they do not "
                                + "clutter the hub").withStyle(ChatFormatting.GRAY),
                () -> cfg.waypointsDungeonOnly,
                v -> {
                    cfg.waypointsDungeonOnly = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.waypointsEnabled));

        settings.add(new ToggleSetting(f7, "Leap Menu", "Leap Menu",
                Component.literal("Swaps Hypixel's Spirit Leap and Infinileap menu for four big player boxes. "
                                + "Click anywhere in a corner of the screen to leap to the player in that corner")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.leapMenuEnabled,
                v -> {
                    cfg.leapMenuEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new SliderSetting(f7, "Leap Menu", "Menu Scale",
                Component.literal("Size of the player boxes").withStyle(ChatFormatting.GRAY),
                LeapMenu.MIN_SCALE, LeapMenu.MAX_SCALE, 5, "%",
                () -> cfg.leapMenuScale,
                v -> {
                    cfg.leapMenuScale = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.leapMenuEnabled));

        settings.add(new ToggleSetting(f7, "Leap Menu", "Show Last Door Opener",
                Component.literal("Lights up the box of the player who opened the last Wither door, until the "
                                + "Blood Room opens").withStyle(ChatFormatting.GRAY),
                () -> cfg.leapMenuDoorOpener,
                v -> {
                    cfg.leapMenuDoorOpener = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.leapMenuEnabled));

        settings.add(new ToggleSetting(f7, "Leap Menu", "Tint Dead Players",
                Component.literal("Tints the boxes of dead players ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("red").withStyle(ChatFormatting.RED)),
                () -> cfg.leapMenuTintDead,
                v -> {
                    cfg.leapMenuTintDead = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.leapMenuEnabled));

        settings.add(new ToggleSetting(f7, "Leap Menu", "Left Click Only",
                Component.literal("Only leaps when you left click, so a right or middle click does nothing")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.leapMenuLeftClickOnly,
                v -> {
                    cfg.leapMenuLeftClickOnly = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.leapMenuEnabled));

        settings.add(new DropdownSetting(f7, "Leap Menu", "Leap Order",
                Component.literal("Which corner each player goes in. ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Odin Sorting").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" puts ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Archer").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(" top left, ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Berserk").withStyle(ChatFormatting.DARK_RED))
                        .append(Component.literal(" top right, ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Healer").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" bottom left and ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Mage").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" or ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Tank").withStyle(ChatFormatting.DARK_GREEN))
                        .append(Component.literal(" bottom right. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Custom Sorting").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" uses your own order").withStyle(ChatFormatting.GRAY)),
                () -> LeapMenu.ORDERS,
                () -> cfg.leapMenuOrder,
                v -> {
                    cfg.leapMenuOrder = v;
                    ConfigManager.save();
                },
                LeapMenu::label,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.leapMenuEnabled));

        settings.add(new ActionSetting(f7, "Leap Menu", "Custom Order",
                Component.literal("Previews the leap menu. Pick the class you're playing and drag the others "
                                + "to where you want them. Each class keeps its own order")
                        .withStyle(ChatFormatting.GRAY),
                "Edit",
                () -> Mc.setScreen(new LeapOrderScreen()))
                .visibleWhen(() -> cfg.leapMenuEnabled && LeapMenu.ORDER_CUSTOM.equals(cfg.leapMenuOrder)));

        settings.add(new ToggleSetting(f7, "Wither Key Pickup", "Wither Key Pickup",
                Component.literal("Shows ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("WITHER KEY").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(" on screen - grey until the key drops, ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("red").withStyle(ChatFormatting.RED))
                        .append(Component.literal(" while it is on the floor, ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("green").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" once it is picked up. Drag it in ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Edit GUI")
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(".").withStyle(ChatFormatting.GRAY)),
                () -> cfg.witherKeyEnabled,
                v -> {
                    cfg.witherKeyEnabled = v;
                    WitherKey.reset();
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(f7, "Split Timers", "Split Timers",
                Component.literal("Updated Split Timers").withStyle(ChatFormatting.GRAY),
                () -> cfg.splitTimersEnabled,
                v -> {
                    cfg.splitTimersEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(f7, "Split Timers", "Show Tick Time",
                Component.literal("Shows the lag-free server tick time next to each split")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.splitTimersTickTime,
                v -> {
                    cfg.splitTimersTickTime = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.splitTimersEnabled));

        settings.add(new ToggleSetting(f7, "Split Timers", "Boss Entry Split",
                Component.literal("Adds a split from the start of the run to boss entry")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.splitTimersBossEntry,
                v -> {
                    cfg.splitTimersBossEntry = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.splitTimersEnabled));

        settings.add(new ToggleSetting(f7, "Time Lost to Lag", "Time Lost to Lag",
                Component.literal("When the run ends, says how much longer it took in real time than in server ticks")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.lagTimerEnabled,
                v -> {
                    cfg.lagTimerEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new DropdownSetting(f7, "Time Lost to Lag", "Announce Mode",
                Component.literal("Send the time lost to the whole party, or only to yourself.")
                        .withStyle(ChatFormatting.GRAY),
                () -> List.of("party", "client"),
                () -> cfg.lagAnnounceMode,
                v -> {
                    cfg.lagAnnounceMode = v;
                    ConfigManager.save();
                },
                SettingsRegistry::announceModeLabel,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.lagTimerEnabled));

        settings.add(new ToggleSetting(f7, "Terminal Tracker", "Terminal Tracker",
                Component.literal("Shows how many terminals, levers and devices are done in the current terminal "
                                + "section, starting at 0/7. A device done by the Berserk during Section 1 is the "
                                + "Section 4 device, so it counts for Section 4 instead. Move it in ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Edit GUI").withStyle(ChatFormatting.LIGHT_PURPLE)),
                () -> cfg.terminalTrackerEnabled,
                v -> {
                    cfg.terminalTrackerEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(f7, "Terminal Tracker", "Section Done Alert",
                Component.literal("When a section's terminals, levers and device are all done, shows ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("S1 Done!").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" (up to S4) on screen and plays a ding")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.terminalDoneAlert,
                v -> {
                    cfg.terminalDoneAlert = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalTrackerEnabled));

        settings.add(new ToggleSetting(f7, "Terminal Tracker", "Tell Party",
                Component.literal("Also says ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("S1 Done!").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" and so on in party chat").withStyle(ChatFormatting.GRAY)),
                () -> cfg.terminalDoneParty,
                v -> {
                    cfg.terminalDoneParty = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalTrackerEnabled && cfg.terminalDoneAlert));

        settings.add(new ToggleSetting(f7, "Terminal Tracker", "Gate Destroyed Alert",
                Component.literal("Shows ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Gate Destroyed").withStyle(ChatFormatting.RED))
                        .append(Component.literal(" on screen every time a P3 gate is destroyed")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.terminalGateAlert,
                v -> {
                    cfg.terminalGateAlert = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalTrackerEnabled));

        settings.add(new SliderSetting(f7, "Terminal Tracker", "Alert Duration",
                Component.literal("How long ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("S1 Done!").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Gate Destroyed").withStyle(ChatFormatting.RED))
                        .append(Component.literal(" and the other alerts stay on screen")
                                .withStyle(ChatFormatting.GRAY)),
                TerminalTracker.MIN_ALERT_SECONDS, TerminalTracker.MAX_ALERT_SECONDS, 0.25, "s",
                () -> cfg.terminalDoneSeconds,
                v -> {
                    cfg.terminalDoneSeconds = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalTrackerEnabled && (cfg.terminalDoneAlert || cfg.terminalGateAlert)));

        settings.add(new ToggleSetting(f7, "PY Timer", "PY Timer",
                Component.literal("Updated Timer for PY in Storm Phase")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.pyTimerEnabled,
                v -> {
                    cfg.pyTimerEnabled = v;
                    PYTimer.reset();
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(f7, "Storm Timer", "Storm Timer",
                Component.literal("Counts how long Storm phase has lasted in tick time, from Storm's first line "
                                + "until he dies. Drag it in ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Edit GUI").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(".").withStyle(ChatFormatting.GRAY)),
                () -> cfg.stormTimerEnabled,
                v -> {
                    cfg.stormTimerEnabled = v;
                    StormTimer.reset();
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(f7, "Storm Timer", "Count Down From 5",
                Component.literal("Only shows the last 5 seconds before 29s into Storm, counting down to 0")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.stormTimerCountDown,
                v -> {
                    cfg.stormTimerCountDown = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.stormTimerEnabled));

        settings.add(colourSetting(f7, "Storm Timer", "Timer Colour", "Colour of the Storm Timer",
                () -> cfg.stormTimerColour, v -> cfg.stormTimerColour = v)
                .visibleWhen(() -> cfg.stormTimerEnabled));

        settings.add(new ToggleSetting(f7, "Crystal Spawn Timer", "Crystal Spawn Timer",
                Component.literal("Shows ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Crystals Spawned").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" when Maxor starts. Once both crystals are placed it shows ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Crystals Spawning in").withStyle(ChatFormatting.RED))
                        .append(Component.literal(", counting down in tick time to the second crystals, 8 seconds "
                                        + "into Maxor. Drag it in ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Edit GUI").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(".").withStyle(ChatFormatting.GRAY)),
                () -> cfg.crystalTimerEnabled,
                v -> {
                    cfg.crystalTimerEnabled = v;
                    CrystalTimer.reset();
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(f7, "Necron Leap Notifier", "Necron Leap Notifier",
                Component.literal("On M7, tells you when to leap down to P5 for your class. ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Berserk").withStyle(ChatFormatting.DARK_RED))
                        .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Archer").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(" and ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Tank").withStyle(ChatFormatting.DARK_GREEN))
                        .append(Component.literal(" leap when Necron is at 70M HP, ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Mage").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" when he dies. Healers get no notification. Drag it in ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Edit GUI").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(".").withStyle(ChatFormatting.GRAY)),
                () -> cfg.necronLeapEnabled,
                v -> {
                    cfg.necronLeapEnabled = v;
                    NecronLeap.reset();
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(f7, "Relic Spawn Timer", "Relic Spawn Timer",
                Component.literal("On M7, shows ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Relics: 3.05s").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" from Necron's death, counting down in tick time to when the relics "
                                + "spawn. Drag it in ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Edit GUI").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(".").withStyle(ChatFormatting.GRAY)),
                () -> cfg.relicTimerEnabled,
                v -> {
                    cfg.relicTimerEnabled = v;
                    RelicTimer.reset();
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(f7, "Starred Mobs", "Starred Mob Highlight",
                Component.literal("Draws a box around starred mobs in dungeons")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.starredMobsEnabled,
                v -> {
                    cfg.starredMobsEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(colourSetting(f7, "Starred Mobs", "Star Mob Colour", "Colour of the box around starred mobs",
                () -> cfg.starredMobColour, v -> cfg.starredMobColour = v)
                .visibleWhen(() -> cfg.starredMobsEnabled));

        settings.add(new ToggleSetting(f7, "Starred Mobs", "Highlight Bats",
                Component.literal("Also boxes bats in dungeons").withStyle(ChatFormatting.GRAY),
                () -> cfg.starredMobsBats,
                v -> {
                    cfg.starredMobsBats = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.starredMobsEnabled));

        settings.add(colourSetting(f7, "Starred Mobs", "Bat Colour", "Colour of the box around bats",
                () -> cfg.starredMobsBatColour, v -> cfg.starredMobsBatColour = v)
                .visibleWhen(() -> cfg.starredMobsEnabled && cfg.starredMobsBats));

        settings.add(new ToggleSetting(f7, "Starred Mobs", "Highlight Fels",
                Component.literal("Also boxes Fels, even while they are invisible")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.starredMobsFels,
                v -> {
                    cfg.starredMobsFels = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.starredMobsEnabled));

        settings.add(colourSetting(f7, "Starred Mobs", "Fel Colour", "Colour of the box around Fels",
                () -> cfg.starredMobsFelColour, v -> cfg.starredMobsFelColour = v)
                .visibleWhen(() -> cfg.starredMobsEnabled && cfg.starredMobsFels));

        settings.add(new ToggleSetting(f7, "Blood Camp Tracker", "Blood Camp Tracker",
                Component.literal("Boxes where each blood mob will land and counts down until it spawns")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.bloodTrackerEnabled,
                v -> {
                    cfg.bloodTrackerEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(f7, "Blood Camp Tracker", "Flight Line",
                Component.literal("Draws a line from each flying skull to where its mob will land")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.bloodTrackerLine,
                v -> {
                    cfg.bloodTrackerLine = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.bloodTrackerEnabled));

        settings.add(new ToggleSetting(f7, "Blood Camp Tracker", "Spawn Timer",
                Component.literal("Shows the seconds left until the mob spawns above its box")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.bloodTrackerTimer,
                v -> {
                    cfg.bloodTrackerTimer = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.bloodTrackerEnabled));

        settings.add(new ToggleSetting(f7, "Blood Camp Tracker", "Watcher Move Timer",
                Component.literal("Counts down above the Watcher until it moves, starting from its greeting")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.bloodTrackerWatcherTimer,
                v -> {
                    cfg.bloodTrackerWatcherTimer = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.bloodTrackerEnabled));

        settings.add(new ToggleSetting(f7, "Blood Camp Tracker", "Kill Mobs Alert",
                Component.literal("Shows ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Kill Mobs").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" 20 seconds after the Watcher's greeting").withStyle(ChatFormatting.GRAY)),
                () -> cfg.bloodTrackerKillAlert,
                v -> {
                    cfg.bloodTrackerKillAlert = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.bloodTrackerEnabled));

        String chat = "Chat";

        settings.add(new ToggleSetting(chat, "QZA Chat", "QZA Chat Toggle",
                Component.literal("All in one chat GUI for Hypixel")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.qzaChatEnabled,
                v -> {
                    cfg.qzaChatEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ActionSetting(chat, "QZA Chat", "Open QZA Chat",
                Component.literal("Opens QZA Chat").withStyle(ChatFormatting.GRAY),
                () -> {
                    int unread = ChatHistory.unreadTotal();
                    return unread > 0 ? "Open (" + unread + ")" : "Open";
                },
                () -> Mc.setScreen(new QZAChatScreen()))
                .visibleWhen(() -> cfg.qzaChatEnabled));

        settings.add(new ToggleSetting(chat, "QZA Chat", "Bottom Left",
                Component.literal("Opens QZA Chat in the bottom left, in place of Minecraft's chat")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.chatBottomLeft,
                v -> {
                    cfg.chatBottomLeft = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.qzaChatEnabled));

        settings.add(new SliderSetting(chat, "QZA Chat", "Bottom Left Text Size",
                Component.literal("Size of the message text while QZA Chat is in the bottom left")
                        .withStyle(ChatFormatting.GRAY),
                50, 100, 5, "%",
                () -> cfg.chatDockTextScale,
                v -> {
                    cfg.chatDockTextScale = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.qzaChatEnabled && cfg.chatBottomLeft));

        settings.add(new ToggleSetting(chat, "QZA Chat", "QZA Style Chat",
                Component.literal("Gives Minecraft's chat the background and text style of QZA Chat")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.chatQzaStyle,
                v -> {
                    cfg.chatQzaStyle = v;
                    ConfigManager.save();
                    ChatComponent vanilla = Mc.chat();
                    if (vanilla != null) {
                        vanilla.resetChatScroll();
                    }
                })
                .visibleWhen(() -> cfg.qzaChatEnabled));

        settings.add(new ToggleSetting(chat, "QZA Chat", "Hide Vanilla Chat",
                Component.literal("Hides Minecraft's chat in the corner while QZA Chat is open")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.hideVanillaChat,
                v -> {
                    cfg.hideVanillaChat = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.qzaChatEnabled && !cfg.chatBottomLeft));

        settings.add(new ToggleSetting(chat, "Clean Up", "Hide Useless Messages",
                Component.literal("Hides Hypixel spam like lobby joins, Warping..., profile info, Watchdog, "
                                + "ability cooldowns, heal and damage lines and essence pickups. An archer's "
                                + "Explosive Shot damage still shows in dungeons")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.chatHideUseless,
                v -> {
                    cfg.chatHideUseless = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(chat, "Clean Up", "Hide Separator Lines",
                Component.literal("Hides the - - - - - and ▬▬▬ lines Hypixel puts around /fl, /pl and run results")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.chatHideSeparators,
                v -> {
                    cfg.chatHideSeparators = v;
                    ConfigManager.save();
                }));

        settings.add(new DropdownSetting(chat, "Open Chat", "Default Tab",
                Component.literal("Select which tab QZA Chat goes to when opened")
                        .withStyle(ChatFormatting.GRAY),
                () -> ChatFocus.OPTIONS,
                () -> cfg.chatDefaultTab,
                v -> {
                    cfg.chatDefaultTab = v;
                    ConfigManager.save();
                },
                ChatFocus::label,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.qzaChatEnabled));

        settings.add(new ToggleSetting(chat, "Open Chat", "Open With T and /",
                Component.literal("Override both chat keybinds to open QZA Chat")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.openChatWithT,
                v -> {
                    cfg.openChatWithT = v;
                    ChatKeybind.cancel();
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.qzaChatEnabled));

        settings.add(new ActionSetting(chat, "Open Chat", "Custom Key",
                Component.literal("Custom keybind to open QZA Chat: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Delete").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" clears it and ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("esc").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" cancels").withStyle(ChatFormatting.GRAY)),
                () -> ChatKeybind.capturing() ? "Press a key..." : ChatKeybind.label(),
                ChatKeybind::arm)
                .visibleWhen(() -> cfg.qzaChatEnabled && !cfg.openChatWithT));

        settings.add(new ToggleSetting(chat, "History", "Unlimited Tab History",
                Component.literal("Keeps every message from the current session saved in "
                                + "every tab").withStyle(ChatFormatting.GRAY),
                () -> cfg.chatUnlimitedHistory,
                v -> {
                    cfg.chatUnlimitedHistory = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.qzaChatEnabled));

        settings.add(new DropdownSetting(chat, "History", "DM History",
                Component.literal("Wipes DM History after closing game or keeps it forever")
                        .withStyle(ChatFormatting.GRAY),
                () -> List.of(ChatHistory.MODE_FOREVER, ChatHistory.MODE_SESSION),
                () -> cfg.chatHistoryMode,
                v -> {
                    cfg.chatHistoryMode = v;
                    ConfigManager.save();
                    ChatHistory.save();
                },
                SettingsRegistry::historyModeLabel,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.qzaChatEnabled));

        boolean[] confirmClear = {false};
        settings.add(new ActionSetting(chat, "History", "Clear DMs",
                Component.literal("Deletes every saved DM conversation on every account. ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Click twice to confirm.")
                                .withStyle(ChatFormatting.RED)),
                () -> confirmClear[0] ? "Confirm?" : "Clear",
                () -> {
                    if (!confirmClear[0]) {
                        confirmClear[0] = true;
                        return;
                    }
                    confirmClear[0] = false;
                    ChatHistory.clear();
                    ChatUtil.success("Cleared saved messages.");
                })
                .visibleWhen(() -> cfg.qzaChatEnabled));

        String invite = "Auto Check Stats";

        settings.add(new ToggleSetting(invite, "Stats", "Auto Check Stats",
                Component.literal("When a player whispers \"lf inv\", automatically "
                                + "check their stats for selected floor")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.autoInviteEnabled,
                v -> {
                    cfg.autoInviteEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(invite, "Stats", "Auto Invite",
                Component.literal("Automatically invites players who meets requirements "
                                + "and replies No to those who do not")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.autoInviteRespond,
                v -> {
                    cfg.autoInviteRespond = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.autoInviteEnabled));

        settings.add(new ToggleSetting(invite, "Stats", "Anyone While Queued",
                Component.literal("While you are party leader and queued in Party Finder, "
                                + "anyone who messages you counts, not just \"lf inv\"")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.autoInviteWhileQueued,
                v -> {
                    cfg.autoInviteWhileQueued = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.autoInviteEnabled));

        settings.add(new NumberSetting(invite, "Requirements", "Cata Level",
                Component.literal("Catacomb level requirement. 0 = Any Cata Level")
                        .withStyle(ChatFormatting.GRAY),
                List.of(new NumberSetting.Field("", 60, 2)),
                "",
                () -> new int[]{(int) Math.round(cfg.autoInviteCataReq)},
                v -> cfg.autoInviteCataReq = v[0])
                .visibleWhen(() -> cfg.autoInviteEnabled));

        settings.add(new DropdownSetting(invite, "Requirements", "Floor",
                Component.literal("Which floor the best time is checked on")
                        .withStyle(ChatFormatting.GRAY),
                () -> DungeonFloor.NUMBERS,
                () -> String.valueOf(DungeonFloor.number(cfg.autoInviteFloor)),
                v -> {
                    boolean master = DungeonFloor.master(cfg.autoInviteFloor);
                    cfg.autoInviteFloor = DungeonFloor.key(NumberSetting.parse(v), master);
                    ConfigManager.save();
                },

                n -> (DungeonFloor.master(cfg.autoInviteFloor) ? "M" : "F") + n,
                null,
                "M7",
                70)
                .visibleWhen(() -> cfg.autoInviteEnabled));

        settings.add(new ToggleSetting(invite, "Requirements", "Master Mode",
                Component.literal("Switches the floors between Catacombs and Master Mode")
                        .withStyle(ChatFormatting.GRAY),
                () -> DungeonFloor.master(cfg.autoInviteFloor),
                v -> {
                    cfg.autoInviteFloor =
                            DungeonFloor.key(DungeonFloor.number(cfg.autoInviteFloor), v);
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.autoInviteEnabled));

        settings.add(new NumberSetting(invite, "Requirements", "Best Time",
                Component.literal("Slowest S+ time you will accept on that floor - 0 ignores it")
                        .withStyle(ChatFormatting.GRAY),
                List.of(new NumberSetting.Field("min", 59, 2),
                        new NumberSetting.Field("sec", 59, 2)),
                "",
                () -> {
                    int total = (int) Math.round(cfg.autoInvitePbSeconds);
                    return new int[]{total / 60, total % 60};
                },
                v -> cfg.autoInvitePbSeconds = (v[0] * 60) + v[1])
                .visibleWhen(() -> cfg.autoInviteEnabled));

        String notify = "Notifications";

        settings.add(new ToggleSetting(notify, "Dungeon Runs", "Dungeon Only Notifications",
                Component.literal("Only displays notifications while inside a Dungeon / Kuudra run")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.dungeonOnlyNotifications,
                v -> {
                    cfg.dungeonOnlyNotifications = v;
                    ConfigManager.save();
                }));

        settings.add(new DropdownSetting(notify, "Dungeon Runs", "Show During Runs",
                Component.literal("Only displays selected notifications during a Dungeon / Kuudra run")
                        .withStyle(ChatFormatting.GRAY),
                () -> List.of(NotificationGate.SCOPE_BOTH,
                        NotificationGate.SCOPE_MESSAGES,
                        NotificationGate.SCOPE_PARTY),
                () -> cfg.dungeonOnlyScope,
                v -> {
                    cfg.dungeonOnlyScope = v;
                    ConfigManager.save();
                },
                SettingsRegistry::dungeonScopeLabel,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.dungeonOnlyNotifications));

        settings.add(new ToggleSetting(notify, "Dungeon Score", "Score Display",
                Component.literal("Shows the run's live score on screen. It counts deaths (the first one costs 1 if "
                                + "that player has a Legendary Spirit pet), puzzles, rooms, secrets, time, crypts, the "
                                + "Mimic, the Prince and Bat bonus score and Paul. Move it in ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Edit GUI").withStyle(ChatFormatting.LIGHT_PURPLE)),
                () -> cfg.scoreHudEnabled,
                v -> {
                    cfg.scoreHudEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(notify, "Dungeon Score", "Score Breakdown",
                Component.literal("Adds Skill, Explore, Speed and Bonus under the score, plus deaths, crypts, "
                                + "puzzles, Mimic, Prince and bats").withStyle(ChatFormatting.GRAY),
                () -> cfg.scoreHudBreakdown,
                v -> {
                    cfg.scoreHudBreakdown = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.scoreHudEnabled));

        settings.add(new ToggleSetting(notify, "Dungeon Score", "300 Score Alert",
                Component.literal("Shows a title and plays a sound once the run reaches 300 score")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.score300Alert,
                v -> {
                    cfg.score300Alert = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(notify, "Dungeon Score", "Tell Party",
                Component.literal("Also says ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("300 Score!").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" in party chat, unless a teammate already said it")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.score300Party,
                v -> {
                    cfg.score300Party = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.score300Alert));

        settings.add(new ToggleSetting(notify, "Dungeon Score", "Announce Bonus Kills",
                Component.literal("Says Mimic Killed, Prince Killed or Bat Killed in party chat when you get one, "
                                + "so your teammates' score counters include it")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.scoreBonusAnnounce,
                v -> {
                    cfg.scoreBonusAnnounce = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(notify, "Dungeon Score", "Debug Log",
                Component.literal("Writes the score and everything it's worked out from every second, next to "
                                + "NoammAddons' score if you have it, to ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("config/qza/debug").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(", so a score that's off can be tracked down")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.scoreDebugLog,
                v -> {
                    cfg.scoreDebugLog = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.scoreHudEnabled || cfg.score300Alert));

        settings.add(new ToggleSetting(notify, "Party", "Party Invite Alert",
                Component.literal("Displays party invite notification on screen")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.partyInviteNotifyEnabled,
                v -> {
                    cfg.partyInviteNotifyEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new SliderSetting(notify, "Party", "Notification Duration",
                Component.literal("Displays duration of notification for selected amount of time")
                        .withStyle(ChatFormatting.GRAY),
                PartyNotification.MIN_DURATION, PartyNotification.MAX_DURATION, 1, "s",
                () -> cfg.partyNotifyDuration,
                v -> {
                    cfg.partyNotifyDuration = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.partyInviteNotifyEnabled));

        settings.add(new ToggleSetting(notify, "Leap Notifications", "Leap Notifications",
                Component.literal("Shows <ign> Leaped to You! on screen when someone leaps to you, or says "
                                + "your name in chat during a run. Drag it in ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Edit GUI").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(".").withStyle(ChatFormatting.GRAY)),
                () -> cfg.leapNotifyEnabled,
                v -> {
                    cfg.leapNotifyEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(notify, "Leap Notifications", "Show Class",
                Component.literal("Shows the player's class instead of their IGN, like ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Bers").withStyle(ChatFormatting.DARK_RED))
                        .append(Component.literal(" Leaped to You!").withStyle(ChatFormatting.GRAY)),
                () -> cfg.leapNotifyClass,
                v -> {
                    cfg.leapNotifyClass = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.leapNotifyEnabled));

        settings.add(new ToggleSetting(notify, "Leap Notifications", "Boss Only",
                Component.literal("Only shows leap notifications in boss, after you go through the portal")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.leapNotifyBossOnly,
                v -> {
                    cfg.leapNotifyBossOnly = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.leapNotifyEnabled));

        settings.add(new SliderSetting(notify, "Leap Notifications", "Notification Duration",
                Component.literal("How long the leap notification stays on screen")
                        .withStyle(ChatFormatting.GRAY),
                LeapNotification.MIN_DURATION, LeapNotification.MAX_DURATION, 0.5, "s",
                () -> cfg.leapNotifyDuration,
                v -> {
                    cfg.leapNotifyDuration = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.leapNotifyEnabled));

        settings.add(new ToggleSetting(notify, "QZA Chat", "Message Alert",
                Component.literal("Pops a notification showing what someone whispered you.")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.chatNotifyEnabled,
                v -> {
                    cfg.chatNotifyEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new DropdownSetting(notify, "QZA Chat", "Alert Mode",
                Component.literal("Ringer plays a sound and displays notification, Silent only displays without sound, and Do Not Disturb silences and hides it")
                        .withStyle(ChatFormatting.GRAY),
                () -> List.of(ChatNotification.MODE_RINGER,
                        ChatNotification.MODE_SILENT,
                        ChatNotification.MODE_DND),
                () -> cfg.chatNotifyMode,
                v -> {
                    cfg.chatNotifyMode = v;
                    ConfigManager.save();
                },
                SettingsRegistry::alertModeLabel,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.chatNotifyEnabled));

        settings.add(new SliderSetting(notify, "QZA Chat", "Notification Duration",
                Component.literal("Displays duration of notification for selected amount of time")
                        .withStyle(ChatFormatting.GRAY),
                ChatNotification.MIN_DURATION, ChatNotification.MAX_DURATION, 1, "s",
                () -> cfg.chatNotifyDuration,
                v -> {
                    cfg.chatNotifyDuration = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.chatNotifyEnabled));

        settings.add(new ToggleSetting(notify, "Discord", "Party Full Alert",
                Component.literal("Pings you on Discord when your party hits 5/5, as long as you "
                                + "are leading it or tabbed out of the game. Link your Discord "
                                + "below first.").withStyle(ChatFormatting.GRAY),
                () -> cfg.discordAlertEnabled,
                v -> {
                    cfg.discordAlertEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(notify, "Discord", "Always Alert",
                Component.literal("Alerts every time your party hits 5/5. Off, it only "
                                + "alerts when you are leading the party or tabbed out "
                                + "of the game.").withStyle(ChatFormatting.GRAY),
                () -> cfg.discordAlertAlways,
                v -> {
                    cfg.discordAlertAlways = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.discordAlertEnabled));

        settings.add(new ActionSetting(notify, "Discord", "Link Discord",
                Component.literal("Gives you a code to run as ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("/link <code>").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" in the QZA Discord. Unlinking deletes "
                                + "everything stored about you.").withStyle(ChatFormatting.GRAY)),
                () -> DiscordAlert.linked() ? "Unlink" : "Link",
                () -> {
                    Mc.setScreen(null);
                    if (DiscordAlert.linked()) {
                        DiscordAlert.unlink(ChatUtil::success, ChatUtil::error);
                    } else {
                        ChatUtil.info("Asking for a code...");
                        DiscordAlert.link(DiscordAlert::announceCode, ChatUtil::error);
                    }
                })
                .visibleWhen(() -> cfg.discordAlertEnabled));

        settings.add(new ToggleSetting(notify, "Discord", "Direct Message",
                Component.literal("The bot messages you directly").withStyle(ChatFormatting.GRAY),
                () -> cfg.discordAlertDm,
                v -> {
                    cfg.discordAlertDm = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.discordAlertEnabled));

        settings.add(new ToggleSetting(notify, "Discord", "Channel Ping",
                Component.literal("The bot @ mentions you in party-full-ping channel")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.discordAlertChannel,
                v -> {
                    cfg.discordAlertChannel = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.discordAlertEnabled));

        for (QZAAddon addon : ADDONS) {
            addon.addSettings(settings);
        }

        String misc = "Miscellaneous";
        String inventory = "Inventory";

        settings.add(new ToggleSetting(inventory, "Item List", "Item List",
                Component.literal("Shows every SkyBlock item on the right of your inventory. Click an item to see its recipe, right-click to see its uses")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.itemListEnabled,
                v -> {
                    cfg.itemListEnabled = v;
                    ConfigManager.save();
                    if (v) {
                        ItemRepo.ensureLoaded();
                    }
                }));

        settings.add(new ToggleSetting(inventory, "Item List", "Show in Inventory",
                Component.literal("Shows the item list in your normal inventory too, not just in menus")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.itemListInventory,
                v -> {
                    cfg.itemListInventory = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemListEnabled));

        settings.add(new SliderSetting(misc, "Interface", "GUI Scale",
                Component.literal("Size of GUI Scale (")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("100%").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" is default)").withStyle(ChatFormatting.GRAY)),
                50, 150, 5, "%",
                () -> cfg.guiScale,
                v -> {
                    cfg.guiScale = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(misc, "Open Where Last Closed", "Open Where Last Closed",
                Component.literal("Makes ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("/qza").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" open on the tab, scroll spot, open cards and search you "
                                + "closed it on, even after restarting Minecraft. When off, it opens on the first "
                                + "tab").withStyle(ChatFormatting.GRAY)),
                () -> cfg.guiReopenLast,
                v -> {
                    cfg.guiReopenLast = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(misc, "Clock", "Clock",
                Component.literal("Shows the current time on screen. Drag it in ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Edit GUI")
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(".").withStyle(ChatFormatting.GRAY)),
                () -> cfg.clockEnabled,
                v -> {
                    cfg.clockEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new DropdownSetting(misc, "Clock", "Clock Format",
                Component.literal("12 hour shows ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("AM").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" or ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("PM").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" after the time, 24 hour does not.")
                                .withStyle(ChatFormatting.GRAY)),
                () -> List.of(ClockDisplay.TWELVE_HOUR, ClockDisplay.TWENTY_FOUR_HOUR),
                () -> cfg.clockFormat,
                v -> {
                    cfg.clockFormat = v;
                    ConfigManager.save();
                },
                ClockDisplay::label,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.clockEnabled));

        settings.add(new ToggleSetting(inventory, "Hotbar Scroll Lock", "Hotbar Scroll Lock",
                Component.literal("Stops the hotbar jumping from the first slot to the last when you "
                        + "scroll past it, and back").withStyle(ChatFormatting.GRAY),
                () -> cfg.hotbarScrollLock,
                v -> {
                    cfg.hotbarScrollLock = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(inventory, "Tooltip Scale", "Tooltip Scale",
                Component.literal("Changes the size of item tooltips").withStyle(ChatFormatting.GRAY),
                () -> cfg.tooltipScaleEnabled,
                v -> {
                    cfg.tooltipScaleEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new DropdownSetting(inventory, "Tooltip Scale", "Scale Mode",
                Component.literal("Dynamic shrinks tooltips that are too big so they always fit on "
                        + "screen. Custom scales every tooltip by Custom Scale.").withStyle(ChatFormatting.GRAY),
                () -> TooltipScale.MODES,
                () -> cfg.tooltipScaleMode,
                v -> {
                    cfg.tooltipScaleMode = v;
                    ConfigManager.save();
                },
                TooltipScale::label,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.tooltipScaleEnabled));

        settings.add(new SliderSetting(inventory, "Tooltip Scale", "Custom Scale",
                Component.literal("How big tooltips are in Custom mode (")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("1.00x").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" is normal)").withStyle(ChatFormatting.GRAY)),
                TooltipScale.MIN_SCALE, TooltipScale.MAX_SCALE, 0.05, "x",
                () -> cfg.tooltipScale,
                v -> {
                    cfg.tooltipScale = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.tooltipScaleEnabled && TooltipScale.CUSTOM.equals(cfg.tooltipScaleMode)));

        settings.add(new ToggleSetting(inventory, "Item Prices", "Item Prices",
                Component.literal("Adds Bazaar, Lowest BIN, average BIN and estimated value lines to SkyBlock "
                        + "item tooltips").withStyle(ChatFormatting.GRAY),
                () -> cfg.itemPricesEnabled,
                v -> {
                    cfg.itemPricesEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(inventory, "Item Prices", "Bazaar Price",
                Component.literal("Instant buy and instant sell price for Bazaar items").withStyle(ChatFormatting.GRAY),
                () -> cfg.itemPriceBazaar,
                v -> {
                    cfg.itemPriceBazaar = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemPricesEnabled));

        settings.add(new ToggleSetting(inventory, "Item Prices", "Lowest BIN",
                Component.literal("Cheapest Buy It Now auction for the item right now").withStyle(ChatFormatting.GRAY),
                () -> cfg.itemPriceLowestBin,
                v -> {
                    cfg.itemPriceLowestBin = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemPricesEnabled));

        settings.add(new DropdownSetting(inventory, "Item Prices", "Average BIN",
                Component.literal("Average Lowest BIN over the last day, the last 3 days or both")
                        .withStyle(ChatFormatting.GRAY),
                () -> PriceTooltip.AVERAGES,
                () -> cfg.itemPriceAverage,
                v -> {
                    cfg.itemPriceAverage = v;
                    ConfigManager.save();
                },
                PriceTooltip::averageLabel,
                null,
                "(none)",
                100)
                .visibleWhen(() -> cfg.itemPricesEnabled));

        settings.add(new ToggleSetting(inventory, "Item Prices", "Estimated Item Value",
                Component.literal("What the item is worth with its enchants, stars, gems, reforge, recomb, books "
                        + "and other upgrades added. Pets are valued by level, held item and skin.")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.itemPriceValue,
                v -> {
                    cfg.itemPriceValue = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemPricesEnabled));

        settings.add(new ToggleSetting(misc, "Command Keybinds", "Command Keybinds",
                Component.literal("Keybinds that run a command or send a chat message when you press them")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.commandKeybindsEnabled,
                v -> {
                    cfg.commandKeybindsEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ActionSetting(misc, "Command Keybinds", "Edit Keybinds",
                Component.literal("Add, change or remove your command keybinds").withStyle(ChatFormatting.GRAY),
                () -> {
                    int count = cfg.commandBinds.size();
                    return count > 0 ? "Edit (" + count + ")" : "Edit";
                },
                () -> Mc.setScreen(new CommandKeybindScreen()))
                .visibleWhen(() -> cfg.commandKeybindsEnabled));

        settings.add(new ToggleSetting(misc, "Command Keybinds", "Work In All Menus",
                Component.literal("Lets every keybind work while a menu like your inventory or a chest is "
                        + "open, not just the ones with Menus ticked").withStyle(ChatFormatting.GRAY),
                () -> cfg.commandKeybindsInMenus,
                v -> {
                    cfg.commandKeybindsInMenus = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.commandKeybindsEnabled));

        settings.add(new ToggleSetting(misc, "Player Size", "Player Size",
                Component.literal("Changes the size of your own player. A negative Y flips you upside down")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.playerSizeEnabled,
                v -> {
                    cfg.playerSizeEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(playerSizeSetting(misc, "Size X", "How wide you are",
                () -> cfg.playerSizeX, v -> cfg.playerSizeX = v)
                .visibleWhen(() -> cfg.playerSizeEnabled));

        settings.add(playerSizeSetting(misc, "Size Y", "How tall you are",
                () -> cfg.playerSizeY, v -> cfg.playerSizeY = v)
                .visibleWhen(() -> cfg.playerSizeEnabled));

        settings.add(playerSizeSetting(misc, "Size Z", "How thick you are",
                () -> cfg.playerSizeZ, v -> cfg.playerSizeZ = v)
                .visibleWhen(() -> cfg.playerSizeEnabled));

        settings.add(new ToggleSetting(inventory, "Equipment in Inventory", "Equipment in Inventory",
                Component.literal("Shows your necklace, cloak, belt and gloves to the right of your player in your "
                                + "inventory, where the shield slot was. Open ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("/equipment").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" once so QZA can see them, and click one to open it")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.equipmentInInventory,
                v -> {
                    cfg.equipmentInInventory = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(inventory, "Equipment in Inventory", "Rarity Backgrounds",
                Component.literal("Colours the square behind each piece of equipment by its rarity, like ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Legendary").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(" or ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Mythic").withStyle(ChatFormatting.LIGHT_PURPLE)),
                () -> cfg.equipmentRarity,
                v -> {
                    cfg.equipmentRarity = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.equipmentInInventory));

        settings.add(new ToggleSetting(inventory, "Storage Preview", "Storage Preview",
                Component.literal("In ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("/storage").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(", hovering an Ender Chest page or backpack shows what is inside. "
                                + "Open each one once so QZA can see it. Hold Shift for the normal tooltip")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.storagePreview,
                v -> {
                    cfg.storagePreview = v;
                    ConfigManager.save();
                })
                .visibleWhen(StoragePreview::available));

        settings.add(new ToggleSetting(misc, "Music Player Overlay", "Music Player Overlay",
                Component.literal("Shows the songs playing on your computer and displays Cover, Title, Artist, "
                                + "and length. Open inventory to pause, skip, restart, or click bar to jump to a "
                                + "certain time. Move it in ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Edit GUI").withStyle(ChatFormatting.LIGHT_PURPLE)),
                () -> cfg.musicOverlayEnabled,
                v -> {
                    cfg.musicOverlayEnabled = v;
                    ConfigManager.save();
                })
                .visibleWhen(MusicOverlay::available));

        settings.add(new ToggleSetting(misc, "Music Player Overlay", "Show Cover",
                Component.literal("Shows the song's cover").withStyle(ChatFormatting.GRAY),
                () -> cfg.musicOverlayCover,
                v -> {
                    cfg.musicOverlayCover = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> MusicOverlay.available() && cfg.musicOverlayEnabled));

        settings.add(new ToggleSetting(misc, "Music Player Overlay", "Hide Background",
                Component.literal("Hides the background of the overlay").withStyle(ChatFormatting.GRAY),
                () -> cfg.musicOverlayHideBackground,
                v -> {
                    cfg.musicOverlayHideBackground = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> MusicOverlay.available() && cfg.musicOverlayEnabled));

        settings.add(new ToggleSetting(misc, "Item Glow", "Item Glow",
                Component.literal("Makes the item in your hand glow.").withStyle(ChatFormatting.GRAY),
                () -> cfg.itemGlowEnabled,
                v -> {
                    cfg.itemGlowEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new DropdownSetting(misc, "Item Glow", "Glow Style",
                Component.literal("Aura").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(" is a soft glow that breathes. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Shimmer").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" sweeps a shine across the item with sparkles. ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Flame").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" makes flames and embers rise off the item")
                                .withStyle(ChatFormatting.GRAY)),
                () -> ItemGlow.STYLES,
                () -> cfg.itemGlowStyle,
                v -> {
                    cfg.itemGlowStyle = v;
                    ConfigManager.save();
                },
                ItemGlow::styleLabel,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.itemGlowEnabled));

        settings.add(new DropdownSetting(misc, "Item Glow", "Glow Colour",
                Component.literal("Colour of the glow. ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Rainbow").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" cycles through every colour. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Rarity").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" matches the item's SkyBlock rarity, like gold for Legendary")
                                .withStyle(ChatFormatting.GRAY)),
                ItemGlow::colours,
                () -> cfg.itemGlowColour,
                v -> {
                    cfg.itemGlowColour = v;
                    ConfigManager.save();
                },
                SettingsRegistry::colourLabel,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.itemGlowEnabled));

        settings.add(new ToggleSetting(misc, "Item Glow", "Static Glow",
                Component.literal("Keeps the glow still instead of moving").withStyle(ChatFormatting.GRAY),
                () -> cfg.itemGlowStatic,
                v -> {
                    cfg.itemGlowStatic = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemGlowEnabled));

        settings.add(new SliderSetting(misc, "Item Glow", "Glow Speed",
                Component.literal("How fast the glow moves (").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("1.00x").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" is normal)").withStyle(ChatFormatting.GRAY)),
                ItemGlow.MIN_SPEED, ItemGlow.MAX_SPEED, 0.05, "x",
                () -> cfg.itemGlowSpeed,
                v -> {
                    cfg.itemGlowSpeed = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemGlowEnabled && !cfg.itemGlowStatic));

        settings.add(new SliderSetting(misc, "Item Glow", "Glow Brightness",
                Component.literal("How bright the glow is").withStyle(ChatFormatting.GRAY),
                ItemGlow.MIN_BRIGHTNESS, ItemGlow.MAX_BRIGHTNESS, 5, "%",
                () -> cfg.itemGlowBrightness,
                v -> {
                    cfg.itemGlowBrightness = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemGlowEnabled));

        settings.add(new ToggleSetting(misc, "Item Glow", "Glow Offhand",
                Component.literal("Also makes the item in your offhand glow").withStyle(ChatFormatting.GRAY),
                () -> cfg.itemGlowOffhand,
                v -> {
                    cfg.itemGlowOffhand = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemGlowEnabled));

        settings.add(new ToggleSetting(inventory, "Hide Shield Slot", "Hide Shield Slot",
                Component.literal("Removes the shield slot from your inventory on SkyBlock, where it does nothing")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.hideShieldSlot,
                v -> {
                    cfg.hideShieldSlot = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(inventory, "Item Protection", "Item Protection",
                Component.literal("Stops the items you protect from being dropped, sold to NPCs, salvaged, traded, "
                                + "auctioned or put in item frames, and marks them with a pink star. Press the ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Protect Key").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" on an item or use ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("/qza protect").withStyle(ChatFormatting.LIGHT_PURPLE)),
                () -> cfg.itemProtectionEnabled,
                v -> {
                    cfg.itemProtectionEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ActionSetting(inventory, "Item Protection", "Protect Key",
                Component.literal("Protects or unprotects the item you're holding, or the one you hover in your "
                                + "inventory or storage. ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Delete").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" clears it and ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("esc").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" cancels").withStyle(ChatFormatting.GRAY)),
                () -> ItemProtection.capturing() ? "Press a key..." : ItemProtection.keyLabel(),
                ItemProtection::startCapture)
                .visibleWhen(() -> cfg.itemProtectionEnabled));

        settings.add(new ToggleSetting(inventory, "Item Protection", "Show Star",
                Component.literal("Marks protected items with a pink star. Turn it off to hide the stars, and "
                        + "your items stay protected").withStyle(ChatFormatting.GRAY),
                () -> cfg.itemProtectionStar,
                v -> {
                    cfg.itemProtectionStar = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemProtectionEnabled));

        settings.add(new DropdownSetting(inventory, "Item Protection", "Star Corner",
                Component.literal("Which corner of the item the star sits in").withStyle(ChatFormatting.GRAY),
                () -> ItemProtection.CORNERS,
                () -> cfg.itemProtectionCorner,
                v -> {
                    cfg.itemProtectionCorner = v;
                    ConfigManager.save();
                },
                ItemProtection::cornerLabel,
                null,
                "Top Right",
                100)
                .visibleWhen(() -> cfg.itemProtectionEnabled && cfg.itemProtectionStar));

        settings.add(new ToggleSetting(inventory, "Item Protection", "Chat Messages",
                Component.literal("Says in chat when you protect or unprotect an item, and when a protected item "
                        + "is stopped").withStyle(ChatFormatting.GRAY),
                () -> cfg.itemProtectionMessages,
                v -> {
                    cfg.itemProtectionMessages = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemProtectionEnabled));

        settings.add(new ToggleSetting(misc, "Sword Blocking", "Sword Blocking",
                Component.literal("Brings back the 1.8 sword blocking animation. Hold right-click with a sword "
                        + "to block. Only items that are swords in vanilla block, so wands and staffs like the Ice "
                        + "Spray Wand don't").withStyle(ChatFormatting.GRAY),
                () -> cfg.swordBlockEnabled,
                v -> {
                    cfg.swordBlockEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(misc, "Sword Blocking", "Third Person",
                Component.literal("Also raises your arm to block in third person").withStyle(ChatFormatting.GRAY),
                () -> cfg.swordBlockThirdPerson,
                v -> {
                    cfg.swordBlockThirdPerson = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.swordBlockEnabled));

        settings.add(new ToggleSetting(misc, "Sword Blocking", "Other Players",
                Component.literal("Shows other players blocking when they hold right-click with a sword, like "
                        + "players on 1.8").withStyle(ChatFormatting.GRAY),
                () -> cfg.swordBlockOthers,
                v -> {
                    cfg.swordBlockOthers = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.swordBlockEnabled));

        settings.add(new ToggleSetting(misc, "Inspect Item", "Inspect Item",
                Component.literal("Press the ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Inspect Key").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" to show off the item in your hand. Swords, pickaxes, bows "
                                + "and every other item each have 3 animations to pick from")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.itemInspectEnabled,
                v -> {
                    cfg.itemInspectEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ActionSetting(misc, "Inspect Item", "Inspect Key",
                Component.literal("The key that starts the animation. ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Delete").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" clears it and ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("esc").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" cancels").withStyle(ChatFormatting.GRAY)),
                () -> ItemInspect.capturing() ? "Press a key..." : ItemInspect.keyLabel(),
                ItemInspect::startCapture)
                .visibleWhen(() -> cfg.itemInspectEnabled));

        settings.add(new DropdownSetting(misc, "Inspect Item", "Sword Animation",
                Component.literal("Animation 1").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(" twirls the sword around your fingers. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Animation 2").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" raises it in a knight's salute and sweeps it down. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Animation 3").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" flips it into the air spinning and catches it").withStyle(ChatFormatting.GRAY)),
                () -> ItemInspect.ANIMATIONS,
                () -> cfg.itemInspectSword,
                v -> {
                    cfg.itemInspectSword = v;
                    ConfigManager.save();
                },
                ItemInspect::animationLabel,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.itemInspectEnabled));

        settings.add(new DropdownSetting(misc, "Inspect Item", "Pickaxe Animation",
                Component.literal("Animation 1").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(" turns it over, then tosses and catches it. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Animation 2").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" balances it on your fist and spins it like a top. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Animation 3").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" points it forward and revs it like a drill").withStyle(ChatFormatting.GRAY)),
                () -> ItemInspect.ANIMATIONS,
                () -> cfg.itemInspectPickaxe,
                v -> {
                    cfg.itemInspectPickaxe = v;
                    ConfigManager.save();
                },
                ItemInspect::animationLabel,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.itemInspectEnabled));

        settings.add(new DropdownSetting(misc, "Inspect Item", "Bow Animation",
                Component.literal("Animation 1").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(" plucks the string and spins the bow around its length. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Animation 2").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" throws it like a boomerang that loops out and flies back to you. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Animation 3").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" slides the bow onto your wrist and spins it around your arm").withStyle(ChatFormatting.GRAY)),
                () -> ItemInspect.ANIMATIONS,
                () -> cfg.itemInspectBow,
                v -> {
                    cfg.itemInspectBow = v;
                    ConfigManager.save();
                },
                ItemInspect::animationLabel,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.itemInspectEnabled));

        settings.add(new DropdownSetting(misc, "Inspect Item", "Other Items",
                Component.literal("Animation 1").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(" brings it closer and turns it around. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Animation 2").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" juggles it twice. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Animation 3").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" floats it above your hand, spinning like a dropped item").withStyle(ChatFormatting.GRAY)),
                () -> ItemInspect.ANIMATIONS,
                () -> cfg.itemInspectOther,
                v -> {
                    cfg.itemInspectOther = v;
                    ConfigManager.save();
                },
                ItemInspect::animationLabel,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.itemInspectEnabled));

        settings.add(new DropdownSetting(misc, "Inspect Item", "Karambit Animation",
                Component.literal("Animation 1").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(" is the CS:GO inspect, spinning it around your finger by the ring. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Animation 2").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" is the Valorant inspect, spinning it around your finger in a reverse grip. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Animation 3").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" spins it both ways and tosses it up").withStyle(ChatFormatting.GRAY)),
                () -> ItemInspect.ANIMATIONS,
                () -> cfg.itemInspectKarambit,
                v -> {
                    cfg.itemInspectKarambit = v;
                    ConfigManager.save();
                },
                ItemInspect::animationLabel,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.itemInspectEnabled));

        settings.add(new DropdownSetting(misc, "Inspect Item", "Butterfly Knife Animation",
                Component.literal("Animation 1").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(" is the CS:GO inspect, flipping it shut and open and spinning it. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Animation 2").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" is the Valorant inspect, fanning it open into a spinning star. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Animation 3").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" tosses it up with the handles flapping and snaps it open").withStyle(ChatFormatting.GRAY)),
                () -> ItemInspect.ANIMATIONS,
                () -> cfg.itemInspectButterfly,
                v -> {
                    cfg.itemInspectButterfly = v;
                    ConfigManager.save();
                },
                ItemInspect::animationLabel,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.itemInspectEnabled));

        settings.add(new ToggleSetting(misc, "Inspect Item", "Show Hand",
                Component.literal("Shows your arm holding the item during the animation. Turn it off to hide it")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.itemInspectShowHand,
                v -> {
                    cfg.itemInspectShowHand = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemInspectEnabled));

        settings.add(new ToggleSetting(misc, "Inspect Item", "Keep Playing While Swinging",
                Component.literal("Left clicking no longer stops the inspect or equip animation, so you can "
                                + "swing or mine while it plays. Right clicking still stops it")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.itemInspectKeepOnSwing,
                v -> {
                    cfg.itemInspectKeepOnSwing = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemInspectEnabled));

        settings.add(new SliderSetting(misc, "Inspect Item", "Inspect Speed",
                Component.literal("How fast the animation plays (").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("1.00x").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" is normal)").withStyle(ChatFormatting.GRAY)),
                ItemInspect.MIN_SPEED, ItemInspect.MAX_SPEED, 0.05, "x",
                () -> cfg.itemInspectSpeed,
                v -> {
                    cfg.itemInspectSpeed = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemInspectEnabled));

        settings.add(new ToggleSetting(misc, "No Loading Screen", "No Loading Screen",
                Component.literal("Skips the loading terrain screen when you switch islands or servers, so you see "
                        + "the world while it loads in").withStyle(ChatFormatting.GRAY),
                () -> cfg.noLoadingScreen,
                v -> {
                    cfg.noLoadingScreen = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(misc, "No Loading Screen", "Servers Only",
                Component.literal("Only skips it on servers, not in singleplayer or LAN worlds")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.noLoadingScreenServersOnly,
                v -> {
                    cfg.noLoadingScreenServersOnly = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.noLoadingScreen));

        settings.add(new ToggleSetting(misc, "Full Bright", "Full Bright",
                Component.literal("Fully brightens everything around you").withStyle(ChatFormatting.GRAY),
                () -> cfg.fullBright,
                v -> {
                    cfg.fullBright = v;
                    ConfigManager.save();
                }));

        settings.add(new DropdownSetting(misc, "Full Bright", "Mode",
                Component.literal("Ambient Light lights every block fully. Brightness turns your brightness far "
                        + "past the max, which looks about the same. Night Vision looks like the Night Vision effect, "
                        + "a little dimmer, without giving it to you").withStyle(ChatFormatting.GRAY),
                () -> FullBright.MODES,
                () -> cfg.fullBrightMode,
                v -> {
                    cfg.fullBrightMode = v;
                    ConfigManager.save();
                },
                FullBright::modeLabel,
                null,
                "Ambient Light",
                100)
                .visibleWhen(() -> cfg.fullBright));

        settings.add(new ToggleSetting(misc, "Full Bright", "Hide Night Vision",
                Component.literal("Ignores a real Night Vision effect on you while Full Bright is on, so its "
                        + "brighter fog and the flicker when it runs out go away").withStyle(ChatFormatting.GRAY),
                () -> cfg.fullBrightHideNightVision,
                v -> {
                    cfg.fullBrightHideNightVision = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.fullBright));

        settings.add(new ToggleSetting(inventory, "Hide Crafting", "Hide Crafting",
                Component.literal("Removes the crafting grid, its arrow, the output slot and the Crafting text "
                        + "from your inventory on SkyBlock").withStyle(ChatFormatting.GRAY),
                () -> cfg.hideCrafting,
                v -> {
                    cfg.hideCrafting = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(inventory, "Item Star Count", "Item Star Count",
                Component.literal("Shows how many stars an item has in the bottom right of it. ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("10").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" means 5 stars and the 5th master star")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.itemStarCount,
                v -> {
                    cfg.itemStarCount = v;
                    ConfigManager.save();
                }));

        settings.add(colourSetting(inventory, "Item Star Count", "Star Count Colour", "Colour of the star number",
                () -> cfg.itemStarColour, v -> cfg.itemStarColour = v)
                .visibleWhen(() -> cfg.itemStarCount));

        settings.add(new SliderSetting(inventory, "Item Star Count", "Star Count Size",
                Component.literal("Size of the star number (")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("100%").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" is default)").withStyle(ChatFormatting.GRAY)),
                ItemStars.MIN_SCALE, ItemStars.MAX_SCALE, 5, "%",
                () -> cfg.itemStarScale,
                v -> {
                    cfg.itemStarScale = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.itemStarCount));

        settings.add(new ToggleSetting(inventory, "Terminator Indicator", "Terminator Indicator",
                Component.literal("Shows which Terminator you are holding, your Precise one with Soul Eater or "
                                + "your Spiritual one with Duplex, so you swap to the right one as Berserk")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.terminatorIndicatorEnabled,
                v -> {
                    cfg.terminatorIndicatorEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new DropdownSetting(inventory, "Terminator Indicator", "Show As",
                Component.literal("GUI").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(" shows the Terminator's name on your screen while you hold it. ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Dot").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" puts a coloured dot on the item. ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Both").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" does both").withStyle(ChatFormatting.GRAY)),
                () -> TerminatorIndicator.SHOW,
                () -> cfg.terminatorIndicatorShow,
                v -> {
                    cfg.terminatorIndicatorShow = v;
                    ConfigManager.save();
                },
                TerminatorIndicator::showLabel,
                null,
                "Both",
                100)
                .visibleWhen(() -> cfg.terminatorIndicatorEnabled));

        settings.add(new ActionSetting(inventory, "Terminator Indicator", "Edit Terminators",
                Component.literal("Rename the Terminators shown on your screen and change their colour. If QZA "
                                + "can't tell them apart, pick which one is which here. Move it in ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Edit GUI").withStyle(ChatFormatting.LIGHT_PURPLE)),
                () -> "Edit",
                () -> Mc.setScreen(new TerminatorScreen()))
                .visibleWhen(() -> cfg.terminatorIndicatorEnabled));

        settings.add(new DropdownSetting(inventory, "Terminator Indicator", "Dot Corner",
                Component.literal("Which corner of the item the dot sits in").withStyle(ChatFormatting.GRAY),
                () -> ItemProtection.CORNERS,
                () -> cfg.terminatorDotCorner,
                v -> {
                    cfg.terminatorDotCorner = v;
                    ConfigManager.save();
                },
                ItemProtection::cornerLabel,
                null,
                "Top Left",
                100)
                .visibleWhen(() -> cfg.terminatorIndicatorEnabled && TerminatorIndicator.showsDot()));

        settings.add(colourSetting(inventory, "Terminator Indicator", "Soul Eater Dot",
                "Colour of the dot on your Soul Eater Terminator",
                () -> cfg.terminatorSoulEaterDot, v -> cfg.terminatorSoulEaterDot = v)
                .visibleWhen(() -> cfg.terminatorIndicatorEnabled && TerminatorIndicator.showsDot()));

        settings.add(colourSetting(inventory, "Terminator Indicator", "Duplex Dot",
                "Colour of the dot on your Duplex Terminator",
                () -> cfg.terminatorDuplexDot, v -> cfg.terminatorDuplexDot = v)
                .visibleWhen(() -> cfg.terminatorIndicatorEnabled && TerminatorIndicator.showsDot()));

        settings.add(new ToggleSetting(inventory, "Custom Items", "Custom Items",
                Component.literal("Change how your SkyBlock items look, like turning your Hyperion into a ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Karambit").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Butterfly Knife").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" or ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Lightsaber").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(". Only you see it, and the item works the same")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.customItemsEnabled,
                v -> {
                    cfg.customItemsEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ActionSetting(inventory, "Custom Items", "Edit Custom Items",
                Component.literal("Add your items, pick what each one turns into, and open each one's settings to "
                        + "change its look, equip animation, glow and sword blocking").withStyle(ChatFormatting.GRAY),
                () -> "Edit",
                () -> Mc.setScreen(new CustomItemScreen()))
                .visibleWhen(() -> cfg.customItemsEnabled));

        settings.add(new ToggleSetting(misc, "Command Shortcuts", "Command Shortcuts",
                Component.literal("Your own short commands that run longer ones, like ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("/d").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("/warp dungeons").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(". Anything typed after a shortcut is kept")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.commandShortcutsEnabled,
                v -> {
                    cfg.commandShortcutsEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ActionSetting(misc, "Command Shortcuts", "Edit Shortcuts",
                Component.literal("Add, change or remove your command shortcuts").withStyle(ChatFormatting.GRAY),
                () -> {
                    int count = CommandShortcuts.shortcuts().size();
                    return count > 0 ? "Edit (" + count + ")" : "Edit";
                },
                () -> Mc.setScreen(new CommandShortcutScreen()))
                .visibleWhen(() -> cfg.commandShortcutsEnabled));

        settings.add(new ToggleSetting(misc, "AH / Bazaar Search", "AH / Bazaar Search",
                Component.literal("")
                        .append(Component.literal("/ahs").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" and ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("/bzs").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" open a search box that suggests item names as you type, then "
                                        + "searches the auction house or bazaar for it")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.marketSearchCommands,
                v -> {
                    cfg.marketSearchCommands = v;
                    MarketSearch.preload();
                    ConfigManager.save();
                }));

        boolean[] confirmReset = {false};
        settings.add(new ActionSetting(misc, "Config", "Reset Settings",
                Component.literal("Restores all settings to default. ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Click twice to confirm")
                                .withStyle(ChatFormatting.RED)),
                () -> confirmReset[0] ? "Confirm?" : "Reset",
                () -> {
                    if (!confirmReset[0]) {
                        confirmReset[0] = true;
                        return;
                    }
                    confirmReset[0] = false;
                    ConfigManager.reset();
                    ADDONS.forEach(QZAAddon::resetSettings);
                    MusicManager.get().applySettings();
                    ChatUtil.success("Settings reset to defaults.");
                    Mc.setScreen(null);
                }));

        for (Setting setting : settings) {
            String card = CARDS.get(setting.category + "/" + setting.section);
            if (card != null) {
                setting.card(card);
            }
        }
        return settings;
    }

    private static List<String> trackNames() {
        List<String> names = new ArrayList<>();
        for (Path track : MusicLibrary.tracks()) {
            names.add(track.getFileName().toString());
        }
        return names;
    }

    private static String historyModeLabel(String raw) {
        return ChatHistory.MODE_SESSION.equals(raw) ? "Reset On Launch" : "Save Forever";
    }

    private static String dungeonScopeLabel(String raw) {
        if (NotificationGate.SCOPE_MESSAGES.equals(raw)) {
            return "Messages Only";
        }
        if (NotificationGate.SCOPE_PARTY.equals(raw)) {
            return "Party Invites Only";
        }
        return "Both";
    }

    private static String alertModeLabel(String raw) {
        if (ChatNotification.MODE_SILENT.equals(raw)) {
            return "Silent";
        }
        if (ChatNotification.MODE_DND.equals(raw)) {
            return "Do Not Disturb";
        }
        return "Ringer";
    }

    private static String announceModeLabel(String raw) {
        return "client".equals(raw) ? "Client Notification" : "Announce to Party";
    }

    private static SliderSetting playerSizeSetting(String category, String title, String description,
                                                   DoubleSupplier getter,
                                                   DoubleConsumer setter) {
        return new SliderSetting(category, "Player Size", title,
                Component.literal(description + " (").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("1.00").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" is normal)").withStyle(ChatFormatting.GRAY)),
                PlayerSize.MIN, PlayerSize.MAX, 0.05, "",
                getter,
                v -> {
                    setter.accept(v);
                    ConfigManager.save();
                });
    }

    private static DropdownSetting colourSetting(String category, String section, String title,
                                                 String description, Supplier<String> getter,
                                                 Consumer<String> setter) {
        return new DropdownSetting(category, section, title,
                Component.literal(description).withStyle(ChatFormatting.GRAY),
                WaypointColour::names,
                getter,
                v -> {
                    setter.accept(v);
                    ConfigManager.save();
                },
                SettingsRegistry::colourLabel,
                null,
                "(none)",
                170);
    }

    private static String colourLabel(String raw) {
        return raw == null || raw.isEmpty() ? "" : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

}
