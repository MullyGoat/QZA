package com.qza.custom.knife;

import com.mojang.blaze3d.platform.NativeImage;
import com.qza.QZA;
import com.qza.compat.KnifeTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class KnifeTextures {
    private static final int KEEP = 48;
    private static final long PAINT_GAP = 35_000_000L;

    private static final Map<String, Identifier> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static long paintedAt;

    private KnifeTextures() {
    }

    public static Identifier request(String knife, String finish, int seed, String wear) {
        Identifier id = CACHE.get(key(knife, finish, seed, wear));
        if (id != null || Util.getNanos() - paintedAt < PAINT_GAP) {
            return id;
        }
        return paint(knife, finish, seed, wear);
    }

    public static Identifier texture(String knife, String finish, int seed, String wear, boolean urgent) {
        Identifier id = CACHE.get(key(knife, finish, seed, wear));
        if (id != null) {
            return id;
        }
        id = urgent ? paint(knife, finish, seed, wear) : request(knife, finish, seed, wear);
        return id != null ? id : fallback(knife);
    }

    private static String key(String knife, String finish, int seed, String wear) {
        return knife + "/" + finish + "/" + seed + "/" + wear;
    }

    private static Identifier paint(String knife, String finish, int seed, String wear) {
        String key = key(knife, finish, seed, wear);
        Identifier id;
        Knives.Knife type = Knives.get(knife);
        Knives.Shape shape = Knives.shape(knife);
        if (type == null || shape == null) {
            return null;
        }
        paintedAt = Util.getNanos();
        try {
            NativeImage image = KnifeFinish.paint(type, finish, seed, wear, shape.bladeLength());
            id = Identifier.fromNamespaceAndPath(QZA.MOD_ID, "knife/" + sanitise(key));
            Identifier label = id;
            Minecraft.getInstance().getTextureManager().register(id, new KnifeTexture(label::toString, image));
            CACHE.put(key, id);
            trim();
            return id;
        } catch (Exception e) {
            QZA.LOGGER.warn("QZA couldn't make the {} knife texture", key, e);
            return null;
        }
    }

    private static Identifier fallback(String knife) {
        String prefix = knife + "/";
        for (Map.Entry<String, Identifier> entry : CACHE.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                return entry.getValue();
            }
        }
        return CACHE.isEmpty() ? null : CACHE.values().iterator().next();
    }

    private static void trim() {
        Iterator<Map.Entry<String, Identifier>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > KEEP && it.hasNext()) {
            Identifier id = it.next().getValue();
            it.remove();
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    public static void clear() {
        for (Identifier id : CACHE.values()) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
        CACHE.clear();
    }

    private static String sanitise(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }
}
