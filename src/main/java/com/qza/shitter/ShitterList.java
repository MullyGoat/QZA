package com.qza.shitter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.qza.QZA;
import com.qza.config.ConfigManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShitterList {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<ShitterEntry>>() {
    }.getType();

    private static final Map<String, ShitterEntry> ENTRIES = new LinkedHashMap<>();

    private ShitterList() {
    }

    private static Path file() {
        return ConfigManager.qzaDir().resolve("shitterlist.json");
    }

    private static String key(String ign) {
        return ign.toLowerCase(Locale.ROOT);
    }

    public static boolean contains(String ign) {
        return ENTRIES.containsKey(key(ign));
    }

    public static ShitterEntry get(String ign) {
        return ENTRIES.get(key(ign));
    }

    public static List<ShitterEntry> all() {
        return new ArrayList<>(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static ShitterEntry add(String ign, String reason) {
        ShitterEntry previous = ENTRIES.get(key(ign));
        ENTRIES.put(key(ign), new ShitterEntry(ign, reason));
        save();
        return previous;
    }

    public static ShitterEntry remove(String ign) {
        ShitterEntry removed = ENTRIES.remove(key(ign));
        if (removed != null) {
            save();
        }
        return removed;
    }

    public static int clear() {
        int removed = ENTRIES.size();
        ENTRIES.clear();
        save();
        return removed;
    }

    public static void load() {
        ENTRIES.clear();
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Collection<ShitterEntry> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded != null) {
                for (ShitterEntry entry : loaded) {
                    if (entry != null && entry.name != null && !entry.name.isBlank()) {
                        ENTRIES.put(key(entry.name), entry);
                    }
                }
            }
        } catch (Exception e) {
            QZA.LOGGER.error("Failed to read shitterlist.json", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(ConfigManager.qzaDir());
            try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(ENTRIES.values()), LIST_TYPE, writer);
            }
        } catch (IOException e) {
            QZA.LOGGER.error("Failed to write shitterlist.json", e);
        }
    }
}
