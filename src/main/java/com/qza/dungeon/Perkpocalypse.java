package com.qza.dungeon;

import com.google.gson.Gson;
import com.qza.QZA;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Perkpocalypse {
    static final long EPOCH = 1560275700000L;
    static final long YEAR = 446400000L;
    static final long ELECTION = 105600000L;
    static final long TURN = 21600000L;
    static final int MAYORS = 6;

    private static final Gson GSON = new Gson();
    private static Learned learned;

    private Perkpocalypse() {
    }

    private static final class Learned {
        long term = Long.MIN_VALUE;
        int paulTurn = -1;
    }

    static long term(long now) {
        return Math.floorDiv(now - EPOCH - ELECTION, YEAR);
    }

    static int turn(long now) {
        long start = EPOCH + ELECTION + term(now) * YEAR;
        return (int) Math.floorMod((now - start) / TURN, (long) MAYORS);
    }

    public static synchronized boolean paulNow(long now) {
        Learned l = learned();
        return l.term == term(now) && l.paulTurn >= 0 && l.paulTurn == turn(now);
    }

    static synchronized void learn(long now, boolean paul) {
        Learned l = learned();
        long term = term(now);
        int turn = turn(now);
        boolean known = l.term == term && l.paulTurn == turn;
        if (paul == known) {
            return;
        }
        Learned next = new Learned();
        next.term = term;
        next.paulTurn = paul ? turn : -1;
        learned = next;
        write(next);
        QZA.LOGGER.info("QZA Dungeon Score: Jerry's turn {} of 6 {} Paul's", turn + 1, paul ? "is" : "isn't");
    }

    private static Learned learned() {
        if (learned == null) {
            learned = read();
        }
        return learned;
    }

    private static Path file() {
        return Path.of(System.getProperty("user.home"), ".qza", "perkpocalypse.json");
    }

    private static Learned read() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return new Learned();
        }
        try {
            Learned loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Learned.class);
            return loaded == null ? new Learned() : loaded;
        } catch (IOException | RuntimeException e) {
            QZA.LOGGER.warn("Could not read Jerry's Paul turn from {}", file, e);
            return new Learned();
        }
    }

    private static void write(Learned l) {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("perkpocalypse.json.tmp");
            Files.writeString(tmp, GSON.toJson(l), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not save Jerry's Paul turn to {}", file, e);
        }
    }
}
