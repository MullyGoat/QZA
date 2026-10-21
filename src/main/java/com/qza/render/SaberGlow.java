package com.qza.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qza.compat.GlowRender;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public final class SaberGlow {
    private static final float[][] LAYERS = {
            {10.0f, 0.00f, 0.07f},
            {6.2f, 0.00f, 0.15f},
            {3.6f, 0.10f, 0.34f},
            {2.1f, 0.35f, 0.62f},
            {1.25f, 0.80f, 0.95f},
    };
    private static final float BASE_REACH = 0.35f;
    private static final float TIP_REACH = 0.9f;
    private static final int FLASH_SIDES = 12;

    private SaberGlow() {
    }

    public static void draw(SubmitNodeCollector collector, Matrix4f pose, TextureAtlasSprite sprite, Vector3f from,
                            Vector3f to, float radius, int rgb, float intensity, float flash) {
        Vector3f a = pose.transformPosition(from, new Vector3f());
        Vector3f b = pose.transformPosition(to, new Vector3f());
        Vector3f axis = new Vector3f(b).sub(a);
        float length = axis.length();
        if (length < 1.0e-4f && flash <= 0.001f) {
            return;
        }
        if (length < 1.0e-4f) {
            axis.set(0f, 1f, 0f);
        } else {
            axis.div(length);
        }
        Vector3f view = new Vector3f(a).add(b).mul(0.5f);
        Vector3f across = new Vector3f(axis).cross(view);
        if (across.lengthSquared() < 1.0e-8f) {
            across.set(axis.y, -axis.x, 0f);
        }
        across.normalize();
        float u = Mth.lerp(0.5f / 16f, sprite.getU0(), sprite.getU1());
        float v = Mth.lerp(0.5f / 16f, sprite.getV0(), sprite.getV1());
        float r = (rgb >> 16 & 0xFF) / 255f;
        float g = (rgb >> 8 & 0xFF) / 255f;
        float bl = (rgb & 0xFF) / 255f;

        GlowRender.submit(collector, sprite.atlasLocation(), List.of(), out -> {
            if (length >= 1.0e-4f) {
                for (int i = 0; i < LAYERS.length; i++) {
                    float[] layer = LAYERS[i];
                    float width = radius * layer[0];
                    float white = layer[1];
                    float k = layer[2] * intensity;
                    int centre = colour(r + (1f - r) * white, g + (1f - g) * white, bl + (1f - bl) * white, k);
                    float pull = 1f - 0.0006f * i;
                    ribbon(out, a, b, axis, across, width, centre, pull, u, v);
                }
            }
            if (flash > 0.001f) {
                int burst = colour(0.55f + 0.45f * r, 0.55f + 0.45f * g, 0.55f + 0.45f * bl, flash * 0.9f);
                disc(out, a, axis, across, radius * 3.5f * (0.6f + 0.6f * flash), burst, u, v);
            }
        });
    }

    private static void ribbon(VertexConsumer out, Vector3f a, Vector3f b, Vector3f axis, Vector3f across, float width,
                               int centre, float pull, float u, float v) {
        Vector3f start = new Vector3f(axis).mul(-width * BASE_REACH).add(a);
        Vector3f end = new Vector3f(axis).mul(width * TIP_REACH).add(b);
        Vector3f side = new Vector3f(across).mul(width);
        Vector3f aLeft = new Vector3f(a).sub(side);
        Vector3f aRight = new Vector3f(a).add(side);
        Vector3f bLeft = new Vector3f(b).sub(side);
        Vector3f bRight = new Vector3f(b).add(side);
        quad(out, aLeft, a, b, bLeft, 0, centre, centre, 0, pull, u, v);
        quad(out, a, aRight, bRight, b, centre, 0, 0, centre, pull, u, v);
        quad(out, bLeft, b, end, end, 0, centre, 0, 0, pull, u, v);
        quad(out, b, bRight, end, end, centre, 0, 0, 0, pull, u, v);
        quad(out, start, start, a, aLeft, 0, 0, centre, 0, pull, u, v);
        quad(out, start, aRight, a, a, 0, 0, centre, centre, pull, u, v);
    }

    private static void disc(VertexConsumer out, Vector3f centre, Vector3f axis, Vector3f across, float size, int colour,
                             float u, float v) {
        Vector3f other = new Vector3f(axis);
        for (int i = 0; i < FLASH_SIDES; i++) {
            float a0 = Mth.TWO_PI * i / FLASH_SIDES;
            float a1 = Mth.TWO_PI * (i + 1) / FLASH_SIDES;
            Vector3f p0 = point(centre, across, other, size, a0);
            Vector3f p1 = point(centre, across, other, size, a1);
            quad(out, centre, p0, p1, p1, colour, 0, 0, 0, 0.999f, u, v);
        }
    }

    private static Vector3f point(Vector3f centre, Vector3f x, Vector3f y, float size, float angle) {
        return new Vector3f(x).mul(Mth.cos(angle) * size).add(new Vector3f(y).mul(Mth.sin(angle) * size)).add(centre);
    }

    private static void quad(VertexConsumer out, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, int c0, int c1,
                             int c2, int c3, float pull, float u, float v) {
        vertex(out, p0, c0, pull, u, v);
        vertex(out, p1, c1, pull, u, v);
        vertex(out, p2, c2, pull, u, v);
        vertex(out, p3, c3, pull, u, v);
    }

    private static void vertex(VertexConsumer out, Vector3f p, int colour, float pull, float u, float v) {
        out.addVertex(p.x * pull, p.y * pull, p.z * pull).setUv(u, v).setColor(colour == 0 ? 0xFF000000 : colour);
    }

    private static int colour(float r, float g, float b, float k) {
        int ri = Math.min(255, Math.round(r * k * 255f));
        int gi = Math.min(255, Math.round(g * k * 255f));
        int bi = Math.min(255, Math.round(b * k * 255f));
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }
}
