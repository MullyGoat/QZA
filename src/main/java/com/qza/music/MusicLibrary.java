package com.qza.music;

import com.qza.QZA;
import com.qza.config.ConfigManager;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class MusicLibrary {
    private static final String README = """
            Drop your terminal-phase music in this folder.

            Supported formats: .ogg (recommended), .wav, .aiff, .au
            MP3 is not decodable by the JDK -- convert it to .ogg first
            (Audacity: File > Export > Export as OGG).

            Multiple files become a playlist. Shuffle and looping are
            toggleable in /qza under the Music category.
            """;

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
            if (!Files.exists(readme)) {
                Files.writeString(readme, README, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            QZA.LOGGER.error("Could not create the music folder", e);
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
                Util.getPlatform().openUri(dir.toUri());
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
