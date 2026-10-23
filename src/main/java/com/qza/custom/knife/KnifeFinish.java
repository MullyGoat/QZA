package com.qza.custom.knife;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KnifeFinish {
    public record Finish(String id, String label, boolean paint, boolean coversHandle) {
    }

    public static final List<Finish> ALL = List.of(
            new Finish("vanilla", "Vanilla", false, false),
            new Finish("fade", "Fade", false, false),
            new Finish("doppler", "Doppler", false, false),
            new Finish("ruby", "Doppler Ruby", false, false),
            new Finish("sapphire", "Doppler Sapphire", false, false),
            new Finish("black_pearl", "Doppler Black Pearl", false, false),
            new Finish("gamma_doppler", "Gamma Doppler", false, false),
            new Finish("emerald", "Gamma Doppler Emerald", false, false),
            new Finish("marble_fade", "Marble Fade", false, false),
            new Finish("tiger_tooth", "Tiger Tooth", false, false),
            new Finish("case_hardened", "Case Hardened", false, false),
            new Finish("crimson_web", "Crimson Web", true, false),
            new Finish("slaughter", "Slaughter", false, false),
            new Finish("damascus", "Damascus Steel", false, false),
            new Finish("ultraviolet", "Ultraviolet", true, true),
            new Finish("night", "Night", true, true),
            new Finish("blue_steel", "Blue Steel", false, false),
            new Finish("rust_coat", "Rust Coat", false, false),
            new Finish("stained", "Stained", false, false),
            new Finish("scorched", "Scorched", true, true),
            new Finish("safari_mesh", "Safari Mesh", true, true),
            new Finish("boreal_forest", "Boreal Forest", true, true),
            new Finish("forest_ddpat", "Forest DDPAT", true, true),
            new Finish("urban_masked", "Urban Masked", true, true),
            new Finish("bright_water", "Bright Water", true, false),
            new Finish("autotronic", "Autotronic", true, false),
            new Finish("amethyst", "Amethyst", false, false),
            new Finish("obsidian", "Obsidian", false, false),
            new Finish("snowfall", "Snowfall", true, false));

    public static final List<String> WEARS = List.of("fn", "mw", "ft", "ww", "bs");

    private static final Map<String, Finish> BY_ID = new LinkedHashMap<>();

    static {
        for (Finish finish : ALL) {
            BY_ID.put(finish.id(), finish);
        }
    }

    private KnifeFinish() {
    }

    public static Finish get(String id) {
        Finish finish = id == null ? null : BY_ID.get(id);
        return finish == null ? ALL.get(0) : finish;
    }

    public static boolean is(String id) {
        return BY_ID.containsKey(id);
    }

    public static List<String> ids() {
        return List.copyOf(BY_ID.keySet());
    }

    public static float wearAmount(String wear) {
        return switch (wear == null ? "fn" : wear) {
            case "mw" -> 0.1f;
            case "ft" -> 0.25f;
            case "ww" -> 0.42f;
            case "bs" -> 0.7f;
            default -> 0.02f;
        };
    }

    public static String phase(String finish, int seed) {
        if ("doppler".equals(finish) || "gamma_doppler".equals(finish)) {
            return "Phase " + (Math.floorMod(seed, 4) + 1);
        }
        if ("case_hardened".equals(finish) && blueGem(seed)) {
            return "Blue Gem";
        }
        if ("marble_fade".equals(finish) && fireAndIce(seed)) {
            return "Fire & Ice";
        }
        return "";
    }

    private static boolean blueGem(int seed) {
        return Math.floorMod(seed * 2654435761L, 1000L) < 25;
    }

    private static boolean fireAndIce(int seed) {
        return Math.floorMod(seed * 40503L + 7L, 100L) < 12;
    }

    private static final class Px {
        float r;
        float g;
        float b;
        float sr;
        float sg;
        float sb;
        boolean paint;

        void set(float r, float g, float b, float sr, float sg, float sb) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.sr = sr;
            this.sg = sg;
            this.sb = sb;
        }

        void metal(float r, float g, float b, float albedo, float shine) {
            set(r * albedo, g * albedo, b * albedo, r * shine + 0.04f, g * shine + 0.04f, b * shine + 0.04f);
        }

        void paint(float r, float g, float b, float gloss) {
            set(r, g, b, gloss, gloss, gloss);
            paint = true;
        }
    }

    public static NativeImage paint(Knives.Knife knife, String finishId, int seed, String wearId, float bladeLength) {
        NativeImage image = new NativeImage(KnifeMesh.TEX_W, KnifeMesh.TEX_H, false);
        Finish finish = get(finishId);
        float wear = wearAmount(wearId);
        Px px = new Px();
        for (int region = 0; region < KnifeMesh.REGIONS.length; region++) {
            int[] r = KnifeMesh.REGIONS[region];
            for (int y = 0; y < r[3]; y++) {
                for (int x = 0; x < r[2]; x++) {
                    float s = (x + 0.5f) / r[2];
                    float w = (y + 0.5f) / r[3];
                    px.paint = false;
                    sample(knife, finish, seed, wear, bladeLength, region, s, w, px);
                    image.setPixel(r[0] + x, r[1] + y, argb(px.r, px.g, px.b));
                    image.setPixel(r[0] + x, r[1] + y + KnifeMesh.HALF, argb(px.sr, px.sg, px.sb));
                }
            }
        }
        return image;
    }

    private static void sample(Knives.Knife knife, Finish finish, int seed, float wear, float bladeLength, int region,
                               float s, float w, Px px) {
        int salt = seed * 31 + region * 977;
        switch (region) {
            case KnifeMesh.BLADE_FRONT, KnifeMesh.BLADE_BACK -> {
                float x = s * bladeLength;
                float y = w * 1.8f + (region == KnifeMesh.BLADE_BACK ? 7.3f : 0f);
                finish(finish, seed, x, y, s, w, px);
                worn(finish, seed + region * 13, wear, x, y, s, w, px, true);
            }
            case KnifeMesh.FINISHED -> {
                float x = s * 10f;
                float y = w * 2.6f + 3.1f;
                finish(finish, seed, x, y, s * 0.85f, 0.5f, px);
                worn(finish, seed + 5, wear, x, y, s, 0.5f, px, false);
                if (knife.kind() == Knives.Kind.BUTTERFLY && hole(s, w)) {
                    px.set(0.015f, 0.015f, 0.018f, 0f, 0f, 0f);
                }
            }
            default -> {
                String material = region == KnifeMesh.GRIP ? knife.grip()
                        : region == KnifeMesh.METAL ? knife.metal() : knife.accent();
                float x = s * (region == KnifeMesh.GRIP ? 6f : 2.5f);
                float y = w * 3.2f;
                if (finish.coversHandle() && !material.startsWith("paracord")) {
                    finish(finish, seed, x + 11f, y + 5f, s, 0.5f, px);
                    worn(finish, seed + 9, wear, x, y, s, 0.5f, px, false);
                } else {
                    material(material, salt, x, y, s, w, px);
                    if (material.equals("steel") || material.equals("brass")) {
                        worn(get("vanilla"), seed + 11, wear, x, y, s, 0.5f, px, false);
                    }
                }
            }
        }
    }

    private static boolean hole(float s, float w) {
        float around = Math.min(Math.abs(w - 0.25f), Math.abs(w - 0.75f));
        if (around > 0.11f || s < 0.1f || s > 0.88f) {
            return false;
        }
        float cell = (s - 0.1f) / 0.078f;
        float f = cell - (float) Math.floor(cell) - 0.5f;
        return f * f / 0.09f + around * around / 0.0081f < 1f;
    }

    private static void steel(float x, float y, float s, float w, boolean blade, Px px) {
        float brush = noise(x * 0.35f, y * 26f, 404) * 0.5f + 0.5f;
        float bevel = blade && w > 0.9f ? 1f : 0f;
        float grind = blade && w > 0.44f ? 0.03f * (noise(x * 2f + y * 9f, y * 3f, 405) * 0.5f + 0.5f) : 0f;
        float base = 0.19f + 0.05f * brush + bevel * 0.1f + grind;
        float shine = 0.7f + 0.14f * brush + bevel * 0.18f;
        px.set(base, base * 1.02f, base * 1.06f, shine * 0.95f, shine * 0.97f, shine);
    }

    private static void finish(Finish finish, int seed, float x, float y, float s, float w, Px px) {
        int a = seed * 7919 + 13;
        switch (finish.id()) {
            case "fade" -> {
                float shift = (hash(seed, 3, 91) - 0.5f) * 0.18f;
                float t = s * 0.95f + (0.5f - w) * 0.12f + shift + 0.04f * noise(x * 0.4f, y * 0.6f, a);
                float[] c = ramp(t, 0.0f, 0xF5C42A, 0.22f, 0xF7A23A, 0.42f, 0xF2459A, 0.66f, 0xB43BE0, 0.9f, 0x5B48F0);
                px.metal(c[0], c[1], c[2], 0.42f, 0.8f);
            }
            case "doppler" -> doppler(Math.floorMod(seed, 4), x, y, a, px);
            case "ruby" -> gem(x, y, a, 0x9E0718, 0xE0142C, 0x2A0208, px);
            case "sapphire" -> gem(x, y, a, 0x0B2AB8, 0x1E5CFF, 0x030A2E, px);
            case "black_pearl" -> gem(x, y, a, 0x2A1450, 0x3C5AD8, 0x07040E, px);
            case "gamma_doppler" -> gamma(Math.floorMod(seed, 4), x, y, a, px);
            case "emerald" -> gem(x, y, a, 0x08A040, 0x2EF070, 0x01200C, px);
            case "amethyst" -> {
                float n = swirl(x, y, a);
                float[] c = ramp(n, 0f, 0x3A0C5C, 0.35f, 0xA055E8, 0.6f, 0xF06AC8, 0.82f, 0xD8B0FF, 1f, 0xFFFFFF);
                px.metal(c[0], c[1], c[2], 0.36f, 0.92f);
            }
            case "marble_fade" -> {
                float band = (float) Math.sin(x * 1.35f + 2.6f * fbm(x * 0.45f, y * 1.1f, a, 4) * 3f + y * 0.5f);
                float t = band * 0.5f + 0.5f;
                float[] c = fireAndIce(seed)
                        ? ramp(t, 0f, 0x1A3CF0, 0.45f, 0x5A7BFF, 0.55f, 0xF8F0FF, 0.62f, 0xFF3A2A, 1f, 0xD0100C)
                        : ramp(t, 0f, 0x1638E8, 0.3f, 0xF8E048, 0.5f, 0xFFF6C8, 0.68f, 0xF03018, 1f, 0xB80E10);
                px.metal(c[0], c[1], c[2], 0.4f, 0.86f);
            }
            case "tiger_tooth" -> {
                float f = x * 1.25f + 0.9f * fbm(x * 0.5f, y * 0.8f, a, 3) * 2f + y * 0.45f;
                float stripe = f - (float) Math.floor(f);
                float width = 0.34f - 0.22f * w;
                float gold = 0.88f + 0.12f * w;
                if (stripe < width) {
                    px.metal(0.32f, 0.12f, 0.03f, 0.55f, 0.55f);
                } else {
                    px.metal(1f * gold, 0.66f * gold, 0.14f, 0.45f, 0.85f);
                }
            }
            case "case_hardened" -> {
                boolean gem = blueGem(seed);
                float n1 = fbm(x * 0.65f + a * 0.001f, y * 0.85f, a, 4);
                float n2 = fbm(x * 1.3f, y * 1.5f + 3f, a + 1, 4);
                float[] c = {0.56f, 0.57f, 0.6f};
                float blue = gem ? smooth(0.25f, 0.4f, n1) : smooth(0.52f, 0.6f, n1);
                c = mix(c, rgb(0x1E3FB8), blue);
                c = mix(c, rgb(0x5A2E8A), smooth(0.44f, 0.5f, n1) * (1f - blue) * 0.8f);
                c = mix(c, rgb(0xC89B3C), gem ? 0f : smooth(0.58f, 0.66f, n2) * (1f - blue * 0.6f));
                c = mix(c, rgb(0x8A5A2C), gem ? 0f : smooth(0.5f, 0.56f, n2) * 0.35f * (1f - blue));
                px.metal(c[0], c[1], c[2], 0.4f, 0.8f);
            }
            case "crimson_web" -> {
                float cx = 3.2f + hash(seed, 1, 5) * 3f;
                float cy = 0.9f + hash(seed, 2, 5) * 0.4f;
                float dx = x - cx;
                float dy = (y - cy) * 1.6f;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                float ang = (float) Math.atan2(dy, dx);
                float spokes = 9f;
                float spoke = Math.abs(((ang / (float) (Math.PI * 2) * spokes) % 1f + 1f) % 1f - 0.5f) * 2f;
                float sag = 0.18f * (1f - spoke * spoke);
                float ringF = (r + sag) / 0.95f;
                float ring = Math.abs(ringF - (float) Math.round(ringF));
                boolean line = (spoke > 0.93f && r < 6.5f) || (ring < 0.05f && r < 6.5f && r > 0.2f);
                if (line) {
                    px.paint(0.02f, 0.015f, 0.015f, 0.08f);
                } else {
                    float d = 0.85f + 0.15f * fbm(x * 1.6f, y * 2f, a, 3);
                    px.paint(0.62f * d, 0.03f * d, 0.04f * d, 0.14f);
                }
            }
            case "slaughter" -> {
                float cell = cellular(x * 0.9f, y * 1.6f, a);
                float n = fbm(x * 1.2f, y * 1.4f, a + 3, 3);
                float light = smooth(0.42f, 0.5f, cell + n * 0.25f);
                float[] c = mix(rgb(0xB8161A), rgb(0xF2A0A0), light);
                c = mix(c, rgb(0x5A0508), smooth(0.2f, 0.12f, cell) * 0.6f);
                px.metal(c[0], c[1], c[2], 0.45f, 0.78f);
            }
            case "damascus" -> {
                float f = (float) Math.sin((y * 3.4f + 1.3f * fbm(x * 0.42f, y * 0.8f, a, 4) * 2f) * 5f + x * 0.6f);
                float t = f * 0.5f + 0.5f;
                float base = 0.15f + 0.22f * t;
                float shine = 0.5f + 0.35f * t;
                px.set(base, base, base * 1.04f, shine, shine, shine * 1.02f);
            }
            case "ultraviolet" -> {
                float d = 0.85f + 0.2f * fbm(x * 1.4f, y * 1.6f, a, 3);
                px.paint(0.25f * d, 0.1f * d, 0.38f * d, 0.1f);
            }
            case "night" -> {
                float d = 0.8f + 0.35f * fbm(x * 1.1f, y * 1.3f, a, 3);
                px.paint(0.075f * d, 0.08f * d, 0.09f * d, 0.12f);
            }
            case "blue_steel" -> {
                float t = w * 0.55f + 0.45f * fbm(x * 0.35f, y * 0.9f, a, 4);
                float[] c = ramp(t, 0.1f, 0xD4AE62, 0.3f, 0x9A6A3C, 0.45f, 0x6A3E8A, 0.62f, 0x2E52C2, 0.85f, 0x6E9AE0);
                px.metal(c[0], c[1], c[2], 0.38f, 0.86f);
            }
            case "rust_coat" -> {
                steel(x, y, s, w, true, px);
                float n = fbm(x * 1.2f, y * 1.5f, a, 5);
                float rust = smooth(0.38f, 0.46f, n);
                if (rust > 0f) {
                    float g = fbm(x * 6f, y * 6f, a + 4, 2);
                    float[] c = mix(rgb(0x5A2A10), rgb(0xA0522A), g);
                    px.set(lerp(px.r, c[0], rust), lerp(px.g, c[1], rust), lerp(px.b, c[2], rust),
                            lerp(px.sr, 0.04f, rust), lerp(px.sg, 0.03f, rust), lerp(px.sb, 0.02f, rust));
                }
            }
            case "stained" -> {
                steel(x, y, s, w, true, px);
                float n = fbm(x * 0.55f, y * 0.9f, a, 4);
                float m = fbm(x * 0.8f + 5f, y * 1.2f, a + 1, 3);
                float[] tint = mix(rgb(0x8A6A48), rgb(0x4A5A8A), m);
                float k = smooth(0.45f, 0.62f, n) * 0.6f;
                px.set(lerp(px.r, tint[0] * 0.5f, k), lerp(px.g, tint[1] * 0.5f, k), lerp(px.b, tint[2] * 0.5f, k),
                        lerp(px.sr, tint[0], k), lerp(px.sg, tint[1], k), lerp(px.sb, tint[2], k));
            }
            case "scorched" -> {
                float n = fbm(x * 0.9f, y * 1.1f, a, 5);
                if (n > 0.52f) {
                    float d = 0.7f + 0.3f * fbm(x * 4f, y * 4f, a + 2, 2);
                    px.paint(0.09f * d, 0.065f * d, 0.05f * d, 0.06f);
                } else {
                    float[] c = mix(rgb(0x6A6A5E), rgb(0x3A3328), smooth(0.3f, 0.52f, n));
                    px.paint(c[0], c[1], c[2], 0.1f);
                }
            }
            case "safari_mesh" -> {
                float n = fbm(x * 0.7f, y * 0.9f, a, 3);
                float[] c = mix(rgb(0x8C8060), rgb(0x5E5A40), smooth(0.48f, 0.56f, n));
                float u1 = x * 2.2f + y * 2.2f;
                float u2 = x * 2.2f - y * 2.2f;
                float l1 = Math.abs(u1 - (float) Math.floor(u1) - 0.5f);
                float l2 = Math.abs(u2 - (float) Math.floor(u2) - 0.5f);
                if (l1 < 0.07f || l2 < 0.07f) {
                    c = mul(c, 0.45f);
                }
                px.paint(c[0], c[1], c[2], 0.07f);
            }
            case "boreal_forest" -> {
                float n = fbm(x * 0.8f, y * 1.0f, a, 4);
                float m = fbm(x * 1.3f + 4f, y * 1.4f, a + 1, 3);
                float[] c = n > 0.56f ? rgb(0x2E332A) : n > 0.47f ? rgb(0x56604E) : rgb(0x7C7666);
                if (m > 0.62f) {
                    c = rgb(0xA8A496);
                }
                px.paint(c[0], c[1], c[2], 0.07f);
            }
            case "forest_ddpat" -> {
                float qx = (float) Math.floor(x * 3.5f) / 3.5f;
                float qy = (float) Math.floor(y * 3.5f) / 3.5f;
                float n = fbm(qx * 0.7f, qy * 0.9f, a, 3);
                float m = fbm(qx * 1.1f + 7f, qy * 1.2f, a + 2, 2);
                float[] c = n > 0.58f ? rgb(0x171812) : n > 0.5f ? rgb(0x4A3C26) : rgb(0x46552E);
                if (m > 0.6f) {
                    c = rgb(0x6E7448);
                }
                px.paint(c[0], c[1], c[2], 0.07f);
            }
            case "urban_masked" -> {
                float n = fbm(x * 0.85f, y * 1.0f, a, 4);
                float m = fbm(x * 1.2f + 2f, y * 1.3f, a + 1, 3);
                float[] c = n > 0.58f ? rgb(0x1E2022) : n > 0.48f ? rgb(0x8A8E92) : rgb(0xC8CCD0);
                if (Math.abs(n - 0.58f) < 0.012f || Math.abs(m - 0.6f) < 0.01f) {
                    c = rgb(0x0A0A0A);
                }
                px.paint(c[0], c[1], c[2], 0.08f);
            }
            case "bright_water" -> {
                float n = swirl(x * 0.8f, y * 0.8f, a);
                float[] c = ramp(n, 0f, 0x0E2A70, 0.4f, 0x1A6EA8, 0.65f, 0x22B8C8, 0.85f, 0xBDF2FF, 1f, 0xFFFFFF);
                px.paint(c[0] * 0.85f, c[1] * 0.85f, c[2] * 0.85f, 0.32f);
            }
            case "autotronic" -> {
                float gx = x * 1.4f;
                float gy = y * 2.6f;
                int cx = (int) Math.floor(gx);
                int cy = (int) Math.floor(gy);
                float fx = gx - cx;
                float fy = gy - cy;
                boolean dark = hash(cx, cy, a) > 0.55f;
                boolean seam = fx < 0.05f || fy < 0.07f;
                float split = hash(cx, cy, a + 1);
                if (split > 0.7f && Math.abs(fx - fy) < 0.05f) {
                    seam = true;
                }
                if (seam) {
                    px.paint(0.25f, 0.25f, 0.26f, 0.4f);
                } else if (dark) {
                    px.paint(0.03f, 0.03f, 0.035f, 0.22f);
                } else {
                    float d = 0.9f + 0.1f * fbm(x * 2f, y * 2f, a, 2);
                    px.paint(0.72f * d, 0.05f * d, 0.05f * d, 0.3f);
                }
            }
            case "obsidian" -> {
                float n = fbm(x * 0.5f, y * 1.2f, a, 4);
                float[] sheen = ramp(n, 0.3f, 0x3A2A6A, 0.5f, 0x2A4A8A, 0.7f, 0x6A3A8A);
                px.set(0.02f, 0.02f, 0.025f, sheen[0] * 0.9f + 0.12f, sheen[1] * 0.9f + 0.12f, sheen[2] * 0.9f + 0.14f);
            }
            case "snowfall" -> {
                float n = fbm(x * 1.6f, y * 2.2f, a, 4);
                float speck = hash((int) Math.floor(x * 14f), (int) Math.floor(y * 14f), a);
                float[] c = mix(rgb(0xEEF2F8), rgb(0x9CB4D2), smooth(0.55f, 0.7f, n));
                if (speck > 0.93f) {
                    c = rgb(0x7E96B8);
                }
                px.paint(c[0], c[1], c[2], 0.3f);
            }
            default -> steel(x, y, s, w, true, px);
        }
    }

    private static void doppler(int phase, float x, float y, int a, Px px) {
        float n = swirl(x, y, a);
        float[] c = switch (phase) {
            case 0 -> ramp(n, 0f, 0x050308, 0.45f, 0x1A0A20, 0.62f, 0x9A2A8A, 0.82f, 0xF04AA8, 1f, 0xFFC0E8);
            case 1 -> ramp(n, 0f, 0x2A0A30, 0.3f, 0xC82E96, 0.55f, 0xF050B4, 0.8f, 0x8A40E0, 1f, 0xFFD0F0);
            case 2 -> ramp(n, 0f, 0x041014, 0.35f, 0x1C6A6A, 0.6f, 0x2E9AB0, 0.8f, 0x58D0C0, 1f, 0xE0FFF8);
            default -> ramp(n, 0f, 0x0A0A30, 0.35f, 0x2A4AE8, 0.6f, 0x6A6AFF, 0.8f, 0xD058C8, 1f, 0xF0E0FF);
        };
        speckle(x, y, a, c);
        px.metal(c[0], c[1], c[2], 0.32f, 0.92f);
    }

    private static void gamma(int phase, float x, float y, int a, Px px) {
        float n = swirl(x, y, a);
        float[] c = switch (phase) {
            case 0 -> ramp(n, 0f, 0x02180A, 0.4f, 0x0E7A2A, 0.7f, 0x6AE04A, 1f, 0xE8FF9A);
            case 1 -> ramp(n, 0f, 0x062010, 0.35f, 0x18A040, 0.65f, 0xC8E838, 1f, 0xFFF8B0);
            case 2 -> ramp(n, 0f, 0x021418, 0.35f, 0x0E8A6A, 0.65f, 0x2AC8E0, 1f, 0xD8FFF8);
            default -> ramp(n, 0f, 0x040A20, 0.35f, 0x1A60C8, 0.6f, 0x20C878, 1f, 0xE0FFE8);
        };
        speckle(x, y, a, c);
        px.metal(c[0], c[1], c[2], 0.32f, 0.92f);
    }

    private static void gem(float x, float y, int a, int mid, int bright, int dark, Px px) {
        float n = swirl(x, y, a);
        float[] c = ramp(n, 0f, dark, 0.45f, mid, 0.8f, bright, 1f, 0xFFFFFF);
        speckle(x, y, a, c);
        px.metal(c[0], c[1], c[2], 0.3f, 0.95f);
    }

    private static void speckle(float x, float y, int a, float[] c) {
        float h = hash((int) Math.floor(x * 18f), (int) Math.floor(y * 18f), a + 99);
        if (h > 0.965f) {
            c[0] *= 0.25f;
            c[1] *= 0.25f;
            c[2] *= 0.25f;
        }
    }

    private static float swirl(float x, float y, int a) {
        float wx = fbm(x * 0.55f, y * 0.7f, a + 11, 3);
        float wy = fbm(x * 0.55f + 4.2f, y * 0.7f + 1.7f, a + 12, 3);
        float n = fbm(x * 0.8f + wx * 3.2f, y * 0.9f + wy * 3.2f, a + 13, 5);
        return clamp((n - 0.28f) / 0.44f);
    }

    private static void worn(Finish finish, int seed, float wear, float x, float y, float s, float w, Px px,
                             boolean blade) {
        float scratch = Math.abs(noise(x * 0.9f + y * 0.35f, y * 30f - x * 2.2f, seed + 501));
        boolean scratched = scratch < 0.008f + wear * 0.05f && hash((int) (x * 3f), (int) (y * 3f), seed) < wear * 1.4f;
        if (px.paint) {
            float exposure = fbm(x * 2.4f, y * 3.2f, seed + 77, 4) * 0.75f;
            if (blade) {
                exposure += smooth(0.78f, 1f, w) * 0.55f + smooth(0.15f, 0f, w) * 0.35f + smooth(0.82f, 1f, s) * 0.4f;
            }
            if (exposure > 1.25f - wear * 1.35f || (scratched && wear > 0.05f)) {
                steel(x, y, s, w, blade, px);
                px.r *= 1.08f;
                px.g *= 1.08f;
                px.b *= 1.08f;
                px.sr *= 0.85f;
                px.sg *= 0.85f;
                px.sb *= 0.85f;
            }
            return;
        }
        if (scratched) {
            px.r = px.r * 0.85f + 0.06f;
            px.g = px.g * 0.85f + 0.06f;
            px.b = px.b * 0.85f + 0.06f;
            px.sr *= 0.7f;
            px.sg *= 0.7f;
            px.sb *= 0.7f;
        }
        float dull = 1f - wear * 0.35f;
        px.sr *= dull;
        px.sg *= dull;
        px.sb *= dull;
    }

    private static void material(String material, int salt, float x, float y, float s, float w, Px px) {
        switch (material) {
            case "steel" -> {
                float brush = noise(x * 0.5f, y * 22f, salt) * 0.5f + 0.5f;
                float base = 0.2f + 0.05f * brush;
                float shine = 0.74f + 0.12f * brush;
                px.set(base, base * 1.02f, base * 1.06f, shine * 0.95f, shine * 0.97f, shine);
            }
            case "brass" -> {
                float brush = noise(x * 0.5f, y * 22f, salt) * 0.5f + 0.5f;
                px.metal(0.9f, 0.7f, 0.34f, 0.4f + 0.05f * brush, 0.8f + 0.1f * brush);
            }
            case "rubber", "rubber_blue", "polymer" -> {
                float grain = fbm(x * 9f, y * 9f, salt, 2);
                float d = 0.85f + 0.3f * grain;
                float[] c = material.equals("rubber_blue") ? rgb(0x232A3A)
                        : material.equals("polymer") ? rgb(0x141416) : rgb(0x18181A);
                px.paint(c[0] * d, c[1] * d, c[2] * d, material.equals("polymer") ? 0.09f : 0.05f);
            }
            case "g10" -> {
                float weave = (float) (Math.sin(x * 26f) * Math.sin(y * 26f));
                float d = 0.85f + 0.15f * weave + 0.1f * fbm(x * 5f, y * 5f, salt, 2);
                px.paint(0.07f * d, 0.075f * d, 0.08f * d, 0.1f);
            }
            case "wood", "wood_dark" -> {
                float grain = (float) Math.sin((y * 1.1f + 0.7f * fbm(x * 0.35f, y * 0.9f, salt, 4) * 2f) * 15f);
                float fine = fbm(x * 0.6f, y * 18f, salt + 1, 2);
                float t = grain * 0.5f + 0.5f;
                float[] light = material.equals("wood") ? rgb(0x7A4A24) : rgb(0x3A2214);
                float[] dark = material.equals("wood") ? rgb(0x4A2810) : rgb(0x1E1008);
                float[] c = mix(light, dark, t * 0.8f + fine * 0.2f);
                px.paint(c[0], c[1], c[2], 0.16f);
            }
            case "bone" -> {
                float streak = fbm(x * 0.5f, y * 10f, salt, 3);
                float[] c = mix(rgb(0xE8E0C8), rgb(0xBCB096), streak);
                px.paint(c[0], c[1], c[2], 0.2f);
            }
            case "leather" -> {
                float band = x * 1.7f + y * 0.35f;
                float f = band - (float) Math.floor(band);
                float d = f < 0.08f ? 0.45f : 0.8f + 0.2f * (float) Math.sin(f * Math.PI);
                float n = 0.9f + 0.15f * fbm(x * 6f, y * 6f, salt, 2);
                px.paint(0.2f * d * n, 0.12f * d * n, 0.08f * d * n, 0.1f);
            }
            case "paracord_black", "paracord_green" -> {
                float u1 = x * 3.2f + y * 3.2f;
                float u2 = x * 3.2f - y * 3.2f;
                boolean first = (Math.floorMod((int) Math.floor(u1) + (int) Math.floor(u2), 2)) == 0;
                float f = first ? u1 - (float) Math.floor(u1) : u2 - (float) Math.floor(u2);
                float shade = 0.45f + 0.55f * (float) Math.sin(f * Math.PI);
                float fiber = 0.9f + 0.2f * fbm(x * 12f, y * 12f, salt, 2);
                float[] c = material.equals("paracord_green") ? rgb(0x3A4A28) : rgb(0x1C1C1E);
                px.paint(c[0] * shade * fiber, c[1] * shade * fiber, c[2] * shade * fiber, 0.05f);
            }
            default -> px.paint(0.1f, 0.1f, 0.1f, 0.05f);
        }
    }

    private static int argb(float r, float g, float b) {
        return 0xFF000000 | (byteOf(r) << 16) | (byteOf(g) << 8) | byteOf(b);
    }

    private static int byteOf(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    private static float[] rgb(int hex) {
        return new float[]{(hex >> 16 & 0xFF) / 255f, (hex >> 8 & 0xFF) / 255f, (hex & 0xFF) / 255f};
    }

    private static float[] ramp(float t, Object... stops) {
        float x = clamp(t);
        int n = stops.length / 2;
        float first = ((Number) stops[0]).floatValue();
        if (x <= first) {
            return rgb((Integer) stops[1]);
        }
        for (int i = 0; i < n - 1; i++) {
            float t0 = ((Number) stops[i * 2]).floatValue();
            float t1 = ((Number) stops[i * 2 + 2]).floatValue();
            if (x <= t1) {
                float f = (x - t0) / Math.max(t1 - t0, 1.0e-5f);
                f = f * f * (3f - 2f * f);
                return mix(rgb((Integer) stops[i * 2 + 1]), rgb((Integer) stops[i * 2 + 3]), f);
            }
        }
        return rgb((Integer) stops[stops.length - 1]);
    }

    private static float[] mix(float[] p, float[] q, float t) {
        return new float[]{lerp(p[0], q[0], t), lerp(p[1], q[1], t), lerp(p[2], q[2], t)};
    }

    private static float[] mul(float[] p, float k) {
        return new float[]{p[0] * k, p[1] * k, p[2] * k};
    }

    private static float lerp(float p, float q, float t) {
        return p + (q - p) * t;
    }

    private static float clamp(float x) {
        return x < 0f ? 0f : (x > 1f ? 1f : x);
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    static float noise(float x, float y, int seed) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        float u = fx * fx * fx * (fx * (fx * 6f - 15f) + 10f);
        float v = fy * fy * fy * (fy * (fy * 6f - 15f) + 10f);
        float n00 = grad(x0, y0, seed, fx, fy);
        float n10 = grad(x0 + 1, y0, seed, fx - 1f, fy);
        float n01 = grad(x0, y0 + 1, seed, fx, fy - 1f);
        float n11 = grad(x0 + 1, y0 + 1, seed, fx - 1f, fy - 1f);
        return lerp(lerp(n00, n10, u), lerp(n01, n11, u), v) * 1.4f;
    }

    private static float grad(int x, int y, int seed, float dx, float dy) {
        float angle = hash(x, y, seed) * (float) (Math.PI * 2);
        return (float) Math.cos(angle) * dx + (float) Math.sin(angle) * dy;
    }

    static float fbm(float x, float y, int seed, int octaves) {
        float sum = 0f;
        float amp = 0.5f;
        float total = 0f;
        for (int i = 0; i < octaves; i++) {
            sum += noise(x, y, seed + i * 101) * amp;
            total += amp;
            x = x * 2.03f + 1.7f;
            y = y * 2.03f + 9.2f;
            amp *= 0.5f;
        }
        return clamp(sum / total * 0.5f + 0.5f);
    }

    private static float cellular(float x, float y, int seed) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        float best = 9f;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                float px = cx + i + hash(cx + i, cy + j, seed);
                float py = cy + j + hash(cx + i, cy + j, seed + 1);
                float d = (x - px) * (x - px) + (y - py) * (y - py);
                best = Math.min(best, d);
            }
        }
        return (float) Math.sqrt(best);
    }
}
