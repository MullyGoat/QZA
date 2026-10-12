package com.qza.cheat.movement;

import com.qza.QZA;
import com.qza.cheat.CheatConfigManager;
import com.qza.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
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

final class MovementLog {
    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int FLUSH_EVERY = 40;

    private static BufferedWriter out;
    private static int lines;

    private MovementLog() {
    }

    static boolean on() {
        return CheatConfigManager.get().movementDebug;
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
                out = Files.newBufferedWriter(dir.resolve("movement-" + FILE.format(LocalDateTime.now()) + ".txt"),
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
            QZA.LOGGER.error("Failed to write the movement debug log", e);
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

    static String pos(Entity entity) {
        return entity == null ? "?" : pos(entity.position());
    }

    static String pos(Vec3 at) {
        return at == null ? "?" : String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", at.x, at.y, at.z);
    }

    static String pos(MoveFrame frame) {
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", frame.x, frame.y, frame.z);
    }

    static String pos(BlockPos block) {
        return block == null ? "?" : block.getX() + ", " + block.getY() + ", " + block.getZ();
    }
}
