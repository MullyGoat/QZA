package com.qza.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.qza.QZA;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final QZAConfig config = new QZAConfig();

    private ConfigManager() {
    }

    public static Path qzaDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(QZA.MOD_ID);
    }

    private static Path configFile() {
        return qzaDir().resolve("config.json");
    }

    public static QZAConfig get() {
        return config;
    }

    public static void load() {
        Path file = configFile();
        if (!Files.isRegularFile(file)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            QZAConfig loaded = GSON.fromJson(reader, QZAConfig.class);
            if (loaded != null) {
                copyInto(loaded, config);
            }
        } catch (Exception e) {
            QZA.LOGGER.error("Failed to read config.json, keeping defaults", e);
        }
    }

    private static void copyInto(QZAConfig from, QZAConfig to) {
        for (Field field : QZAConfig.class.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.set(to, field.get(from));
            } catch (ReflectiveOperationException e) {
                QZA.LOGGER.warn("Could not copy config field {}", field.getName(), e);
            }
        }
    }

    public static void save() {
        try {
            Files.createDirectories(qzaDir());
            try (Writer writer = Files.newBufferedWriter(configFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            QZA.LOGGER.error("Failed to write config.json", e);
        }
    }

    public static void reset() {
        copyInto(new QZAConfig(), config);
        save();
    }
}
