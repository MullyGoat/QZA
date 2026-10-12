package com.qza.music;

import com.qza.QZA;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class MusicLibrary {
    private static final String README = """
            Drop your terminal-phase music in this folder.

            Supported formats: .mp3, .ogg, .wav, .aiff, .au

            No converting needed -- drop the file in and it plays.

            Multiple files become a playlist. Shuffle and looping are
            toggleable in /qza under the Music category.
            """;

    private static final String[] DEFAULT_SONGS = {
            "BizzM7DissTrack.ogg", "CharlieKirk.ogg", "Phantom.ogg", "Prague.ogg"};

    private static final String DEFAULT_SONG_FOLDER = "/qza-music/";

    private static final String INSTALLED_FILE = "default-songs.txt";

    private static List<Path> cached = new ArrayList<>();

    private MusicLibrary() {
    }

    public static Path musicDir() {
        return ConfigManager.qzaDir().resolve("music");
    }

    public static void ensureDir() {
        Path dir = musicDir();
        try {
            Files.createDirectories(dir);
            Path readme = dir.resolve("README.txt");
            String existing = Files.isRegularFile(readme)
                    ? Files.readString(readme, StandardCharsets.UTF_8) : null;
            if (!README.equals(existing)) {
                Files.writeString(readme, README, StandardCharsets.UTF_8);
            }
            installDefaultSongs(dir);
        } catch (IOException e) {
            QZA.LOGGER.error("Could not create the music folder", e);
        }
    }

    private static void installDefaultSongs(Path dir) throws IOException {
        Path record = ConfigManager.qzaDir().resolve(INSTALLED_FILE);
        Set<String> installed = new TreeSet<>(Files.isRegularFile(record)
                ? Files.readAllLines(record, StandardCharsets.UTF_8) : List.of());
        Set<String> present = new HashSet<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(p -> present.add(p.getFileName().toString().toLowerCase(Locale.ROOT)));
        }

        boolean changed = false;
        for (String song : DEFAULT_SONGS) {
            if (installed.contains(song)) {
                continue;
            }
            installed.add(song);
            changed = true;
            if (present.contains(song.toLowerCase(Locale.ROOT))) {
                continue;
            }
            try (InputStream in = MusicLibrary.class.getResourceAsStream(DEFAULT_SONG_FOLDER + song)) {
                if (in == null) {
                    QZA.LOGGER.warn("QZA is missing its bundled song {}", song);
                    continue;
                }
                Files.copy(in, dir.resolve(song));
            }
        }
        if (changed) {
            Files.write(record, installed, StandardCharsets.UTF_8);
        }
    }

    public static List<Path> reload() {
        ensureDir();
        List<Path> found = new ArrayList<>();
        try (Stream<Path> stream = Files.list(musicDir())) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> PcmStream.isSupported(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(found::add);
        } catch (IOException e) {
            QZA.LOGGER.error("Could not list the music folder", e);
        }
        cached = found;
        return found;
    }

    public static List<Path> tracks() {
        if (cached.isEmpty()) {
            return reload();
        }
        return cached;
    }

    public static int count() {
        return tracks().size();
    }

    public static Path findByName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        for (Path track : tracks()) {
            if (track.getFileName().toString().equals(fileName)) {
                return track;
            }
        }
        return null;
    }

    public static void openFolder() {
        ensureDir();
        Path dir = musicDir();
        Minecraft.getInstance().execute(() -> {
            try {
                Mc.openUri(dir.toUri());
            } catch (Throwable first) {
                try {
                    java.awt.Desktop.getDesktop().open(dir.toFile());
                } catch (Throwable second) {
                    QZA.LOGGER.error("Could not open {}", dir, second);
                }
            }
        });
    }
}
