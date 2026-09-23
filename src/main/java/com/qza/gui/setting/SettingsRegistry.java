package com.qza.gui.setting;

import com.qza.chat.ChatFocus;
import com.qza.chat.ChatHistory;
import com.qza.chat.ChatKeybind;
import com.qza.chat.ChatNotification;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.discord.DiscordAlert;
import com.qza.dungeon.WitherKey;
import com.qza.gui.MusicNamesScreen;
import com.qza.gui.setting.NumberSetting.Field;
import com.qza.gui.QZAChatScreen;
import com.qza.gui.TerminalGuiScreen;
import com.qza.gui.WaypointScreen;
import com.qza.music.MusicAliases;
import com.qza.music.MusicLibrary;
import com.qza.music.MusicManager;
import com.qza.notify.NotificationGate;
import com.qza.party.PartyNotification;
import com.qza.shitter.ShitterListPage;
import com.qza.stats.DungeonFloor;
import com.qza.util.ChatUtil;
import com.qza.waypoint.WaypointEditor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SettingsRegistry {
    public static final List<String> CATEGORIES = List.of(
            "Shitter List",
            "F7 / M7",
            "Music",
            "Chat",
            "Auto Check Stats",
            "Notifications",
            "Miscellaneous");

    private SettingsRegistry() {
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
                    Minecraft.getInstance().setScreen(null);
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
                    Minecraft.getInstance().setScreen(new MusicNamesScreen());
                })
                .visibleWhen(() -> cfg.terminalMusicEnabled && !cfg.shuffleMode));

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
                () -> Minecraft.getInstance().setScreen(new WaypointScreen()))
                .visibleWhen(() -> cfg.waypointsEnabled));

        settings.add(new ActionSetting(f7, "Waypoints", "Manual Waypoint Add",
                Component.literal("Closes the menu and lets you right click blocks to mark "
                                + "them. Right click a marked block to clear it. Press ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Esc").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" to finish.").withStyle(ChatFormatting.GRAY)),
                () -> WaypointEditor.active() ? "Stop" : "Add",
                () -> {
                    Minecraft.getInstance().setScreen(null);
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

        settings.add(new ToggleSetting(f7, "Custom Terminal GUI", "Custom Terminal GUI",
                Component.literal("Redraws the phase 3 terminals with a template of your "
                                + "choice instead of the chest. Works in ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Odin's Terminal Simulator")
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" too.").withStyle(ChatFormatting.GRAY)),
                () -> cfg.terminalGuiEnabled,
                v -> {
                    cfg.terminalGuiEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ActionSetting(f7, "Custom Terminal GUI", "Edit Terminal GUI",
                Component.literal("Preview every terminal and pick a template for each one")
                        .withStyle(ChatFormatting.GRAY),
                "Open",
                () -> Minecraft.getInstance().setScreen(new TerminalGuiScreen()))
                .visibleWhen(() -> cfg.terminalGuiEnabled));

        settings.add(new ToggleSetting(f7, "Custom Terminal GUI", "Only Show Next Numbers",
                Component.literal("In ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Click in order")
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(", hides every pane except the next few, "
                                + "and drops each one as it is clicked")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.terminalNumbersLimit,
                v -> {
                    cfg.terminalNumbersLimit = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalGuiEnabled));

        settings.add(new SliderSetting(f7, "Custom Terminal GUI", "Numbers Shown",
                Component.literal("How many panes stay on screen at once")
                        .withStyle(ChatFormatting.GRAY),
                1, 9, 1, "",
                () -> cfg.terminalNumbersShown,
                v -> {
                    cfg.terminalNumbersShown = (int) Math.round(v);
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalGuiEnabled && cfg.terminalNumbersLimit));

        settings.add(new ToggleSetting(f7, "Custom Terminal GUI", "Only Show What Is Left",
                Component.literal("Leaves only the slots still needing a click. Red panes in ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Correct all the panes")
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(", the asked colour in ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Select all the items")
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(", the asked letter in ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("What starts with")
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(". Each one drops away as it is clicked.")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.terminalHideDone,
                v -> {
                    cfg.terminalHideDone = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalGuiEnabled));

        settings.add(new ToggleSetting(f7, "Custom Terminal GUI", "Rubix Click Counts",
                Component.literal("In ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Change all to same color")
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(", works out the colour that takes the "
                                + "fewest clicks and writes each pane's share on it. ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("+").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" is a left click, ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("-").withStyle(ChatFormatting.RED))
                        .append(Component.literal(" is a right click.")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.terminalRubixHints,
                v -> {
                    cfg.terminalRubixHints = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalGuiEnabled));

        settings.add(new ToggleSetting(f7, "Custom Terminal GUI", "Aim At Melody Button",
                Component.literal("Puts the cursor on the melody button as the terminal "
                                + "opens. Moves your own cursor only, nothing is sent to "
                                + "the server.").withStyle(ChatFormatting.GRAY),
                () -> cfg.terminalMelodyAim,
                v -> {
                    cfg.terminalMelodyAim = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalGuiEnabled));

        settings.add(new ToggleSetting(f7, "Custom Terminal GUI", "Hold Melody Button",
                Component.literal("In ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Melody")
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(", keeps the button ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("red").withStyle(ChatFormatting.RED))
                        .append(Component.literal(" until the marker lines up with the "
                                + "target, then lets it go ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("green").withStyle(ChatFormatting.GREEN)),
                () -> cfg.terminalMelodyHold,
                v -> {
                    cfg.terminalMelodyHold = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalGuiEnabled));

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

        String music = "Music";

        settings.add(new ActionSetting(music, "Library", "Add Music",
                Component.literal("Opens QZA's music folder - Only drag and drop ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(".mp3").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(".ogg").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" or ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(".wav").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" files to play").withStyle(ChatFormatting.GRAY)),
                "Open Folder",
                MusicLibrary::openFolder));

        settings.add(new ActionSetting(music, "Library", "Reload Playlist",
                Component.literal("Re-scan the folder after adding files.").withStyle(ChatFormatting.GRAY),
                "Refresh",
                () -> {
                    int found = MusicLibrary.reload().size();
                    ChatUtil.success("Found " + found + " track" + (found == 1 ? "" : "s") + ".");
                }));

        settings.add(new ActionSetting(music, "Library", "Test Playback",
                Component.literal("Tests the output of a song in the folder")
                        .withStyle(ChatFormatting.GRAY),
                () -> MusicManager.get().isPlaying() ? "Stop" : "Play",
                () -> MusicManager.get().toggleTestPlayback()));

        settings.add(new SliderSetting(music, "Playback", "Volume",
                Component.literal("Independent of Minecraft's own music slider.")
                        .withStyle(ChatFormatting.GRAY),
                0, 100, 1, "%",
                () -> cfg.musicVolume,
                v -> {
                    cfg.musicVolume = v;
                    MusicManager.get().applySettings();
                    ConfigManager.save();
                }));

        settings.add(new SliderSetting(music, "Playback", "Fade Length",
                Component.literal("The fade in and fade out duration of songs")
                        .withStyle(ChatFormatting.GRAY),
                0, 5000, 100, "ms",
                () -> cfg.fadeMillis,
                v -> {
                    cfg.fadeMillis = v;
                    MusicManager.get().applySettings();
                    ConfigManager.save();
                }));

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
                () -> Minecraft.getInstance().setScreen(new QZAChatScreen()))
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
                Component.literal("Deletes every saved DM conversation. ")
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
                    Minecraft.getInstance().setScreen(null);
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

        String misc = "Miscellaneous";

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
                    MusicManager.get().applySettings();
                    ChatUtil.success("Settings reset to defaults.");
                    Minecraft.getInstance().setScreen(null);
                }));

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

}
