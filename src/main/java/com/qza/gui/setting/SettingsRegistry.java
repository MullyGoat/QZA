package com.qza.gui.setting;

import com.qza.chat.ChatHistory;
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
                Component.literal("On: pick a ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("random").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" track every run. Off: always play the chosen track below.")
                                .withStyle(ChatFormatting.GRAY)),
                () -> cfg.shuffleMode,
                v -> {
                    cfg.shuffleMode = v;
                    ConfigManager.save();
                })
                .visibleWhen(() -> cfg.terminalMusicEnabled));

        settings.add(new DropdownSetting(f7, "Track Selection", "Chosen Track",
                Component.literal("Pick the track from your music folder. Only used when ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Shuffle Mode").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" is off.").withStyle(ChatFormatting.GRAY)),
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
                .visibleWhen(() -> cfg.terminalMusicEnabled));

        settings.add(new ToggleSetting(f7, "Necron Timer", "Necron Kill Time",
                Component.literal("Announces how long it took to kill Necron before the phase is fully over")
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
                Component.literal("Opens ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("config/qza/music/").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" - drag and drop ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(".ogg").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" or ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(".wav").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" files straight in.").withStyle(ChatFormatting.GRAY)),
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
                Component.literal("Play right now to check volume and format support.")
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
                Component.literal("Fade in on start and fade out on stop. Music loops automatically for as long as the terminal phase lasts.")
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
                Component.literal("Saves the whispers you send and receive so you can read them in one place.")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.qzaChatEnabled,
                v -> {
                    cfg.qzaChatEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new ActionSetting(chat, "QZA Chat", "Open QZA Chat",
                Component.literal("Opens the messaging screen. Same as ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("/qza chat").withStyle(ChatFormatting.LIGHT_PURPLE)),
                () -> {
                    int unread = ChatHistory.unreadTotal();
                    return unread > 0 ? "Open (" + unread + ")" : "Open";
                },
                () -> Minecraft.getInstance().setScreen(new QZAChatScreen()))
                .visibleWhen(() -> cfg.qzaChatEnabled));

        settings.add(new DropdownSetting(chat, "History", "Message History",
                Component.literal("Keep every conversation on disk, or wipe them all when the game launches.")
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
        settings.add(new ActionSetting(chat, "History", "Clear Messages",
                Component.literal("Deletes every saved conversation. ")
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
                Component.literal("Only pop notifications while you are inside a dungeon run. ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Everything stays silent outside of one.")
                                .withStyle(ChatFormatting.WHITE)),
                () -> cfg.dungeonOnlyNotifications,
                v -> {
                    cfg.dungeonOnlyNotifications = v;
                    ConfigManager.save();
                }));

        settings.add(new DropdownSetting(notify, "Dungeon Runs", "Show During Runs",
                Component.literal("Which notifications you still want once you are in a run.")
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
                Component.literal("Pops a notification on screen when someone invites you to their party, so you do not miss it in busy chat.")
                        .withStyle(ChatFormatting.GRAY),
                () -> cfg.partyInviteNotifyEnabled,
                v -> {
                    cfg.partyInviteNotifyEnabled = v;
                    ConfigManager.save();
                }));

        settings.add(new SliderSetting(notify, "Party", "Notification Duration",
                Component.literal("How long the invite notification stays on screen before it fades out.")
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
                Component.literal("Ringer plays a ding, Silent shows it quietly, Do Not Disturb hides it completely.")
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
                Component.literal("How long the message notification stays on screen before it fades out.")
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
                Component.literal("Size of this settings screen. ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("100%").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" is the default; lower makes it smaller.")
                                .withStyle(ChatFormatting.GRAY)),
                50, 150, 5, "%",
                () -> cfg.guiScale,
                v -> {
                    cfg.guiScale = v;
                    ConfigManager.save();
                }));

        settings.add(new ActionSetting(misc, "Config", "Reset Settings",
                Component.literal("Restores every option to its default. ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Your shitter list is kept.")
                                .withStyle(ChatFormatting.GREEN)),
                "Reset",
                () -> {
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
