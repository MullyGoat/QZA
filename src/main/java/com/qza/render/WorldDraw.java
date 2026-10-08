package com.qza.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public final class WorldDraw {
    private static final PoseStack IDENTITY = new PoseStack();

    private WorldDraw() {
    }

    public static void submit(SubmitNodeCollector collector, RenderType type,
                              SubmitNodeCollector.CustomGeometryRenderer renderer) {
        collector.submitCustomGeometry(IDENTITY, type, renderer);
    }

    public static void outline(VertexConsumer buffer, PoseStack.Pose pose, AABB box, int argb, float width) {
        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        for (float y : new float[]{y1, y2}) {
            for (float z : new float[]{z1, z2}) {
                line(buffer, pose, x1, y, z, x2, y, z, 1f, 0f, 0f, argb, width);
            }
        }
        for (float x : new float[]{x1, x2}) {
            for (float z : new float[]{z1, z2}) {
                line(buffer, pose, x, y1, z, x, y2, z, 0f, 1f, 0f, argb, width);
            }
        }
        for (float x : new float[]{x1, x2}) {
            for (float y : new float[]{y1, y2}) {
                line(buffer, pose, x, y, z1, x, y, z2, 0f, 0f, 1f, argb, width);
            }
        }
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float nx, float ny, float nz, int argb, float width) {
        buffer.addVertex(pose, ax, ay, az).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
        buffer.addVertex(pose, bx, by, bz).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }

    public static void fill(VertexConsumer buffer, PoseStack.Pose pose, AABB box, int argb) {
        Matrix4f m = pose.pose();
        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        quad(buffer, m, argb, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        quad(buffer, m, argb, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2);
        quad(buffer, m, argb, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1);
        quad(buffer, m, argb, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
        quad(buffer, m, argb, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        quad(buffer, m, argb, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1);
    }

    private static void quad(VertexConsumer buffer, Matrix4f m, int argb,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        buffer.addVertex(m, ax, ay, az).setColor(argb);
        buffer.addVertex(m, bx, by, bz).setColor(argb);
        buffer.addVertex(m, cx, cy, cz).setColor(argb);
        buffer.addVertex(m, dx, dy, dz).setColor(argb);
    }
}
