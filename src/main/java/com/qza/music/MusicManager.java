package com.qza.music;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.ChatUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Ties the chat triggers to the player.
 *
 * In F7 / M7, across the terminal phase:
 *   "[BOSS] Goldor: Who dares trespass into my domain?" -> music starts, looping
 *   "The Core entrance is opening!"                     -> music fades out
 *
 * The music always loops, so it lasts as long as the terminal phase does.
 * Leaving the run stops it -- see the level-change watcher in QZA.
 *
 * Both trigger substrings live in config.json (musicStartTrigger /
 * musicStopTrigger) so they can be re-pointed without a rebuild.
 */
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

    // ------------------------------------------------------------------ triggers

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

    // ------------------------------------------------------------------ control

    /**
     * Starts playback according to the current mode: a shuffled run through the
     * whole folder, or the one chosen track. Always loops.
     */
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
                // Chosen track was renamed or deleted -- fall back rather than go silent.
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

    /** Pushes the current config values into the running player. */
    public void applySettings() {
        QZAConfig cfg = ConfigManager.get();
        player.setVolume((float) (cfg.musicVolume / 100.0));
        player.setFadeMillis((int) cfg.fadeMillis);
    }
}
