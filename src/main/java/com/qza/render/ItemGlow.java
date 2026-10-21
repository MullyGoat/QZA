package com.qza.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qza.compat.GlowRender;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.custom.CustomItems;
import com.qza.inventory.Equipment;
import com.qza.waypoint.WaypointColour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public final class ItemGlow {
    public static final String AURA = "aura";
    public static final String SHIMMER = "shimmer";
    public static final String FLAME = "flame";
    public static final List<String> STYLES = List.of(AURA, SHIMMER, FLAME);
    public static final String RAINBOW = "rainbow";
    public static final String RARITY = "rarity";
    public static final double MIN_SPEED = 0.25;
    public static final double MAX_SPEED = 3.0;
    public static final double MIN_BRIGHTNESS = 25.0;
    public static final double MAX_BRIGHTNESS = 200.0;

    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float BACK = 0.2f;
    private static final float FRONT = -0.004f;
    private static final float SPARK_FRONT = -0.05f;
    private static final int COPY_BUDGET = 9000;
    private static final int FLAME_CELLS = 140;

    private static final List<Quad> pool = new ArrayList<>();
    private static final List<Quad> shown = new ArrayList<>();
    private static final Vector3f scratch = new Vector3f();
    private static int used;
    private static boolean collecting;
    private static ItemStack held = ItemStack.EMPTY;

    private static Map<Identifier, Mesh> meshes = new LinkedHashMap<>();
    private static float[] grid = new float[0];
    private static float shownArea;
    private static float cx;
    private static float cy;
    private static float cz;
    private static float ax;
    private static float ay;
    private static float az;
    private static float bx;
    private static float by;
    private static float bz;
    private static float size;
    private static float unit;
    private static float time;
    private static float hue;
    private static float strength;
    private static boolean still;
    private static boolean rainbow;
    private static int baseRgb;

    private ItemGlow() {
    }

    public static List<String> colours() {
        List<String> out = new ArrayList<>();
        for (String name : WaypointColour.names()) {
            if (!name.equals("black")) {
                out.add(name);
            }
        }
        out.add(RAINBOW);
        out.add(RARITY);
        return out;
    }

    public static String styleLabel(String style) {
        return switch (style == null ? "" : style) {
            case SHIMMER -> "Shimmer";
            case FLAME -> "Flame";
            default -> "Aura";
        };
    }

    public static void begin(ItemDisplayContext context) {
        if (CustomItems.drawingPart()) {
            return;
        }
        collecting = wanted(context);
        used = 0;
        if (collecting) {
            LocalPlayer player = Minecraft.getInstance().player;
            boolean right = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
            boolean mainHand = player != null && (player.getMainArm() == HumanoidArm.RIGHT) == right;
            held = player == null ? ItemStack.EMPTY : mainHand ? player.getMainHandItem() : player.getOffhandItem();
        }
    }

    public static boolean collecting() {
        return collecting;
    }

    public static void layer(Matrix4f pose, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            Quad q = next();
            for (int v = 0; v < 4; v++) {
                Vector3fc p = quad.position(v);
                pose.transformPosition(p.x(), p.y(), p.z(), scratch);
                q.p[v * 3] = scratch.x;
                q.p[v * 3 + 1] = scratch.y;
                q.p[v * 3 + 2] = scratch.z;
                long uv = quad.packedUV(v);
                q.uv[v * 2] = UVPair.unpackU(uv);
                q.uv[v * 2 + 1] = UVPair.unpackV(uv);
            }
            q.sprite = quad.materialInfo().sprite();
            q.itemType = quad.materialInfo().itemRenderType();
        }
    }

    public static void end(SubmitNodeCollector collector) {
        if (!collecting || CustomItems.drawingPart()) {
            return;
        }
        collecting = false;
        if (used == 0) {
            return;
        }
        try {
            draw(collector);
        } finally {
            used = 0;
        }
    }

    private static boolean wanted(ItemDisplayContext context) {
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            return false;
        }
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.itemGlowEnabled) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        boolean right = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean mainHand = (player.getMainArm() == HumanoidArm.RIGHT) == right;
        return mainHand || cfg.itemGlowOffhand;
    }

    private static Quad next() {
        if (used == pool.size()) {
            pool.add(new Quad());
        }
        return pool.get(used++);
    }

    private static void draw(SubmitNodeCollector collector) {
        QZAConfig cfg = ConfigManager.get();
        frame(cfg);
        meshes = new LinkedHashMap<>();
        switch (cfg.itemGlowStyle == null ? AURA : cfg.itemGlowStyle) {
            case SHIMMER -> shimmer();
            case FLAME -> flame();
            default -> aura();
        }
        List<RenderType> under = new ArrayList<>();
        for (int i = 0; i < used; i++) {
            RenderType type = pool.get(i).itemType;
            if (type != null && !under.contains(type)) {
                under.add(type);
            }
        }
        for (Map.Entry<Identifier, Mesh> entry : meshes.entrySet()) {
            Mesh mesh = entry.getValue();
            if (mesh.count > 0) {
                GlowRender.submit(collector, entry.getKey(), under, mesh::write);
            }
        }
    }

    private static void frame(QZAConfig cfg) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < used; i++) {
            float[] p = pool.get(i).p;
            for (int v = 0; v < 4; v++) {
                minX = Math.min(minX, p[v * 3]);
                minY = Math.min(minY, p[v * 3 + 1]);
                minZ = Math.min(minZ, p[v * 3 + 2]);
                maxX = Math.max(maxX, p[v * 3]);
                maxY = Math.max(maxY, p[v * 3 + 1]);
                maxZ = Math.max(maxZ, p[v * 3 + 2]);
            }
        }
        cx = (minX + maxX) * 0.5f;
        cy = (minY + maxY) * 0.5f;
        cz = (minZ + maxZ) * 0.5f;
        float length = (float) Math.sqrt((cx * cx) + (cy * cy) + (cz * cz));
        float fx = 0f;
        float fy = 0f;
        float fz = -1f;
        if (length > 1.0E-4f) {
            fx = cx / length;
            fy = cy / length;
            fz = cz / length;
        }
        ax = -fz;
        ay = 0f;
        az = fx;
        float across = (float) Math.sqrt((ax * ax) + (az * az));
        if (across < 1.0E-4f) {
            ax = 1f;
            az = 0f;
        } else {
            ax /= across;
            az /= across;
        }
        bx = (ay * fz) - (az * fy);
        by = (az * fx) - (ax * fz);
        bz = (ax * fy) - (ay * fx);

        float lowA = Float.MAX_VALUE;
        float highA = -Float.MAX_VALUE;
        float lowB = Float.MAX_VALUE;
        float highB = -Float.MAX_VALUE;
        for (int i = 0; i < used; i++) {
            float[] p = pool.get(i).p;
            for (int v = 0; v < 4; v++) {
                float dx = p[v * 3] - cx;
                float dy = p[v * 3 + 1] - cy;
                float dz = p[v * 3 + 2] - cz;
                float onA = (dx * ax) + (dy * ay) + (dz * az);
                float onB = (dx * bx) + (dy * by) + (dz * bz);
                lowA = Math.min(lowA, onA);
                highA = Math.max(highA, onA);
                lowB = Math.min(lowB, onB);
                highB = Math.max(highB, onB);
            }
        }
        size = Math.max(1.0E-3f, Math.max(highA - lowA, highB - lowB));
        unit = size / 16f;

        still = cfg.itemGlowStatic;
        double speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, cfg.itemGlowSpeed));
        time = still ? 0f : (float) (((System.currentTimeMillis() % 3_600_000L) / 1000.0) * speed);
        strength = (float) (Math.max(MIN_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, cfg.itemGlowBrightness)) / 100.0);
        rainbow = RAINBOW.equals(cfg.itemGlowColour);
        hue = still ? 0f : frac(time * 0.12f);
        baseRgb = RARITY.equals(cfg.itemGlowColour) ? rarityRgb(held)
                : WaypointColour.argb(cfg.itemGlowColour) & 0xFFFFFF;

        shown.clear();
        shownArea = 0f;
        for (int i = 0; i < used; i++) {
            Quad q = pool.get(i);
            q.area = facingArea(q);
            for (int v = 0; v < 4; v++) {
                float distance = length(q.p[v * 3], q.p[v * 3 + 1], q.p[v * 3 + 2]);
                q.back[v] = distance > 1.0E-5f ? 1f + (BACK / distance) : 1f;
                q.front[v] = distance > 1.0E-5f ? 1f + (FRONT / distance) : 1f;
            }
            if (q.area > 0f) {
                shown.add(q);
                shownArea += q.area;
            }
        }
        if (shown.isEmpty()) {
            for (int i = 0; i < used; i++) {
                shown.add(pool.get(i));
            }
        }
    }

    public static int rarityRgb(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0xFFFFFF;
        }
        int rarity = Equipment.rarityColour(stack);
        if (rarity != 0) {
            return rarity & 0xFFFFFF;
        }
        int[] found = {0xFFFFFF};
        boolean[] done = {false};
        Component name = stack.getHoverName();
        name.visit((style, text) -> {
            TextColor colour = style.getColor();
            if (!done[0] && colour != null && !text.isBlank()) {
                found[0] = colour.getValue();
                done[0] = true;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return found[0];
    }

    private static void aura() {
        float wave = still ? 1f : (float) Math.sin(TAU * time / 2.2f);
        float breathe = still ? 1f : 0.72f + (0.28f * wave);
        float reach = unit * 2.8f * (still ? 1f : 0.92f + (0.08f * wave));
        halo(5, 12, reach, 0.95f * breathe, 0.1f);
        tint(0.10f * breathe, 0f);
    }

    private static void shimmer() {
        halo(3, 10, unit * 1.4f, 0.6f, 0.05f);
        float centre;
        if (still) {
            centre = -0.15f;
        } else {
            float phase = frac(time / 2.4f);
            centre = phase < 0.65f ? -1.1f + (phase / 0.65f * 2.2f) : 9f;
        }
        band(centre, 0.1f, 1.0f);
        sparkles(5);
        tint(0.05f, 0f);
    }

    private static void flame() {
        halo(2, 8, unit * 0.9f, 0.45f, 0.04f);
        int layers = Math.max(4, Math.min(12, (COPY_BUDGET * 2) / Math.max(1, shown.size() * 8)));
        flameCells();
        float sum = 0f;
        float[] weight = new float[layers];
        for (int k = 0; k < layers; k++) {
            weight[k] = (float) Math.pow(1f - (k / (float) layers), 0.8);
            sum += weight[k];
        }
        float scale = 0.85f / sum;
        for (int k = 0; k < layers; k++) {
            float f = (k + 1) / (float) layers;
            float height = unit * 9f * (float) Math.pow(f, 1.15);
            int hot = whiten(colour(0.04f * k), 0.4f * (1f - f) * (1f - f));
            float glow = weight[k] * scale * strength * (still ? 1f : 0.85f + (0.15f * wobble(time * 1.3f, k)));
            flameLayer(k, height, shade(hot, glow));
        }
        embers(6);
        tint(0.07f, 0f);
    }

    private static void flameCells() {
        float cell = unit * 2f;
        for (int pass = 0; pass < 2; pass++) {
            int total = 0;
            for (Quad q : shown) {
                float[] p = q.p;
                q.cellsS = Math.max(1, Math.min(8, (int) Math.ceil(length(p[3] - p[0], p[4] - p[1], p[5] - p[2]) / cell)));
                q.cellsT = Math.max(1, Math.min(8, (int) Math.ceil(length(p[9] - p[0], p[10] - p[1], p[11] - p[2]) / cell)));
                total += q.cellsS * q.cellsT;
            }
            if (total <= FLAME_CELLS) {
                return;
            }
            cell *= (float) Math.sqrt(total / (double) FLAME_CELLS);
        }
    }

    private static void flameLayer(int k, float height, int colour) {
        for (Quad q : shown) {
            int n = q.cellsS;
            int m = q.cellsT;
            int stride = n + 1;
            int points = stride * (m + 1);
            if (grid.length < points * 5) {
                grid = new float[points * 5];
            }
            for (int ti = 0; ti <= m; ti++) {
                for (int si = 0; si <= n; si++) {
                    float s0 = si / (float) n;
                    float t0 = ti / (float) m;
                    int at = ((ti * stride) + si) * 5;
                    point(q, s0, t0, grid, at);
                    float x = grid[at];
                    float y = grid[at + 1];
                    float z = grid[at + 2];
                    float side = (((x - cx) * ax) + ((y - cy) * ay) + ((z - cz) * az)) / size;
                    float lift = height * tongue(side, k);
                    float sway = still ? 0f : height * 0.18f * Mth.sin((time * 2.3f) + (k * 0.9f) + (side * 6f));
                    float push = bilinear(q.back, s0, t0);
                    grid[at] = (x + (bx * lift) + (ax * sway)) * push;
                    grid[at + 1] = (y + (by * lift) + (ay * sway)) * push;
                    grid[at + 2] = (z + (bz * lift) + (az * sway)) * push;
                }
            }
            Mesh mesh = mesh(q);
            for (int ti = 0; ti < m; ti++) {
                for (int si = 0; si < n; si++) {
                    int a = (ti * stride) + si;
                    gridVertex(mesh, a, colour);
                    gridVertex(mesh, a + 1, colour);
                    gridVertex(mesh, a + stride + 1, colour);
                    gridVertex(mesh, a + stride, colour);
                }
            }
        }
    }

    private static void gridVertex(Mesh mesh, int index, int colour) {
        int at = index * 5;
        mesh.vertex(grid[at], grid[at + 1], grid[at + 2], grid[at + 3], grid[at + 4], colour);
    }

    private static float bilinear(float[] corner, float s, float t) {
        return (corner[0] * (1f - s) * (1f - t)) + (corner[1] * s * (1f - t)) + (corner[2] * s * t)
                + (corner[3] * (1f - s) * t);
    }

    private static float tongue(float side, int k) {
        if (still) {
            return 0.75f + (0.25f * Mth.sin((side * 9f) + (k * 0.6f)));
        }
        float wave = (0.6f * Mth.sin((side * 10f) + (time * 5.1f) + (k * 0.8f)))
                + (0.4f * Mth.sin((side * 4.3f) - (time * 3.3f) + (k * 1.9f)));
        return 0.55f + (0.45f * wave);
    }

    private static float wobble(float t, int k) {
        return (float) ((0.5 * Math.sin((t * 7.3) + (k * 1.3)))
                + (0.3 * Math.sin((t * 12.7) + (k * 2.9)))
                + (0.2 * Math.sin((t * 3.1) + (k * 0.7))));
    }

    private static void embers(int count) {
        if (shownArea <= 0f) {
            return;
        }
        float[] spot = new float[5];
        for (int i = 0; i < count; i++) {
            long slot = mix((i + 11) * 0x9E3779B97F4A7C15L);
            float life = 1.2f + (0.7f * unitFloat(slot));
            double cycle = still ? 0.0 : (time / life) + unitFloat(slot >>> 17);
            long round = (long) Math.floor(cycle);
            float phase = still ? 0.25f + (0.12f * i) : (float) (cycle - round);
            float fade = (float) Math.sin(Math.PI * Math.min(1f, phase * 1.15f));
            if (fade < 0.02f) {
                continue;
            }
            Random random = new Random(mix(slot ^ (round * 0x632BE59BD9B4E019L)));
            Quad on = spotOn(random, spot);
            if (on == null) {
                continue;
            }
            float rise = unit * 11f * phase;
            float drift = unit * 1.5f * (float) Math.sin((phase * 5f) + i);
            spot[0] += (bx * rise) + (ax * drift);
            spot[1] += (by * rise) + (ay * drift);
            spot[2] += (bz * rise) + (az * drift);
            ember(on, spot, unit * (0.9f + (0.5f * fade)), fade);
        }
    }

    private static void ember(Quad q, float[] spot, float reach, float brightness) {
        Mesh mesh = mesh(q);
        float k = pushed(spot[0], spot[1], spot[2], SPARK_FRONT);
        float x = spot[0] * k;
        float y = spot[1] * k;
        float z = spot[2] * k;
        int core = shade(whiten(colour(0f), 0.6f), brightness * strength);
        int side = shade(colour(0f), brightness * strength * 0.6f);
        arm(mesh, x, y, z, spot[3], spot[4], TAU / 4f, reach, reach * 0.3f, core, side);
        arm(mesh, x, y, z, spot[3], spot[4], TAU * 3f / 4f, reach * 0.45f, reach * 0.3f, core, side);
        arm(mesh, x, y, z, spot[3], spot[4], 0f, reach * 0.35f, reach * 0.25f, core, side);
        arm(mesh, x, y, z, spot[3], spot[4], TAU / 2f, reach * 0.35f, reach * 0.25f, core, side);
    }

    private static void halo(int rings, int dirs, float reach, float edge, float hueStep) {
        int[] density = density(rings, dirs);
        rings = density[0];
        dirs = density[1];
        float[] weight = new float[rings];
        float sum = 0f;
        for (int k = 0; k < rings; k++) {
            weight[k] = (float) Math.pow(1f - (k / (float) rings), 1.6);
            sum += weight[k];
        }
        float scale = edge / (sum * dirs * 0.5f);
        for (int k = 0; k < rings; k++) {
            float radius = reach * (k + 1) / rings;
            int colour = shade(colour(hueStep * k), weight[k] * scale * strength);
            float turn = (k & 1) * 0.5f;
            for (int j = 0; j < dirs; j++) {
                offsetCopies(radius, TAU * (j + turn) / dirs, colour);
            }
        }
    }

    private static int[] density(int rings, int dirs) {
        int allowed = Math.max(8, COPY_BUDGET / Math.max(1, shown.size()));
        while (rings * dirs > allowed && dirs > 6) {
            dirs -= 2;
        }
        while (rings * dirs > allowed && rings > 1) {
            rings--;
        }
        return new int[]{rings, dirs};
    }

    private static void offsetCopies(float radius, float angle, int colour) {
        float cos = (float) Math.cos(angle) * radius;
        float sin = (float) Math.sin(angle) * radius;
        float ox = (ax * cos) + (bx * sin);
        float oy = (ay * cos) + (by * sin);
        float oz = (az * cos) + (bz * sin);
        for (Quad q : shown) {
            copy(q, ox, oy, oz, q.back, colour);
        }
    }

    private static void tint(float brightness, float hueShift) {
        if (brightness <= 0.002f) {
            return;
        }
        int colour = shade(colour(hueShift), brightness * strength);
        for (Quad q : shown) {
            copy(q, 0f, 0f, 0f, q.front, colour);
        }
    }

    private static void copy(Quad q, float ox, float oy, float oz, float[] push, int colour) {
        Mesh mesh = mesh(q);
        for (int v = 0; v < 4; v++) {
            float k = push[v];
            mesh.vertex((q.p[v * 3] + ox) * k, (q.p[v * 3 + 1] + oy) * k, (q.p[v * 3 + 2] + oz) * k,
                    q.uv[v * 2], q.uv[v * 2 + 1], colour);
        }
    }

    private static void band(float centre, float width, float peak) {
        int light = whiten(colour(0f), 0.45f);
        float[] corner = new float[20];
        int[] colours = new int[4];
        for (Quad q : shown) {
            float[] p = q.p;
            float lenS = length(p[3] - p[0], p[4] - p[1], p[5] - p[2]);
            float lenT = length(p[9] - p[0], p[10] - p[1], p[11] - p[2]);
            int n = Math.max(1, Math.min(32, (int) Math.ceil(lenS / unit)));
            int m = Math.max(1, Math.min(32, (int) Math.ceil(lenT / unit)));
            float low = Float.MAX_VALUE;
            float high = -Float.MAX_VALUE;
            for (int v = 0; v < 4; v++) {
                float at = across(p[v * 3], p[v * 3 + 1], p[v * 3 + 2]);
                low = Math.min(low, at);
                high = Math.max(high, at);
            }
            if (high < centre - (width * 3f) || low > centre + (width * 3f)) {
                continue;
            }
            Mesh mesh = mesh(q);
            for (int si = 0; si < n; si++) {
                for (int ti = 0; ti < m; ti++) {
                    float s0 = si / (float) n;
                    float s1 = (si + 1) / (float) n;
                    float t0 = ti / (float) m;
                    float t1 = (ti + 1) / (float) m;
                    point(q, s0, t0, corner, 0);
                    point(q, s1, t0, corner, 5);
                    point(q, s1, t1, corner, 10);
                    point(q, s0, t1, corner, 15);
                    float brightest = 0f;
                    for (int v = 0; v < 4; v++) {
                        float at = across(corner[v * 5], corner[v * 5 + 1], corner[v * 5 + 2]);
                        float d = (at - centre) / width;
                        float glow = (float) Math.exp(-(d * d)) * peak;
                        brightest = Math.max(brightest, glow);
                        int base = rainbow ? whiten(colour(at * 0.35f), 0.45f) : light;
                        colours[v] = shade(base, glow * strength);
                    }
                    if (brightest < 0.004f) {
                        continue;
                    }
                    for (int v = 0; v < 4; v++) {
                        float x = corner[v * 5];
                        float y = corner[v * 5 + 1];
                        float z = corner[v * 5 + 2];
                        float k = pushed(x, y, z, FRONT);
                        mesh.vertex(x * k, y * k, z * k, corner[v * 5 + 3], corner[v * 5 + 4], colours[v]);
                    }
                }
            }
        }
    }

    private static void sparkles(int count) {
        if (shownArea <= 0f) {
            return;
        }
        float[] spot = new float[5];
        for (int i = 0; i < count; i++) {
            long slot = mix((i + 1) * 0x9E3779B97F4A7C15L);
            float life = 1.1f + (0.6f * unitFloat(slot));
            float shift = unitFloat(slot >>> 17);
            double cycle = still ? 0.0 : (time / life) + shift;
            long round = (long) Math.floor(cycle);
            float phase = (float) (cycle - round);
            float twinkle = still ? 0.8f : (float) Math.pow(Math.sin(Math.PI * phase), 2);
            if (twinkle < 0.02f) {
                continue;
            }
            Random random = new Random(mix(slot ^ (round * 0x632BE59BD9B4E019L)));
            Quad on = spotOn(random, spot);
            if (on != null) {
                star(on, spot, unit * (0.7f + (1.1f * twinkle)), twinkle);
            }
        }
    }

    private static Quad spotOn(Random random, float[] spot) {
        for (int tries = 0; tries < 12; tries++) {
            float pick = random.nextFloat() * shownArea;
            int index = 0;
            while (index < shown.size() - 1 && pick > shown.get(index).area) {
                pick -= shown.get(index).area;
                index++;
            }
            Quad q = shown.get(index);
            point(q, random.nextFloat(), random.nextFloat(), spot, 0);
            if (opaque(q.sprite, spot[3], spot[4])) {
                return q;
            }
        }
        return null;
    }

    private static boolean opaque(TextureAtlasSprite sprite, float u, float v) {
        if (sprite == null) {
            return false;
        }
        SpriteContents contents = sprite.contents();
        float spanU = sprite.getU1() - sprite.getU0();
        float spanV = sprite.getV1() - sprite.getV0();
        if (spanU == 0f || spanV == 0f) {
            return false;
        }
        int x = (int) Math.floor((u - sprite.getU0()) / spanU * contents.width());
        int y = (int) Math.floor((v - sprite.getV0()) / spanV * contents.height());
        x = Math.max(0, Math.min(contents.width() - 1, x));
        y = Math.max(0, Math.min(contents.height() - 1, y));
        return !contents.isTransparent(0, x, y);
    }

    private static void star(Quad q, float[] spot, float reach, float brightness) {
        Mesh mesh = mesh(q);
        float k = pushed(spot[0], spot[1], spot[2], SPARK_FRONT);
        float x = spot[0] * k;
        float y = spot[1] * k;
        float z = spot[2] * k;
        float u = spot[3];
        float v = spot[4];
        int core = shade(whiten(colour(0f), 0.65f), brightness * strength);
        int side = shade(colour(0f), brightness * strength * 0.55f);
        int faint = shade(whiten(colour(0f), 0.4f), brightness * strength * 0.4f);
        int dim = shade(colour(0f), brightness * strength * 0.3f);
        for (int i = 0; i < 4; i++) {
            float angle = TAU * i / 4f;
            arm(mesh, x, y, z, u, v, angle, reach, reach * 0.14f, core, side);
            arm(mesh, x, y, z, u, v, angle + (TAU / 8f), reach * 0.4f, reach * 0.09f, faint, dim);
        }
    }

    private static void arm(Mesh mesh, float x, float y, float z, float u, float v, float angle,
                            float reach, float width, int core, int side) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float dx = (ax * cos) + (bx * sin);
        float dy = (ay * cos) + (by * sin);
        float dz = (az * cos) + (bz * sin);
        float px = (bx * cos) - (ax * sin);
        float py = (by * cos) - (ay * sin);
        float pz = (bz * cos) - (az * sin);
        float near = reach * 0.22f;
        mesh.vertex(x, y, z, u, v, core);
        mesh.vertex(x + (dx * near) + (px * width), y + (dy * near) + (py * width), z + (dz * near) + (pz * width), u, v, side);
        mesh.vertex(x + (dx * reach), y + (dy * reach), z + (dz * reach), u, v, 0xFF000000);
        mesh.vertex(x + (dx * near) - (px * width), y + (dy * near) - (py * width), z + (dz * near) - (pz * width), u, v, side);
    }

    private static float facingArea(Quad q) {
        float[] p = q.p;
        float ex = p[3] - p[0];
        float ey = p[4] - p[1];
        float ez = p[5] - p[2];
        float fx = p[9] - p[0];
        float fy = p[10] - p[1];
        float fz = p[11] - p[2];
        float nx = (ey * fz) - (ez * fy);
        float ny = (ez * fx) - (ex * fz);
        float nz = (ex * fy) - (ey * fx);
        float mx = (p[0] + p[3] + p[6] + p[9]) * 0.25f;
        float my = (p[1] + p[4] + p[7] + p[10]) * 0.25f;
        float mz = (p[2] + p[5] + p[8] + p[11]) * 0.25f;
        if ((nx * mx) + (ny * my) + (nz * mz) >= 0f) {
            return 0f;
        }
        return length(nx, ny, nz);
    }

    private static void point(Quad q, float s, float t, float[] out, int at) {
        float w0 = (1f - s) * (1f - t);
        float w1 = s * (1f - t);
        float w2 = s * t;
        float w3 = (1f - s) * t;
        float[] p = q.p;
        float[] uv = q.uv;
        out[at] = (p[0] * w0) + (p[3] * w1) + (p[6] * w2) + (p[9] * w3);
        out[at + 1] = (p[1] * w0) + (p[4] * w1) + (p[7] * w2) + (p[10] * w3);
        out[at + 2] = (p[2] * w0) + (p[5] * w1) + (p[8] * w2) + (p[11] * w3);
        out[at + 3] = (uv[0] * w0) + (uv[2] * w1) + (uv[4] * w2) + (uv[6] * w3);
        out[at + 4] = (uv[1] * w0) + (uv[3] * w1) + (uv[5] * w2) + (uv[7] * w3);
    }

    private static float across(float x, float y, float z) {
        float dx = x - cx;
        float dy = y - cy;
        float dz = z - cz;
        float onA = (dx * ax) + (dy * ay) + (dz * az);
        float onB = (dx * bx) + (dy * by) + (dz * bz);
        return (onA + (0.55f * onB)) / size;
    }

    private static float pushed(float x, float y, float z, float push) {
        float length = length(x, y, z);
        return length > 1.0E-5f ? 1f + (push / length) : 1f;
    }

    private static float length(float x, float y, float z) {
        return (float) Math.sqrt((x * x) + (y * y) + (z * z));
    }

    private static Mesh mesh(Quad q) {
        Identifier atlas = q.sprite.atlasLocation();
        return meshes.computeIfAbsent(atlas, id -> new Mesh());
    }

    private static int colour(float hueShift) {
        return rainbow ? hsv(frac(hue + hueShift), 0.8f, 1f) : baseRgb;
    }

    private static int shade(int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * k));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * k));
        int b = Math.min(255, Math.round((rgb & 0xFF) * k));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int whiten(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Math.round(r + ((255 - r) * amount));
        g = Math.round(g + ((255 - g) * amount));
        b = Math.round(b + ((255 - b) * amount));
        return (r << 16) | (g << 8) | b;
    }

    private static int hsv(float h, float s, float v) {
        float scaled = h * 6f;
        int sector = (int) Math.floor(scaled) % 6;
        float f = scaled - (float) Math.floor(scaled);
        float p = v * (1f - s);
        float q = v * (1f - (f * s));
        float t = v * (1f - ((1f - f) * s));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    private static float frac(float value) {
        return value - (float) Math.floor(value);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 33)) * 0xFF51AFD7ED558CCDL;
        value = (value ^ (value >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return value ^ (value >>> 33);
    }

    private static float unitFloat(long value) {
        return ((value >>> 40) & 0xFFFFFF) / (float) 0x1000000;
    }

    private static final class Quad {
        private final float[] p = new float[12];
        private final float[] uv = new float[8];
        private TextureAtlasSprite sprite;
        private RenderType itemType;
        private final float[] back = new float[4];
        private final float[] front = new float[4];
        private float area;
        private int cellsS;
        private int cellsT;
    }

    private static final class Mesh {
        private float[] xyz = new float[3 * 512];
        private float[] uv = new float[2 * 512];
        private int[] argb = new int[512];
        private int count;

        void vertex(float x, float y, float z, float u, float v, int colour) {
            if (count == argb.length) {
                int grown = argb.length * 2;
                xyz = Arrays.copyOf(xyz, grown * 3);
                uv = Arrays.copyOf(uv, grown * 2);
                argb = Arrays.copyOf(argb, grown);
            }
            xyz[count * 3] = x;
            xyz[count * 3 + 1] = y;
            xyz[count * 3 + 2] = z;
            uv[count * 2] = u;
            uv[count * 2 + 1] = v;
            argb[count] = colour;
            count++;
        }

        void write(VertexConsumer out) {
            for (int i = 0; i < count; i++) {
                out.addVertex(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                        .setUv(uv[i * 2], uv[i * 2 + 1])
                        .setColor(argb[i]);
            }
        }
    }
}
