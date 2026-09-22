package com.qza.waypoint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qza.config.ConfigManager;
import com.qza.util.DungeonState;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public final class WaypointRenderer {
    private static final float FILL_ALPHA = 0.25f;
    private static final double GROW = 0.002;
    private static final int LIGHT = 0xF000F0;

    private static int framesDrawn;
    private static int labelsAttempted;
    private static String lastSkip = "never drawn";

    private WaypointRenderer() {
    }

    public static String diagnosis() {
        return "drawn " + framesDrawn + " frames, labels attempted " + labelsAttempted
                + ", last skip: " + lastSkip;
    }

    public static void init() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            if (!showing()) {
                return;
            }
            draw(context.poseStack(), context.bufferSource());
        });
    }

    private static boolean showing() {
        if (!ConfigManager.get().waypointsEnabled) {
            lastSkip = "waypoints switched off";
            return false;
        }
        if (WaypointList.size() == 0) {
            lastSkip = "no waypoints saved";
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            lastSkip = "not in a world";
            return false;
        }
        if (!WaypointEditor.active() && ConfigManager.get().waypointsDungeonOnly
                && !DungeonState.inDungeon()) {
            lastSkip = "dungeons only, and not in one";
            return false;
        }
        lastSkip = "none";
        return true;
    }

    private static void draw(PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer fill = buffers.getBuffer(RenderTypes.debugFilledBox());
        for (Waypoint waypoint : WaypointList.all()) {
            if (waypoint.enabled) {
                fill(fill, poseStack, box(waypoint), waypoint.argb());
            }
        }

        VertexConsumer lines = buffers.getBuffer(RenderTypes.lines());
        for (Waypoint waypoint : WaypointList.all()) {
            if (waypoint.enabled) {
                outline(lines, poseStack, box(waypoint), waypoint.argb());
            }
        }

        framesDrawn++;
        if (ConfigManager.get().waypointShowNames) {
            for (Waypoint waypoint : WaypointList.all()) {
                if (waypoint.enabled) {
                    labelsAttempted++;
                    label(poseStack, buffers, waypoint);
                }
            }
        }

        poseStack.popPose();

        // Text is queued rather than drawn, and nothing else in this pass ends
        // the batch, so without this the labels are built and thrown away.
        buffers.endBatch();
    }

    private static void label(PoseStack poseStack, MultiBufferSource buffers, Waypoint waypoint) {
        String name = waypoint.label();
        if (name == null || name.isBlank()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        if (font == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(
                (waypoint.minX() + waypoint.maxX()) / 2.0,
                waypoint.maxY() + 0.35,
                (waypoint.minZ() + waypoint.maxZ()) / 2.0);
        poseStack.mulPose(client.gameRenderer.getMainCamera().rotation());
        poseStack.scale(-0.025f, -0.025f, 0.025f);

        font.drawInBatch(name, -font.width(name) / 2.0f, 0, waypoint.argb(), false,
                poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LIGHT);

        poseStack.popPose();
    }

    private static AABB box(Waypoint waypoint) {
        return new AABB(waypoint.minX(), waypoint.minY(), waypoint.minZ(),
                waypoint.maxX(), waypoint.maxY(), waypoint.maxZ()).inflate(GROW);
    }

    private static void outline(VertexConsumer lines, PoseStack poseStack, AABB box, int argb) {
        ShapeRenderer.renderShape(poseStack, lines,
                Shapes.create(box.move(-box.minX, -box.minY, -box.minZ)),
                box.minX, box.minY, box.minZ, argb, 1.0f);
    }

    private static void fill(VertexConsumer quads, PoseStack poseStack, AABB box, int argb) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * FILL_ALPHA);
        int faded = (alpha << 24) | (argb & 0x00FFFFFF);

        var pose = poseStack.last().pose();
        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        quad(quads, pose, faded, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        quad(quads, pose, faded, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2);
        quad(quads, pose, faded, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1);
        quad(quads, pose, faded, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
        quad(quads, pose, faded, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        quad(quads, pose, faded, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1);
    }

    private static void quad(VertexConsumer quads, org.joml.Matrix4f pose, int argb,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        quads.addVertex(pose, ax, ay, az).setColor(argb);
        quads.addVertex(pose, bx, by, bz).setColor(argb);
        quads.addVertex(pose, cx, cy, cz).setColor(argb);
        quads.addVertex(pose, dx, dy, dz).setColor(argb);
    }
}
