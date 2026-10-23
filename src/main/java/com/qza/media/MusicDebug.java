package com.qza.media;

import com.qza.QZA;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class MusicDebug {
    static final long TICKS_PER_MS = 10_000L;

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final long SESSIONS_MS = 2000L;
    private static final long HEARTBEAT_MS = 10_000L;
    private static final long FILETIME_EPOCH = 116_444_736_000_000_000L;
    private static final String[] STATUS = {"Closed", "Opened", "Changing", "Stopped", "Playing", "Paused"};

    private static BufferedWriter writer;
    private static long sessionsAt;
    private static String lastSessions = "";
    private static String lastState = "";
    private static String lastCover = "";
    private static long stateAt;

    private MusicDebug() {
    }

    static boolean active() {
        return ConfigManager.get().musicDebugLog;
    }

    static void line(String text) {
        if (active()) {
            write(LocalDateTime.now().format(LINE_TIME) + " " + text);
        }
    }

    static void error(String what, Throwable e) {
        if (!active()) {
            return;
        }
        StringWriter trace = new StringWriter();
        e.printStackTrace(new PrintWriter(trace));
        line(what + "\n" + trace.toString().stripTrailing());
    }

    static void sessions() {
        if (!active()) {
            stop();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - sessionsAt < SESSIONS_MS) {
            return;
        }
        sessionsAt = now;
        List<WindowsMedia.Listed> all = WindowsMedia.sessions();
        String current = WindowsMedia.currentApp();
        StringBuilder key = new StringBuilder(current);
        StringBuilder text = new StringBuilder("sessions: ").append(all.size())
                .append(all.size() == 1 ? " app" : " apps").append(", Windows' current one is ")
                .append(current.isEmpty() ? "none" : current);
        for (int i = 0; i < all.size(); i++) {
            WindowsMedia.Listed listed = all.get(i);
            String session = describe(listed.track()) + (listed.problem().isEmpty() ? "" : " | " + listed.problem());
            key.append('\n').append(session);
            text.append("\n    #").append(i).append(' ').append(session)
                    .append(" | updated ").append(updated(listed.track().updatedTicks(), now));
        }
        if (!key.toString().equals(lastSessions)) {
            lastSessions = key.toString();
            line(text.toString());
        }
    }

    static void state(String key, String text) {
        if (!active()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (key.equals(lastState) && now - stateAt < HEARTBEAT_MS) {
            return;
        }
        lastState = key;
        stateAt = now;
        line(text);
    }

    static void cover(byte[] bytes, int width, int height) {
        cover(bytes.length + " bytes of " + format(bytes) + ", "
                + (width > 0 ? "shown at " + width + "x" + height : "QZA couldn't decode it"));
    }

    static void cover(String result) {
        if (active() && !result.equals(lastCover)) {
            lastCover = result;
            line("cover: " + result);
        }
    }

    static String controls(int flags) {
        StringBuilder names = new StringBuilder();
        if ((flags & WindowsMedia.CAN_PREVIOUS) != 0) {
            names.append(" previous");
        }
        if ((flags & WindowsMedia.CAN_NEXT) != 0) {
            names.append(" next");
        }
        if ((flags & WindowsMedia.CAN_TOGGLE) != 0) {
            names.append(" play/pause");
        }
        if ((flags & WindowsMedia.CAN_SEEK) != 0) {
            names.append(" seek");
        }
        return names.isEmpty() ? "none" : names.toString().trim();
    }

    static String clock(long millis) {
        long abs = Math.abs(millis);
        return String.format(Locale.ROOT, "%s%d:%02d.%03d", millis < 0 ? "-" : "", abs / 60_000L,
                (abs / 1000L) % 60L, abs % 1000L);
    }

    private static String describe(WindowsMedia.Track track) {
        int status = track.status();
        return track.app() + " | " + (status >= 0 && status < STATUS.length ? STATUS[status] : "status " + status)
                + " | can: " + controls(track.controls())
                + " | \"" + track.title() + "\" by \"" + track.artist() + "\" on \"" + track.album() + "\""
                + " | start " + clock(track.startTicks() / TICKS_PER_MS)
                + " end " + clock(track.endTicks() / TICKS_PER_MS)
                + " seek " + clock(track.minSeekTicks() / TICKS_PER_MS) + " to " + clock(track.maxSeekTicks() / TICKS_PER_MS)
                + " position " + clock(track.positionTicks() / TICKS_PER_MS)
                + " | last update " + track.updatedTicks();
    }

    private static String updated(long ticks, long now) {
        if (ticks <= FILETIME_EPOCH) {
            return "never";
        }
        long at = (ticks - FILETIME_EPOCH) / TICKS_PER_MS;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault()).format(LINE_TIME)
                + String.format(Locale.ROOT, " (%.1fs ago)", (now - at) / 1000.0);
    }

    private static String format(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "PNG";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "JPEG";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[8] == 'W' && bytes[9] == 'E') {
            return "WebP";
        }
        if (bytes.length >= 3 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "GIF";
        }
        if (bytes.length >= 2 && bytes[0] == 'B' && bytes[1] == 'M') {
            return "BMP";
        }
        StringBuilder start = new StringBuilder("an unknown format starting");
        for (int i = 0; i < Math.min(8, bytes.length); i++) {
            start.append(String.format(Locale.ROOT, " %02X", bytes[i] & 0xFF));
        }
        return start.toString();
    }

    private static synchronized void write(String text) {
        try {
            if (writer == null) {
                Path dir = folder();
                Files.createDirectories(dir);
                writer = Files.newBufferedWriter(dir.resolve("music-" + LocalDateTime.now().format(FILE_TIME) + ".txt"),
                        StandardCharsets.UTF_8);
                writer.write("QZA music debug log");
                writer.newLine();
                writer.write("QZA " + version("qza", "qzacheat") + " on Minecraft " + version("minecraft")
                        + ", " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                        + " (" + System.getProperty("os.arch") + "), Java " + System.getProperty("java.version"));
                writer.newLine();
                writer.newLine();
            }
            writer.write(text);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not write the music debug log", e);
            stop();
        }
    }

    static synchronized void stop() {
        sessionsAt = 0L;
        lastSessions = "";
        lastState = "";
        lastCover = "";
        stateAt = 0L;
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not close the music debug log", e);
        }
        writer = null;
    }

    private static String version(String... ids) {
        for (String id : ids) {
            String found = FabricLoader.getInstance().getModContainer(id)
                    .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                    .orElse(null);
            if (found != null) {
                return found;
            }
        }
        return "dev";
    }

    private static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("qza").resolve("debug");
    }

    public static void openFolder() {
        Path dir = folder();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not make {}", dir, e);
        }
        Minecraft.getInstance().execute(() -> {
            try {
                Mc.openUri(dir.toUri());
            } catch (Throwable first) {
                try {
                    java.awt.Desktop.getDesktop().open(dir.toFile());
                } catch (Throwable second) {
                    QZA.LOGGER.error("Could not open {}", dir, second);
                }
            }
        });
    }
}
