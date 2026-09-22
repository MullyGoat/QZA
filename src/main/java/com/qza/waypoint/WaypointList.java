package com.qza.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.qza.QZA;
import com.qza.config.ConfigManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WaypointList {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<List<Waypoint>>() {
    }.getType();

    public static final int MAX = 500;

    private static final List<Waypoint> waypoints = new ArrayList<>();

    private WaypointList() {
    }

    public static Path file() {
        return ConfigManager.qzaDir().resolve("waypoints.json");
    }

    public static void load() {
        waypoints.clear();

        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<Waypoint> loaded = GSON.fromJson(reader, TYPE);
            if (loaded == null) {
                return;
            }
            for (Waypoint waypoint : loaded) {
                if (waypoint == null) {
                    continue;
                }
                waypoint.colour = WaypointColour.tidy(waypoint.colour);
                waypoint.width = clamp(waypoint.width);
                waypoint.height = clamp(waypoint.height);
                waypoint.depth = clamp(waypoint.depth);
                if (waypoint.name == null) {
                    waypoint.name = "";
                }
                waypoints.add(waypoint);
            }
        } catch (Exception e) {
            QZA.LOGGER.error("Failed to read waypoints.json, keeping none", e);
        }
    }

    private static int clamp(int size) {
        return Math.max(1, Math.min(WaypointSize.MAX, size));
    }

    public static void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(waypoints), writer);
            }
        } catch (IOException e) {
            QZA.LOGGER.error("Failed to write waypoints.json", e);
        }
    }

    public static List<Waypoint> all() {
        return List.copyOf(waypoints);
    }

    public static int size() {
        return waypoints.size();
    }

    public static boolean full() {
        return waypoints.size() >= MAX;
    }

    public static Waypoint at(int index) {
        return index < 0 || index >= waypoints.size() ? null : waypoints.get(index);
    }

    public static Waypoint atBlock(int x, int y, int z) {
        for (Waypoint waypoint : waypoints) {
            if (waypoint.sameBlock(x, y, z)) {
                return waypoint;
            }
        }
        return null;
    }

    public static Waypoint add(Waypoint waypoint) {
        Waypoint existing = atBlock(waypoint.x, waypoint.y, waypoint.z);
        if (existing != null) {
            waypoints.set(waypoints.indexOf(existing), waypoint);
        } else {
            if (full()) {
                return null;
            }
            waypoints.add(waypoint);
        }
        save();
        return waypoint;
    }

    public static boolean remove(Waypoint waypoint) {
        boolean gone = waypoints.remove(waypoint);
        if (gone) {
            save();
        }
        return gone;
    }

    public static boolean removeAt(int x, int y, int z) {
        Waypoint existing = atBlock(x, y, z);
        return existing != null && remove(existing);
    }

    public static int clear() {
        int had = waypoints.size();
        waypoints.clear();
        save();
        return had;
    }
}
