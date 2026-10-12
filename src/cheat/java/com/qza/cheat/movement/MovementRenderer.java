package com.qza.cheat.movement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qza.cheat.CheatConfigManager;
import com.qza.render.WorldDraw;
import com.qza.util.SkyBlockArea;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class MovementRenderer {
    private static final int PINK = 0xFFFF55FF;
    private static final int GREEN = 0xFF55FF55;
    private static final int TRIMMED = 0xFF777777;
    private static final int BACKDROP = 0x60000000;
    private static final int LIGHT = 0xF000F0;
    private static final float TEXT_SCALE = 0.025f;
    private static final float LINE_WIDTH = 2.0f;
    private static final double GROW = 0.002;
    private static final double RANGE_SQ = 160.0 * 160.0;
    private static final double PATH_LIFT = 0.05;
    private static final double RING_LIFT = 0.02;
    private static final int RING_SEGMENTS = 48;

    private static final PoseStack IDENTITY = new PoseStack();

    private MovementRenderer() {
    }

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.player == null || !MovementCopier.enabled()) {
                return;
            }
            CameraRenderState camera = context.levelState().cameraRenderState;
            if (camera == null || camera.pos == null) {
                return;
            }
            Vec3 cam = camera.pos;
            SubmitNodeCollector collector = context.submitNodeCollector();

            List<Recording> shown = new ArrayList<>();
            if (CheatConfigManager.get().movementHighlight) {
                String area = SkyBlockArea.current();
                for (Recording recording : Recordings.all()) {
                    BlockPos pos = recording.blockPos();
                    if (pos != null && Recordings.sameArea(recording, area)
                            && Vec3.atCenterOf(pos).distanceToSqr(cam) <= RANGE_SQ) {
                        shown.add(recording);
                    }
                }
            }
            if (!shown.isEmpty()) {
                List<Recording> boxes = new ArrayList<>();
                List<Recording> rings = new ArrayList<>();
                for (Recording recording : shown) {
                    (recording.walkIn() ? rings : boxes).add(recording);
                }
                if (!boxes.isEmpty()) {
                    drawBlocks(collector, cam, boxes);
                }
                if (!rings.isEmpty()) {
                    drawRings(collector, cam, client.level, rings);
                }
                for (Recording recording : shown) {
                    double top = recording.walkIn()
                            ? MovementCopier.ringY(client.level, recording.blockPos()) + 0.5
                            : recording.blockPos().getY() + 1.4;
                    label(collector, camera, recording, top);
                }
            }

            Recording editing = RecordingEditScreen.editing();
            if (editing != null && editing.size() > 1) {
                drawPath(collector, cam, editing);
            }
        });
    }

    private static int colour(Recording recording) {
        return MovementCopier.current() == recording ? GREEN : PINK;
    }

    private static AABB box(Recording recording, Vec3 cam) {
        BlockPos pos = recording.blockPos();
        return new AABB(pos).inflate(GROW).move(-cam.x, -cam.y, -cam.z);
    }

    private static void drawBlocks(SubmitNodeCollector collector, Vec3 cam, List<Recording> shown) {
        WorldDraw.submit(collector, RenderTypes.debugFilledBox(), (pose, buffer) -> {
            for (Recording recording : shown) {
                WorldDraw.fill(buffer, pose, box(recording, cam), (colour(recording) & 0x00FFFFFF) | 0x40000000);
            }
        });
        WorldDraw.submit(collector, RenderTypes.lines(), (pose, buffer) -> {
            for (Recording recording : shown) {
                WorldDraw.outline(buffer, pose, box(recording, cam), colour(recording), LINE_WIDTH);
            }
        });
    }

    private static void drawRings(SubmitNodeCollector collector, Vec3 cam, Level level, List<Recording> rings) {
        WorldDraw.submit(collector, RenderTypes.lines(), (pose, buffer) -> {
            for (Recording recording : rings) {
                BlockPos pos = recording.blockPos();
                float y = (float) (MovementCopier.ringY(level, pos) + RING_LIFT - cam.y);
                double cx = pos.getX() + 0.5 - cam.x;
                double cz = pos.getZ() + 0.5 - cam.z;
                int argb = colour(recording);
                for (int i = 0; i < RING_SEGMENTS; i++) {
                    double a = (Math.PI * 2.0 * i) / RING_SEGMENTS;
                    double b = (Math.PI * 2.0 * (i + 1)) / RING_SEGMENTS;
                    line(buffer, pose,
                            (float) (cx + (Math.cos(a) * MovementCopier.RING_RADIUS)), y,
                            (float) (cz + (Math.sin(a) * MovementCopier.RING_RADIUS)),
                            (float) (cx + (Math.cos(b) * MovementCopier.RING_RADIUS)), y,
                            (float) (cz + (Math.sin(b) * MovementCopier.RING_RADIUS)), argb);
                }
            }
        });
    }

    private static void drawPath(SubmitNodeCollector collector, Vec3 cam, Recording recording) {
        int start = recording.start();
        int end = recording.end();
        List<MoveFrame> frames = recording.frames;
        WorldDraw.submit(collector, RenderTypes.lines(), (pose, buffer) -> {
            MoveFrame last = frames.get(0);
            for (int i = 1; i < frames.size(); i++) {
                MoveFrame frame = frames.get(i);
                if (frame.distanceSq(last) < 1.0E-6) {
                    continue;
                }
                boolean jump = frame.teleports > last.teleports;
                if (!jump) {
                    boolean kept = i > start && i < end;
                    segment(buffer, pose, last, frame, cam, kept ? PINK : TRIMMED);
                }
                last = frame;
            }
        });
        WorldDraw.submit(collector, RenderTypes.lines(), (pose, buffer) -> {
            marker(buffer, pose, frames.get(start), cam, GREEN);
            marker(buffer, pose, frames.get(Math.max(start, end - 1)), cam, 0xFFFF5555);
        });
    }

    private static void marker(VertexConsumer buffer, PoseStack.Pose pose, MoveFrame frame, Vec3 cam, int argb) {
        double x = frame.x - cam.x;
        double y = frame.y - cam.y;
        double z = frame.z - cam.z;
        WorldDraw.outline(buffer, pose, new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3), argb, LINE_WIDTH);
    }

    private static void segment(VertexConsumer buffer, PoseStack.Pose pose, MoveFrame a, MoveFrame b, Vec3 cam,
                                int argb) {
        line(buffer, pose, (float) (a.x - cam.x), (float) (a.y + PATH_LIFT - cam.y), (float) (a.z - cam.z),
                (float) (b.x - cam.x), (float) (b.y + PATH_LIFT - cam.y), (float) (b.z - cam.z), argb);
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose, float ax, float ay, float az,
                             float bx, float by, float bz, int argb) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        if (length < 1.0E-4f) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        buffer.addVertex(pose, ax, ay, az).setColor(argb).setNormal(pose, dx, dy, dz).setLineWidth(LINE_WIDTH);
        buffer.addVertex(pose, bx, by, bz).setColor(argb).setNormal(pose, dx, dy, dz).setLineWidth(LINE_WIDTH);
    }

    private static void label(SubmitNodeCollector collector, CameraRenderState camera, Recording recording,
                              double top) {
        Font font = Minecraft.getInstance().font;
        if (font == null || camera.orientation == null) {
            return;
        }
        BlockPos pos = recording.blockPos();
        Vec3 cam = camera.pos;
        Matrix4f matrix = new Matrix4f()
                .translate((float) (pos.getX() + 0.5 - cam.x), (float) (top - cam.y),
                        (float) (pos.getZ() + 0.5 - cam.z))
                .rotate(camera.orientation)
                .scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        String name = recording.name;
        Font.PreparedText prepared = font.prepareText(name, -font.width(name) / 2.0f, 0.0f, colour(recording),
                false, BACKDROP);
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
                collector.submitCustomGeometry(IDENTITY, renderable.renderType(Font.DisplayMode.NORMAL),
                        (pose, consumer) -> renderable.render(matrix, consumer, LIGHT, false));
            }
        });
    }
}
