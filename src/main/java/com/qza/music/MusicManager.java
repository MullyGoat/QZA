package com.qza.music;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.ChatUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

public final class MusicManager {
    private static final MusicManager INSTANCE = new MusicManager();

    private final MusicPlayer player = new MusicPlayer();

    private MusicManager() {
    }

    public static MusicManager get() {
        return INSTANCE;
    }

    public MusicPlayer player() {
        return player;
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public void onChatMessage(String raw) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.terminalMusicEnabled) {
            return;
        }

        String message = raw.replace("§", "");

        if (matches(message, cfg.musicStartTrigger)) {
            startPlaylist();
            return;
        }

        if (matches(message, cfg.musicStopTrigger) && player.isPlaying()) {
            player.fadeOutAndStop();
        }
    }

    private static boolean matches(String message, String trigger) {
        return trigger != null && !trigger.isBlank() && message.contains(trigger);
    }

    public void startPlaylist() {
        QZAConfig cfg = ConfigManager.get();
        List<Path> tracks = MusicLibrary.reload();

        if (tracks.isEmpty()) {
            ChatUtil.error("No music found. Use the \"Add Music\" button in /qza to open the folder.");
            return;
        }

        List<Path> queue;
        boolean shuffle;
        if (cfg.shuffleMode) {
            queue = tracks;
            shuffle = true;
        } else {
            Path chosen = MusicLibrary.findByName(cfg.selectedTrack);
            if (chosen == null) {
                chosen = tracks.get(0);
            }
            queue = List.of(chosen);
            shuffle = false;
        }

        applySettings();
        player.play(queue, shuffle, true);
    }

    public void stopNow() {
        player.stopNow();
    }

    public void toggleTestPlayback() {
        if (player.isPlaying()) {
            player.fadeOutAndStop();
            ChatUtil.info("Stopping music.");
        } else {
            startPlaylist();
            Path track = player.currentTrack();
            ChatUtil.send(Component.literal("Testing playback").withStyle(ChatFormatting.GRAY)
                    .append(track == null
                            ? Component.literal("...").withStyle(ChatFormatting.GRAY)
                            : Component.literal(" - " + track.getFileName())
                                    .withStyle(ChatFormatting.GREEN)));
        }
    }

    public void applySettings() {
        QZAConfig cfg = ConfigManager.get();
        player.setVolume((float) (cfg.musicVolume / 100.0));
        player.setFadeMillis((int) cfg.fadeMillis);
    }
}
