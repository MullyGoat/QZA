package com.qza.pack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public final class PackTheme {
    public static final String DEFAULT = "default";
    public static final List<String> THEMES = List.of(DEFAULT, "pink", "red", "orange", "yellow", "lime", "aqua",
            "blue", "purple", "white");

    public static final List<String> DIAMONDS = List.of(
            "textures/item/diamond.png", "textures/item/diamond_sword.png", "textures/item/diamond_pickaxe.png",
            "textures/item/diamond_axe.png", "textures/item/diamond_shovel.png", "textures/item/diamond_hoe.png",
            "textures/item/diamond_spear.png", "textures/item/diamond_spear_in_hand.png",
            "textures/item/diamond_helmet.png", "textures/item/diamond_chestplate.png",
            "textures/item/diamond_leggings.png", "textures/item/diamond_boots.png",
            "textures/item/diamond_horse_armor.png", "textures/item/diamond_nautilus_armor.png",
            "textures/entity/equipment/humanoid/diamond.png", "textures/entity/equipment/humanoid_leggings/diamond.png",
            "textures/entity/equipment/horse_body/diamond.png", "textures/entity/equipment/nautilus_body/diamond.png",
            "textures/models/armor/diamond_layer_1.png", "textures/models/armor/diamond_layer_2.png",
            "textures/block/diamond_block.png", "textures/block/diamond_ore.png",
            "textures/block/deepslate_diamond_ore.png");
    public static final List<String> HEARTS = List.of(
            "textures/gui/sprites/hud/heart/full.png", "textures/gui/sprites/hud/heart/half.png",
            "textures/gui/sprites/hud/heart/full_blinking.png", "textures/gui/sprites/hud/heart/half_blinking.png",
            "textures/gui/sprites/hud/heart/hardcore_full.png", "textures/gui/sprites/hud/heart/hardcore_half.png",
            "textures/gui/sprites/hud/heart/hardcore_full_blinking.png",
            "textures/gui/sprites/hud/heart/hardcore_half_blinking.png");
    public static final List<String> XP_BAR = List.of("textures/gui/sprites/hud/experience_bar_progress.png");
    public static final List<String> CROSSHAIR = List.of("textures/gui/sprites/hud/crosshair.png",
            "textures/gui/sprites/hud/hotbar_selection.png");
    public static final List<String> GLINT = List.of("textures/misc/enchanted_glint_item.png",
            "textures/misc/enchanted_glint_armor.png", "textures/misc/enchanted_glint_entity.png");

    public enum Rule {
        DIAMOND(0.42f, 0.70f, 0.12f),
        COLOURED(0f, 1f, 0.2f),
        TINT(0f, 1f, -1f);

        final float from;
        final float to;
        final float minSat;

        Rule(float from, float to, float minSat) {
            this.from = from;
            this.to = to;
            this.minSat = minSat;
        }

        boolean matches(float hue, float sat) {
            if (sat < minSat) {
                return false;
            }
            return from <= to ? hue >= from && hue <= to : hue >= from || hue <= to;
        }
    }

    private PackTheme() {
    }

    public static String label(String theme) {
        if (theme == null || theme.isEmpty() || DEFAULT.equals(theme)) {
            return "Default";
        }
        return theme.substring(0, 1).toUpperCase(Locale.ROOT) + theme.substring(1);
    }

    public static int colour(String theme) {
        if (DEFAULT.equals(theme) || "white".equals(theme)) {
            return 0xFFFFFFFF;
        }
        return 0xFF000000 | hsvToRgb(hue(theme), 0.65f, 1f);
    }

    private static float hue(String theme) {
        return switch (theme) {
            case "red" -> 0f;
            case "orange" -> 0.075f;
            case "yellow" -> 0.15f;
            case "lime" -> 0.27f;
            case "aqua" -> 0.5f;
            case "blue" -> 0.61f;
            case "purple" -> 0.76f;
            default -> 0.89f;
        };
    }

    public static byte[] recolour(byte[] png, Rule rule, String theme) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
        if (source == null) {
            return png;
        }
        boolean white = "white".equals(theme);
        float target = hue(theme);
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        float[] hsv = new float[3];
        float lift = rule == Rule.COLOURED ? brightest(source, rule, hsv) : 1f;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    out.setRGB(x, y, argb);
                    continue;
                }
                rgbToHsv(argb, hsv);
                if (!rule.matches(hsv[0], hsv[1])) {
                    out.setRGB(x, y, argb);
                    continue;
                }
                float sat = hsv[1];
                float val = Math.min(1f, hsv[2] / lift);
                if (white) {
                    sat *= rule == Rule.TINT ? 0f : 0.1f;
                    val = Math.min(1f, val * 1.12f + 0.06f);
                } else if (rule == Rule.TINT) {
                    sat = Math.max(sat, 0.55f);
                }
                out.setRGB(x, y, (alpha << 24) | hsvToRgb(target, sat, val));
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bytes);
        return bytes.toByteArray();
    }

    private static float brightest(BufferedImage image, Rule rule, float[] hsv) {
        float max = 0f;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (argb >>> 24 == 0) {
                    continue;
                }
                rgbToHsv(argb, hsv);
                if (rule.matches(hsv[0], hsv[1])) {
                    max = Math.max(max, hsv[2]);
                }
            }
        }
        return max > 0f ? max : 1f;
    }

    static void rgbToHsv(int argb, float[] hsv) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue = 0f;
        if (delta > 0f) {
            if (max == r) {
                hue = ((g - b) / delta) % 6f;
            } else if (max == g) {
                hue = (b - r) / delta + 2f;
            } else {
                hue = (r - g) / delta + 4f;
            }
            hue /= 6f;
            if (hue < 0f) {
                hue += 1f;
            }
        }
        hsv[0] = hue;
        hsv[1] = max == 0f ? 0f : delta / max;
        hsv[2] = max;
    }

    static int hsvToRgb(float hue, float sat, float val) {
        float h = (hue - (float) Math.floor(hue)) * 6f;
        int sector = (int) h;
        float f = h - sector;
        float p = val * (1f - sat);
        float q = val * (1f - sat * f);
        float t = val * (1f - sat * (1f - f));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> {
                r = val;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = val;
                b = p;
            }
            case 2 -> {
                r = p;
                g = val;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = val;
            }
            case 4 -> {
                r = t;
                g = p;
                b = val;
            }
            default -> {
                r = val;
                g = p;
                b = q;
            }
        }
        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }
}
