package com.qza.gui.setting;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.music.MusicLibrary;
import com.qza.music.MusicManager;
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

    /** Sidebar order. Only categories that actually have features. */
    public static final List<String> CATEGORIES = List.of(
            "Shitter List",
            "F7 / M7",
            "Music",
            "Miscellaneous");

    private SettingsRegistry() {
    }

    public static List<Setting> build() {
        QZAConfig cfg = ConfigManager.get();
        List<Setting> settings = new ArrayList<>();

        // ------------------------------------------------------- Shitter List
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

        // ------------------------------------------------------- F7 / M7
        String f7 = "F7 / M7";

        settings.add(new ToggleSetting(f7, "Terminal Music", "Terminal Music Toggle",
                Component.literal("Plays music during terminal phase").withStyle(ChatFormatting.GRAY),
                () -> cfg.terminalMusicEnabled,
                v -> {
                    cfg.terminalMusicEnabled = v;
                    ConfigManager.save();
                }));

        // Both of these are meaningless with the music off, so they disappear.
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

        // ------------------------------------------------------- Music
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

        // ------------------------------------------------------- Miscellaneous
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

    // ------------------------------------------------------------------ track picker

    /** Bare file names, cached -- the dropdown re-scans on open via onOpen. */
    private static List<String> trackNames() {
        List<String> names = new ArrayList<>();
        for (Path track : MusicLibrary.tracks()) {
            names.add(track.getFileName().toString());
        }
        return names;
    }

    /** Drops the extension so more of the actual name fits. */
    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
