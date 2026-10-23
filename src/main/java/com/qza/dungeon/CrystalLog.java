package com.qza.dungeon;

import com.qza.QZA;
import com.qza.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class CrystalLog {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static BufferedWriter writer;

    private CrystalLog() {
    }

    public static boolean on() {
        return ConfigManager.get().crystalDebugLog;
    }

    public static void line(String text) {
        if (!on()) {
            end();
            return;
        }
        try {
            if (writer == null) {
                Path dir = FabricLoader.getInstance().getConfigDir().resolve("qza").resolve("debug");
                Files.createDirectories(dir);
                writer = Files.newBufferedWriter(
                        dir.resolve("crystals-" + LocalDateTime.now().format(FILE_TIME) + ".txt"),
                        StandardCharsets.UTF_8);
            }
            writer.write(LocalDateTime.now().format(LINE_TIME) + " " + text);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not write the crystal log", e);
            end();
        }
    }

    public static void end() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not close the crystal log", e);
        }
        writer = null;
    }
}
