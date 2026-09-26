package com.qza.cheat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.qza.QZA;
import com.qza.config.ConfigManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CheatConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final CheatConfig config = new CheatConfig();

    private CheatConfigManager() {
    }

    private static Path configFile() {
        return ConfigManager.qzaDir().resolve("cheat.json");
    }

    public static CheatConfig get() {
        return config;
    }

    public static void load() {
        Path file = configFile();
        if (!Files.isRegularFile(file)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            CheatConfig loaded = GSON.fromJson(reader, CheatConfig.class);
            if (loaded != null) {
                copyInto(loaded, config);
            }
        } catch (Exception e) {
            QZA.LOGGER.error("Failed to read cheat.json, keeping defaults", e);

            return;
        }

        save();
    }

    private static void copyInto(CheatConfig from, CheatConfig to) {
        for (Field field : CheatConfig.class.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.set(to, field.get(from));
            } catch (ReflectiveOperationException e) {
                QZA.LOGGER.warn("Could not copy cheat config field {}", field.getName(), e);
            }
        }
    }

    public static void save() {
        try {
            Files.createDirectories(ConfigManager.qzaDir());
            try (Writer writer = Files.newBufferedWriter(configFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            QZA.LOGGER.error("Failed to write cheat.json", e);
        }
    }

    public static void reset() {
        copyInto(new CheatConfig(), config);
        save();
    }
}
