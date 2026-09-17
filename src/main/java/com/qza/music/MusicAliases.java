package com.qza.music;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;

import java.util.Map;

public final class MusicAliases {
    private MusicAliases() {
    }

    private static Map<String, String> map() {
        QZAConfig cfg = ConfigManager.get();
        if (cfg.trackNames == null) {
            cfg.trackNames = new java.util.LinkedHashMap<>();
        }
        return cfg.trackNames;
    }

    public static String display(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        String alias = map().get(fileName);
        return alias == null || alias.isBlank() ? stripExtension(fileName) : alias;
    }

    public static String aliasOf(String fileName) {
        String alias = map().get(fileName);
        return alias == null ? "" : alias;
    }

    public static void set(String fileName, String alias) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        String trimmed = alias == null ? "" : alias.trim();
        if (trimmed.isEmpty() || trimmed.equals(stripExtension(fileName))) {
            map().remove(fileName);
        } else {
            map().put(fileName, trimmed);
        }
    }

    public static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
