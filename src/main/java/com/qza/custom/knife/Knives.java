package com.qza.custom.knife;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qza.QZA;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Knives {
    public enum Kind {
        FIXED,
        FOLDING,
        BUTTERFLY,
        KARAMBIT,
        DAGGERS
    }

    public record Knife(String id, String label, Kind kind, String grip, String metal, String accent) {
        public boolean folding() {
            return kind == Kind.FOLDING;
        }
    }

    public static final List<Knife> ALL = List.of(
            new Knife("bayonet", "Bayonet", Kind.FIXED, "rubber", "steel", "steel"),
            new Knife("bowie", "Bowie Knife", Kind.FIXED, "wood", "brass", "brass"),
            new Knife("butterfly", "Butterfly Knife", Kind.BUTTERFLY, "rubber", "steel", "steel"),
            new Knife("classic", "Classic Knife", Kind.FIXED, "leather", "steel", "steel"),
            new Knife("falchion", "Falchion Knife", Kind.FIXED, "polymer", "steel", "steel"),
            new Knife("flip", "Flip Knife", Kind.FOLDING, "g10", "steel", "steel"),
            new Knife("gut", "Gut Knife", Kind.FIXED, "rubber", "steel", "steel"),
            new Knife("huntsman", "Huntsman Knife", Kind.FIXED, "rubber", "steel", "steel"),
            new Knife("karambit", "Karambit", Kind.KARAMBIT, "polymer", "steel", "steel"),
            new Knife("kukri", "Kukri Knife", Kind.FIXED, "wood", "brass", "brass"),
            new Knife("m9", "M9 Bayonet", Kind.FIXED, "rubber_blue", "steel", "steel"),
            new Knife("navaja", "Navaja Knife", Kind.FOLDING, "wood", "brass", "brass"),
            new Knife("nomad", "Nomad Knife", Kind.FIXED, "polymer", "steel", "steel"),
            new Knife("paracord", "Paracord Knife", Kind.FIXED, "rubber", "steel", "paracord_black"),
            new Knife("daggers", "Shadow Daggers", Kind.DAGGERS, "rubber", "steel", "steel"),
            new Knife("skeleton", "Skeleton Knife", Kind.FIXED, "rubber", "steel", "paracord_green"),
            new Knife("stiletto", "Stiletto Knife", Kind.FOLDING, "wood_dark", "steel", "steel"),
            new Knife("survival", "Survival Knife", Kind.FIXED, "rubber", "steel", "steel"),
            new Knife("talon", "Talon Knife", Kind.FOLDING, "bone", "steel", "steel"),
            new Knife("ursus", "Ursus Knife", Kind.FOLDING, "g10", "steel", "steel"));

    private static final Map<String, Knife> BY_ID = new LinkedHashMap<>();
    private static final Map<String, Shape> SHAPES = new HashMap<>();

    static {
        for (Knife knife : ALL) {
            BY_ID.put(knife.id(), knife);
        }
    }

    private Knives() {
    }

    public record Shape(KnifeMesh mesh, float pivotX, float pivotY, float bladeLength) {
    }

    public static Knife get(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static boolean is(String id) {
        return BY_ID.containsKey(id);
    }

    public static List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (Knife knife : ALL) {
            ids.add(knife.id());
        }
        return ids;
    }

    public static Shape shape(String id) {
        if (!BY_ID.containsKey(id)) {
            return null;
        }
        return SHAPES.computeIfAbsent(id, Knives::load);
    }

    public static float[] pivotTexel(String id) {
        JsonObject root = read(id);
        if (root == null || !root.has("pivot")) {
            return null;
        }
        float[] origin = floats(root.getAsJsonArray("origin"));
        float[] pivot = floats(root.getAsJsonArray("pivot"));
        float x = KnifeMesh.modelX(origin[0], origin[1], pivot[0], pivot[1]) * 16f;
        float y = (1f - KnifeMesh.modelY(origin[0], origin[1], pivot[0], pivot[1])) * 16f;
        return new float[]{x, y};
    }

    private static JsonObject read(String id) {
        try (InputStream in = Knives.class.getResourceAsStream("/assets/qza/knives/" + id + ".json")) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            QZA.LOGGER.warn("QZA couldn't read the {} knife model", id, e);
            return null;
        }
    }

    private static Shape load(String id) {
        JsonObject root = read(id);
        if (root == null) {
            return null;
        }
        float[] origin = floats(root.getAsJsonArray("origin"));
        KnifeMesh.Builder builder = new KnifeMesh.Builder(origin[0], origin[1]);
        float bladeLength = 9f;
        for (JsonElement element : root.getAsJsonArray("parts")) {
            JsonObject part = element.getAsJsonObject();
            if (part.has("blade")) {
                KnifeMesh.Blade blade = blade(part.getAsJsonObject("blade"));
                builder.blade(blade);
                bladeLength = KnifeMesh.Curve.of(blade.spine, false, true, 32).length;
            } else if (part.has("sweep")) {
                builder.sweep(sweep(part.getAsJsonObject("sweep")));
            }
        }
        float px = 0f;
        float py = 0f;
        if (root.has("pivot")) {
            float[] pivot = floats(root.getAsJsonArray("pivot"));
            px = KnifeMesh.modelX(origin[0], origin[1], pivot[0], pivot[1]);
            py = KnifeMesh.modelY(origin[0], origin[1], pivot[0], pivot[1]);
        }
        return new Shape(builder.build(), px, py, bladeLength);
    }

    private static KnifeMesh.Blade blade(JsonObject o) {
        KnifeMesh.Blade b = new KnifeMesh.Blade();
        b.spine = floats(o.getAsJsonArray("spine"));
        b.edge = floats(o.getAsJsonArray("edge"));
        if (o.has("thick")) {
            b.thick = keys(o.get("thick"));
        }
        if (o.has("swedge")) {
            b.swedge = keys(o.get("swedge"));
        }
        b.swedgeWidth = number(o, "swedgeWidth", b.swedgeWidth);
        b.grind = number(o, "grind", b.grind);
        b.edgeBevel = number(o, "edgeBevel", b.edgeBevel);
        b.hollow = number(o, "hollow", b.hollow);
        b.z = number(o, "z", b.z);
        b.stations = (int) number(o, "stations", b.stations);
        b.doubleEdge = o.has("doubleEdge") && o.get("doubleEdge").getAsBoolean();
        b.group = group(o);
        if (o.has("teeth")) {
            float[] t = floats(o.getAsJsonArray("teeth"));
            b.teethFrom = t[0];
            b.teethTo = t[1];
            b.teeth = (int) t[2];
            b.teethDepth = t[3];
        }
        if (o.has("serrations")) {
            float[] t = floats(o.getAsJsonArray("serrations"));
            b.serrFrom = t[0];
            b.serrTo = t[1];
            b.serrations = (int) t[2];
            b.serrDepth = t[3];
        }
        if (o.has("fuller")) {
            float[] t = floats(o.getAsJsonArray("fuller"));
            b.fullerFrom = t[0];
            b.fullerTo = t[1];
            b.fullerW0 = t[2];
            b.fullerW1 = t[3];
            b.fullerDepth = t[4];
        }
        return b;
    }

    private static KnifeMesh.Sweep sweep(JsonObject o) {
        KnifeMesh.Sweep s = new KnifeMesh.Sweep();
        s.path = floats(o.getAsJsonArray("path"));
        s.closed = o.has("closed") && o.get("closed").getAsBoolean();
        s.smooth = !o.has("smooth") || o.get("smooth").getAsBoolean();
        s.caps = !o.has("caps") || o.get("caps").getAsBoolean();
        if (o.has("top")) {
            s.top = keys(o.get("top"));
        }
        if (o.has("bottom")) {
            s.bottom = keys(o.get("bottom"));
        }
        if (o.has("depth")) {
            s.depth = keys(o.get("depth"));
        }
        if (o.has("shift")) {
            s.shift = keys(o.get("shift"));
        }
        s.exponent = number(o, "exponent", s.exponent);
        s.z = number(o, "z", s.z);
        s.segments = (int) number(o, "segments", s.segments);
        s.stations = (int) number(o, "stations", s.stations);
        s.region = switch (o.has("region") ? o.get("region").getAsString() : "grip") {
            case "metal" -> KnifeMesh.METAL;
            case "accent" -> KnifeMesh.ACCENT;
            case "finished" -> KnifeMesh.FINISHED;
            default -> KnifeMesh.GRIP;
        };
        s.group = group(o);
        return s;
    }

    private static int group(JsonObject o) {
        return switch (o.has("group") ? o.get("group").getAsString() : "fixed") {
            case "blade" -> KnifeMesh.BLADE;
            case "back" -> KnifeMesh.BACK;
            default -> KnifeMesh.FIXED;
        };
    }

    private static float number(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }

    private static KnifeMesh.Keys keys(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return KnifeMesh.Keys.of(element.getAsFloat());
        }
        return KnifeMesh.Keys.of(floats(element.getAsJsonArray()));
    }

    private static float[] floats(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsFloat();
        }
        return out;
    }
}
