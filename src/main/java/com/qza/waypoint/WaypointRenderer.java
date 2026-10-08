package com.qza.waypoint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qza.config.ConfigManager;
import com.qza.render.WorldDraw;
import com.qza.util.DungeonState;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class WaypointRenderer {
    private static final float FILL_ALPHA = 0.25f;
    private static final double GROW = 0.002;
    private static final int LIGHT = 0xF000F0;
    private static final int BACKDROP = 0x60000000;
    private static final float TEXT_SCALE = 0.025f;
    private static final float LINE_WIDTH = 1.0f;

    private static final PoseStack IDENTITY = new PoseStack();

    private WaypointRenderer() {
    }

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            if (!showing()) {
                return;
            }
            CameraRenderState camera = context.levelState().cameraRenderState;
            if (camera == null || camera.pos == null) {
                return;
            }
            drawBoxes(context.submitNodeCollector(), camera.pos);
            if (ConfigManager.get().waypointShowNames) {
                drawLabels(context.poseStack(), context.submitNodeCollector(), camera);
            }
        });
    }

    private static boolean showing() {
        if (!ConfigManager.get().waypointsEnabled) {
            return false;
        }
        if (WaypointList.size() == 0) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return false;
        }
        if (!WaypointEditor.active() && ConfigManager.get().waypointsDungeonOnly
                && !DungeonState.inDungeon()) {
            return false;
        }
        return true;
    }

    private static void drawBoxes(SubmitNodeCollector collector, Vec3 camera) {
        List<Waypoint> shown = new ArrayList<>();
        for (Waypoint waypoint : WaypointList.all()) {
            if (waypoint.enabled) {
                shown.add(waypoint);
            }
        }
        if (shown.isEmpty()) {
            return;
        }

        WorldDraw.submit(collector, RenderTypes.debugFilledBox(), (pose, buffer) -> {
            for (Waypoint waypoint : shown) {
                WorldDraw.fill(buffer, pose, box(waypoint, camera), faded(waypoint.argb()));
            }
        });
        WorldDraw.submit(collector, RenderTypes.lines(), (pose, buffer) -> {
            for (Waypoint waypoint : shown) {
                WorldDraw.outline(buffer, pose, box(waypoint, camera), waypoint.argb(), LINE_WIDTH);
            }
        });
    }

    private static void drawLabels(PoseStack poseStack, SubmitNodeCollector collector,
                                   CameraRenderState cameraState) {
        if (cameraState == null || cameraState.pos == null) {
            return;
        }
        Vec3 camera = cameraState.pos;

        for (WaypointGroup group : WaypointList.groups()) {
            if (group.visible()) {
                label(poseStack, collector, cameraState, camera, group);
            }
        }
    }

    private static void label(PoseStack poseStack, SubmitNodeCollector collector,
                              CameraRenderState cameraState, Vec3 camera, WaypointGroup group) {
        String name = group.label();
        if (name == null || name.isBlank()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        if (font == null || cameraState.orientation == null) {
            return;
        }

        double x = (group.minX() + group.maxX()) / 2.0;
        double y = group.maxY() + 0.35;
        double z = (group.minZ() + group.maxZ()) / 2.0;

        Matrix4f matrix = new Matrix4f()
                .translate((float) (x - camera.x), (float) (y - camera.y),
                        (float) (z - camera.z))
                .rotate(cameraState.orientation)
                .scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        Font.PreparedText prepared = font.prepareText(name,
                -font.width(name) / 2.0f, 0.0f, group.argb(), false, BACKDROP);

        prepared.visit(new Font.GlyphVisitor() {
            @Override
            public void acceptGlyph(TextRenderable.Styled glyph) {
                submit(glyph);
            }

            @Override
            public void acceptEffect(TextRenderable effect) {
                submit(effect);
            }

            private void submit(TextRenderable renderable) {
                collector.submitCustomGeometry(IDENTITY,
                        renderable.renderType(Font.DisplayMode.NORMAL),
                        (pose, consumer) -> renderable.render(matrix, consumer, LIGHT, false));
            }
        });
    }

    private static AABB box(Waypoint waypoint, Vec3 camera) {
        return new AABB(waypoint.minX(), waypoint.minY(), waypoint.minZ(),
                waypoint.maxX(), waypoint.maxY(), waypoint.maxZ()).inflate(GROW)
                .move(-camera.x, -camera.y, -camera.z);
    }

    private static int faded(int argb) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * FILL_ALPHA);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
