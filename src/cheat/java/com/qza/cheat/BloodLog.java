package com.qza.cheat;

import com.qza.QZA;
import com.qza.config.ConfigManager;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class BloodLog {
    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int FLUSH_EVERY = 20;

    private static BufferedWriter out;
    private static int lines;

    private BloodLog() {
    }

    static boolean on() {
        return CheatConfigManager.get().bloodClickDebug;
    }

    static void line(String text) {
        if (!on()) {
            close();
            return;
        }
        try {
            if (out == null) {
                Path dir = ConfigManager.qzaDir().resolve("debug");
                Files.createDirectories(dir);
                out = Files.newBufferedWriter(dir.resolve("bloodcamp-" + FILE.format(LocalDateTime.now()) + ".txt"),
                        StandardCharsets.UTF_8);
            }
            out.write(TIME.format(LocalTime.now()));
            out.write(' ');
            out.write(text);
            out.newLine();
            if (++lines % FLUSH_EVERY == 0) {
                out.flush();
            }
        } catch (IOException e) {
            QZA.LOGGER.error("Failed to write the BloodCamp Triggerbot debug log", e);
            close();
        }
    }

    static void flush() {
        if (out == null) {
            return;
        }
        try {
            out.flush();
        } catch (IOException e) {
            close();
        }
    }

    private static void close() {
        if (out == null) {
            return;
        }
        try {
            out.close();
        } catch (IOException ignored) {
        }
        out = null;
    }

    static String pos(Vec3 at) {
        return at == null ? "?" : String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", at.x, at.y, at.z);
    }
}
