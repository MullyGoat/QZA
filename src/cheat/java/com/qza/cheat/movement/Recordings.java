package com.qza.cheat.movement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.qza.QZA;
import com.qza.config.ConfigManager;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class Recordings {
    public static final int MAX_NAME = 32;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String DEFAULT_NAME = "Recording ";

    private static final List<Recording> ALL = new ArrayList<>();
    private static boolean loaded;

    private Recordings() {
    }

    private static Path dir() {
        return ConfigManager.qzaDir().resolve("recordings");
    }

    public static List<Recording> all() {
        if (!loaded) {
            load();
        }
        return ALL;
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (Recording recording : all()) {
            names.add(recording.name);
        }
        return names;
    }

    public static void load() {
        loaded = true;
        ALL.clear();
        Path dir = dir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : files) {
                Recording recording = read(file);
                if (recording != null) {
                    ALL.add(recording);
                }
            }
        } catch (IOException e) {
            QZA.LOGGER.error("Failed to list movement recordings", e);
        }
        sort();
    }

    private static Recording read(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Recording recording = GSON.fromJson(reader, Recording.class);
            if (recording == null) {
                return null;
            }
            if (recording.frames == null) {
                recording.frames = new ArrayList<>();
            }
            recording.frames.removeIf(frame -> frame == null);
            if (recording.name == null || recording.name.isBlank()) {
                String stem = file.getFileName().toString();
                recording.name = stem.substring(0, stem.length() - ".json".length());
            }
            if (recording.area == null) {
                recording.area = "";
            }
            if (recording.trigger == null) {
                recording.trigger = Recording.START_AUTO;
            }
            recording.clampTrim();
            recording.file = file;
            return recording;
        } catch (Exception e) {
            QZA.LOGGER.error("Failed to read movement recording {}", file, e);
            return null;
        }
    }

    private static void sort() {
        ALL.sort(Comparator.comparing(recording -> recording.name.toLowerCase(Locale.ROOT)));
    }

    public static Recording find(String name) {
        if (name == null) {
            return null;
        }
        String wanted = name.trim();
        for (Recording recording : all()) {
            if (recording.name.equalsIgnoreCase(wanted)) {
                return recording;
            }
        }
        return null;
    }

    public static Recording at(BlockPos pos, String area) {
        if (pos == null) {
            return null;
        }
        for (Recording recording : all()) {
            if (pos.equals(recording.blockPos()) && sameArea(recording, area)) {
                return recording;
            }
        }
        return null;
    }

    public static boolean sameArea(Recording recording, String area) {
        return recording.area == null || recording.area.isEmpty() || area == null || area.isEmpty()
                || recording.area.equalsIgnoreCase(area);
    }

    public static String tidy(String name) {
        String trimmed = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        return trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME).trim() : trimmed;
    }

    public static String nextName() {
        int n = 1;
        while (find(DEFAULT_NAME + n) != null) {
            n++;
        }
        return DEFAULT_NAME + n;
    }

    public static void add(Recording recording) {
        Recording old = find(recording.name);
        if (old != null && old != recording) {
            if (!recording.placed() && old.placed()) {
                recording.block = old.block;
                recording.area = old.area;
            }
            remove(old);
        }
        if (!all().contains(recording)) {
            ALL.add(recording);
        }
        sort();
        save(recording);
    }

    public static void rename(Recording recording, String name) {
        String tidy = tidy(name);
        if (tidy.isEmpty() || tidy.equals(recording.name)) {
            return;
        }
        Recording other = find(tidy);
        if (other != null && other != recording) {
            return;
        }
        deleteFile(recording);
        recording.name = tidy;
        sort();
        save(recording);
    }

    public static void save(Recording recording) {
        recording.clampTrim();
        Path dir = dir();
        Path target = dir.resolve(fileName(recording));
        try {
            Files.createDirectories(dir);
            if (recording.file != null && !recording.file.equals(target)) {
                Files.deleteIfExists(recording.file);
            }
            try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                GSON.toJson(recording, writer);
            }
            recording.file = target;
        } catch (IOException e) {
            QZA.LOGGER.error("Failed to save movement recording {}", recording.name, e);
        }
    }

    public static void remove(Recording recording) {
        ALL.remove(recording);
        deleteFile(recording);
    }

    private static void deleteFile(Recording recording) {
        if (recording.file == null) {
            return;
        }
        try {
            Files.deleteIfExists(recording.file);
        } catch (IOException e) {
            QZA.LOGGER.error("Failed to delete movement recording {}", recording.file, e);
        }
        recording.file = null;
    }

    private static String fileName(Recording recording) {
        String safe = recording.name.replaceAll("[^A-Za-z0-9 _-]", "_").trim();
        if (safe.isEmpty()) {
            safe = "recording";
        }
        return safe + "-" + recording.created + ".json";
    }
}
