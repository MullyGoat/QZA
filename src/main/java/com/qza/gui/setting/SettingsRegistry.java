package com.qza.gui.setting;

import com.qza.chat.ChatFocus;
import com.qza.chat.ChatHistory;
import com.qza.chat.ChatKeybind;
import com.qza.chat.ChatNotification;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.gui.QZAChatScreen;
import com.qza.music.MusicLibrary;
import com.qza.music.MusicManager;
import com.qza.notify.NotificationGate;
import com.qza.party.PartyNotification;
import com.qza.shitter.ShitterList;
import com.qza.shitter.ShitterListPage;
import com.qza.util.ChatUtil;
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
                () -> ShitterList.size() + " listed",
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
                SettingsRegistry::stripExtension,
                MusicLibrary::reload,
                "(no music)",
                170)
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

        String music = "Music";

        settings.add(new ActionSetting(music, "Library", "Add Music",
                Component.literal("Opens QZA's music folder - Only drag and drop ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(".ogg").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" or ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(".wav").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" files to play").withStyle(ChatFormatting.GRAY)),
                "Open Folder",
                MusicLibrary::openFolder));

        settings.add(new ActionSetting(music, "Library", "Reload Playlist",
                Component.literal("Re-scan the folder after adding files.").withStyle(ChatFormatting.GRAY),
                () -> MusicLibrary.count() + " track" + (MusicLibrary.count() == 1 ? "" : "s"),
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

        settings.add(new ToggleSetting(chat, "Open Chat", "Open With T",
                Component.literal("Override chat keybind to open QZA Chat")
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

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
