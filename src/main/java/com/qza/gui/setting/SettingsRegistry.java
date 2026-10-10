package com.qza.gui.setting;

import com.qza.addon.QZAAddon;
import com.qza.chat.ChatFocus;
import com.qza.chat.ChatHistory;
import com.qza.chat.ChatKeybind;
import com.qza.chat.ChatNotification;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.discord.DiscordAlert;
import com.qza.dungeon.CrystalTimer;
import com.qza.dungeon.LeapNotification;
import com.qza.dungeon.NecronLeap;
import com.qza.dungeon.PYTimer;
import com.qza.dungeon.WitherKey;
import com.qza.gui.CommandKeybindScreen;
import com.qza.gui.CommandShortcutScreen;
import com.qza.gui.MusicNamesScreen;
import com.qza.gui.QZAChatScreen;
import com.qza.gui.WaypointScreen;
import com.qza.gui.setting.NumberSetting.Field;
import com.qza.itemlist.ItemRepo;
import com.qza.music.MusicAliases;
import com.qza.music.MusicLibrary;
import com.qza.music.MusicManager;
import com.qza.notify.NotificationGate;
import com.qza.party.PartyNotification;
import com.qza.search.MarketSearch;
import com.qza.shitter.ShitterListPage;
import com.qza.stats.DungeonFloor;
import com.qza.timer.ClockDisplay;
import com.qza.timer.NecronDebugTimer;
import com.qza.tweaks.CommandShortcuts;
import com.qza.tweaks.ItemStars;
import com.qza.tweaks.PlayerSize;
import com.qza.tweaks.TooltipScale;
import com.qza.util.ChatUtil;
import com.qza.waypoint.WaypointColour;
import com.qza.waypoint.WaypointEditor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
            "F7 / M7",
            "Chat",
            "Auto Check Stats",
            "Notifications",
            "Miscellaneous"));

    private static final Map<String, String> CARDS = Map.ofEntries(
            Map.entry("F7 / M7/Track Selection", "Terminal Music"),
            Map.entry("F7 / M7/Music Library", "Terminal Music"),
            Map.entry("F7 / M7/Music Playback", "Terminal Music"),
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

        String f7 = "F7 / M7";

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

        settings.add(new ToggleSetting(f7, "Necron Timer", "Necron Kill Time",
                Component.literal("Announces how long it took to kill Necron before phase is over")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.necronTimerEnabled,
                v -> {
                    cfg.necronTimerEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new DropdownSetting(f7, "Necron Timer", "Announce Mode",
                Component.literal("Send the kill time to the whole party, or only to yourself.")
                        .withStyle(ChatFormatting.GRAY),
                () -> List.of("party", "client"),
                () -> cfg.necronAnnounceMode,
                v -> {
                    cfg.necronAnnounceMode = v;
                    ConfigManager.save();
                },
                SettingsRegistry::announceModeLabel,
                null,
                "(none)",
                170)
                .visibleWhen(() -> cfg.necronTimerEnabled));

        settings.add(new ToggleSetting(f7, "Necron Timer", "Debug Timer",
                Component.literal("Tells only you, in tick time, when Necron's health bar hits ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("5%").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" and ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("0").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(", and when the Wither King's bar fills to ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("100").withStyle(ChatFormatting.WHITE)),
                () -> cfg.necronDebugTimer,
                v -> {
                    cfg.necronDebugTimer = v;
                    NecronDebugTimer.reset();
                    ConfigManager.save();
                }));

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

        settings.add(new ToggleSetting(f7, "PY Timer", "PY Timer",
                Component.literal("Updated Timer for PY in Storm Phase")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.pyTimerEnabled,
                v -> {
                    cfg.pyTimerEnabled = v;
                    PYTimer.reset();
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(f7, "Crystal Spawn Timer", "Crystal Spawn Timer",
                Component.literal("Shows ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Crystal Spawned").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" when Maxor starts. Once the Energy Laser charges up it shows ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Crystal Spawning in").withStyle(ChatFormatting.RED))
                        .append(Component.literal(", counting down in tick time from the laser hitting Maxor to "
                                        + "the second crystals. Drag it in ")
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

        settings.add(new ToggleSetting(chat, "QZA Chat", "Hide Vanilla Chat",
                Component.literal("Hides Minecraft's chat in the corner while QZA Chat is open")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.hideVanillaChat,
                v -> {
                    cfg.hideVanillaChat = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.qzaChatEnabled));

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

        settings.add(new ToggleSetting(misc, "Item List", "Item List",
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

        settings.add(new ToggleSetting(misc, "Item List", "Show in Inventory",
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

        settings.add(new ToggleSetting(misc, "Hotbar Scroll Lock", "Hotbar Scroll Lock",
                Component.literal("Stops the hotbar jumping from the first slot to the last when you "
                        + "scroll past it, and back").withStyle(ChatFormatting.GRAY),
                () -> cfg.hotbarScrollLock,
                v -> {
                    cfg.hotbarScrollLock = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(misc, "Tooltip Scale", "Tooltip Scale",
                Component.literal("Changes the size of item tooltips").withStyle(ChatFormatting.GRAY),
                () -> cfg.tooltipScaleEnabled,
                v -> {
                    cfg.tooltipScaleEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new DropdownSetting(misc, "Tooltip Scale", "Scale Mode",
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

        settings.add(new SliderSetting(misc, "Tooltip Scale", "Custom Scale",
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

        settings.add(new ToggleSetting(misc, "Equipment in Inventory", "Equipment in Inventory",
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

        settings.add(new ToggleSetting(misc, "Equipment in Inventory", "Rarity Backgrounds",
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

        settings.add(new ToggleSetting(misc, "Hide Shield Slot", "Hide Shield Slot",
                Component.literal("Removes the shield slot from your inventory on SkyBlock, where it does nothing")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.hideShieldSlot,
                v -> {
                    cfg.hideShieldSlot = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(misc, "Hide Crafting", "Hide Crafting",
                Component.literal("Removes the crafting grid, its arrow, the output slot and the Crafting text "
                        + "from your inventory on SkyBlock").withStyle(ChatFormatting.GRAY),
                () -> cfg.hideCrafting,
                v -> {
                    cfg.hideCrafting = v;
                    ConfigManager.save();
                }));

        settings.add(new ToggleSetting(misc, "Item Star Count", "Item Star Count",
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

        settings.add(colourSetting(misc, "Item Star Count", "Star Count Colour", "Colour of the star number",
                () -> cfg.itemStarColour, v -> cfg.itemStarColour = v)
                .visibleWhen(() -> cfg.itemStarCount));

        settings.add(new SliderSetting(misc, "Item Star Count", "Star Count Size",
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
