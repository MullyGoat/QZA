package com.qza.dungeon;

import com.qza.QZA;
import com.qza.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ScoreLog {
    private static final int EVERY_TICKS = 20;
    private static final String NOAMM_SCORE = "com.github.noamm9.utils.dungeons.map.handlers.ScoreCalculation";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static BufferedWriter writer;
    private static int ticks;
    private static int lastQza = -1;
    private static int lastNoamm = -1;
    private static boolean noammChecked;
    private static Object noammInstance;
    private static Method noammScore;

    private ScoreLog() {
    }

    public static void tick(DungeonScore.Snapshot s, DungeonScore.Breakdown b) {
        if (!ConfigManager.get().scoreDebugLog) {
            end();
            return;
        }
        if (s == null || b == null) {
            return;
        }
        int noamm = noammScore();
        ticks++;
        if (b.total() == lastQza && noamm == lastNoamm && ticks < EVERY_TICKS) {
            return;
        }
        ticks = 0;
        lastQza = b.total();
        lastNoamm = noamm;
        write(String.format(Locale.ROOT,
                "%s QZA %d (skill %d explore %d speed %d bonus %d) NoammAddons %s | secrets %.1f%% found %d"
                        + " | rooms %d cleared %d%% total rooms %.0f blood %b boss %b | puzzles %d/%d deaths %d"
                        + " spirit %b | crypts %d mimic %b prince %b bats %d paul %b | %ds",
                LocalDateTime.now().format(LINE_TIME), b.total(), b.skill(), b.explore(), b.speed(), b.bonus(),
                noamm < 0 ? "-" : String.valueOf(noamm), s.secretPercent(), s.secretsFound(), s.completedRooms(),
                s.clearedPercent(), s.roomTotal(), s.bloodDone(), s.inBoss(), s.puzzlesDone(), s.puzzles(),
                s.deaths(), s.firstDeathSpirit(), s.crypts(), s.mimic(), s.prince(), s.bats(), s.paul(),
                s.seconds()));
    }

    private static int noammScore() {
        if (!noammChecked) {
            noammChecked = true;
            try {
                Class<?> type = Class.forName(NOAMM_SCORE);
                Field instance = type.getField("INSTANCE");
                noammInstance = instance.get(null);
                noammScore = type.getMethod("getScore");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                noammScore = null;
            }
        }
        if (noammScore == null) {
            return -1;
        }
        try {
            return ((Number) noammScore.invoke(noammInstance)).intValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static void write(String line) {
        try {
            if (writer == null) {
                Path dir = FabricLoader.getInstance().getConfigDir().resolve("qza").resolve("debug");
                Files.createDirectories(dir);
                writer = Files.newBufferedWriter(dir.resolve("score-" + LocalDateTime.now().format(FILE_TIME) + ".txt"),
                        StandardCharsets.UTF_8);
            }
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not write the dungeon score log", e);
            end();
        }
    }

    public static void end() {
        lastQza = -1;
        lastNoamm = -1;
        ticks = 0;
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not close the dungeon score log", e);
        }
        writer = null;
    }
}
