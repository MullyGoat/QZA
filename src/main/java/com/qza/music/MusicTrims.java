package com.qza.music;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MusicTrims {
    private MusicTrims() {
    }

    private static Map<String, TrackTrim> map() {
        QZAConfig cfg = ConfigManager.get();
        if (cfg.trackTrims == null) {
            cfg.trackTrims = new LinkedHashMap<>();
        }
        return cfg.trackTrims;
    }

    public static double start(String fileName) {
        TrackTrim trim = map().get(fileName);
        return trim == null ? 0.0 : Math.max(0.0, trim.start);
    }

    public static double end(String fileName) {
        TrackTrim trim = map().get(fileName);
        return trim == null ? 0.0 : Math.max(0.0, trim.end);
    }

    public static boolean isTrimmed(String fileName) {
        return start(fileName) > 0.0 || end(fileName) > 0.0;
    }

    public static void set(String fileName, double start, double end) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        double from = Math.max(0.0, start);
        double to = Math.max(0.0, end);
        if (to > 0.0 && to <= from) {
            to = 0.0;
        }
        if (from <= 0.0 && to <= 0.0) {
            map().remove(fileName);
        } else {
            map().put(fileName, new TrackTrim(from, to));
        }
    }

    public static String label(String fileName) {
        double from = start(fileName);
        double to = end(fileName);
        if (from <= 0.0 && to <= 0.0) {
            return "full";
        }
        return time(from) + " - " + (to > 0.0 ? time(to) : "end");
    }

    public static String time(double seconds) {
        int total = (int) Math.round(seconds);
        return (total / 60) + ":" + String.format("%02d", total % 60);
    }
}
