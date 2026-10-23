package com.qza.custom.knife;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import com.qza.QZA;
import com.qza.compat.GlowRender;
import com.qza.compat.Mc;
import com.qza.config.CustomItemEntry;
import com.qza.custom.CustomItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.function.Consumer;

public final class KnifeRenderer implements SpecialModelRenderer<KnifeRenderer.Arg> {
    public static final Identifier TYPE = Identifier.fromNamespaceAndPath(QZA.MOD_ID, "knife");
    public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

    private static final Vector3f SOFTBOX = new Vector3f(0.05f, 0.55f, 0.85f).normalize();
    private static final Vector3f KEY = new Vector3f(-0.6f, 0.7f, 0.4f).normalize();
    private static final Vector3f RIM = new Vector3f(0.85f, 0.2f, -0.5f).normalize();
    private static final Vector3f FILL = new Vector3f(0.6f, -0.1f, 0.8f).normalize();
    private static final float PULL = 0.9996f;
    private static final float GUI_SHINE = 0.7f;

    private static ItemDisplayContext context;
    private static boolean mainHand;
    private static float bladeAngle;
    private static float backAngle;

    public record Arg(String knife, String finish, int seed, String wear, boolean ready) {
    }

    public static final class Unbaked implements SpecialModelRenderer.Unbaked<Arg> {
        @Override
        public SpecialModelRenderer<Arg> bake(SpecialModelRenderer.BakingContext bakingContext) {
            return new KnifeRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    public static void begin(ItemDisplayContext displayContext, boolean main, float blade, float back) {
        context = displayContext;
        mainHand = main;
        bladeAngle = blade;
        backAngle = back;
    }

    public static void end() {
        context = null;
        mainHand = false;
        bladeAngle = 0f;
        backAngle = 0f;
    }

    @Override
    public Arg extractArgument(ItemStack stack) {
        CustomItemEntry entry = CustomItems.active(stack);
        if (entry == null) {
            entry = CustomItems.sharedEntry(stack);
        }
        if (entry == null) {
            entry = CustomItems.previewEntry(stack);
        }
        if (entry == null || !Knives.is(entry.type)) {
            return new Arg(CustomItems.KARAMBIT, "vanilla", 0, "fn", true);
        }
        boolean ready = KnifeTextures.request(entry.type, entry.finish, entry.seed, entry.wear) != null;
        return new Arg(entry.type, entry.finish, entry.seed, entry.wear, ready);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(-0.1f, -0.1f, 0.47f));
        output.accept(new Vector3f(1.1f, 1.1f, 0.53f));
    }

    @Override
    public void submit(Arg arg, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                       boolean foil, int outline) {
        if (arg == null) {
            return;
        }
        Knives.Knife knife = Knives.get(arg.knife());
        Knives.Shape shape = Knives.shape(arg.knife());
        if (knife == null || shape == null) {
            return;
        }
        boolean gui = context == ItemDisplayContext.GUI;
        boolean held = context != null && context != ItemDisplayContext.GUI && context != ItemDisplayContext.GROUND
                && context != ItemDisplayContext.FIXED;
        Identifier texture = KnifeTextures.texture(arg.knife(), arg.finish(), arg.seed(), arg.wear(), held);
        if (texture == null) {
            return;
        }
        Matrix4f[] groups = groups(knife, shape);
        draw(poseStack.last(), poseStack, collector, shape.mesh(), groups, texture, light, overlay, gui);
        if (knife.kind() == Knives.Kind.DAGGERS && mainHand && firstPerson()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && player.getOffhandItem().isEmpty()) {
                Vector3f side = Mc.cameraRotation()
                        .transform(new Vector3f(1f, 0f, 0f));
                Matrix3f reflect = new Matrix3f(
                        1f - 2f * side.x * side.x, -2f * side.x * side.y, -2f * side.x * side.z,
                        -2f * side.y * side.x, 1f - 2f * side.y * side.y, -2f * side.y * side.z,
                        -2f * side.z * side.x, -2f * side.z * side.y, 1f - 2f * side.z * side.z);
                PoseStack mirror = new PoseStack();
                PoseStack.Pose pose = mirror.last();
                pose.pose().set(reflect).mul(poseStack.last().pose());
                pose.normal().set(reflect).mul(poseStack.last().normal());
                draw(pose, mirror, collector, shape.mesh(), groups, texture, light, overlay, false);
            }
        }
    }

    private static boolean firstPerson() {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }

    private static Matrix4f[] groups(Knives.Knife knife, Knives.Shape shape) {
        Matrix4f[] out = {new Matrix4f(), new Matrix4f(), new Matrix4f()};
        float px = shape.pivotX();
        float py = shape.pivotY();
        switch (knife.kind()) {
            case BUTTERFLY -> {
                out[KnifeMesh.BLADE] = around(px, py, bladeAngle);
                out[KnifeMesh.BACK] = around(px, py, backAngle);
            }
            case FOLDING -> out[KnifeMesh.BLADE] = around(px, py, -bladeAngle);
            default -> {
            }
        }
        return out;
    }

    private static Matrix4f around(float x, float y, float degrees) {
        return new Matrix4f().translate(x, y, 0.5f).rotateZ((float) Math.toRadians(degrees)).translate(-x, -y, -0.5f);
    }

    private static void draw(PoseStack.Pose pose, PoseStack stack, SubmitNodeCollector collector, KnifeMesh mesh,
                             Matrix4f[] groups, Identifier texture, int light, int overlay, boolean gui) {
        RenderType base = RenderTypes.entityCutout(texture);
        Matrix4f matrix = new Matrix4f(pose.pose());
        Matrix3f normal = new Matrix3f(pose.normal());
        PoseStack submitted = stack;
        if (stack.last() != pose) {
            submitted = new PoseStack();
            submitted.last().set(pose);
        }
        collector.submitCustomGeometry(submitted, base, (p, out) -> emitBase(mesh, groups, p, out, light, overlay));
        float bright = brightness(light, gui);
        Quaternionf toView = gui ? null
                : new Quaternionf(Mc.cameraRotation()).conjugate();
        GlowRender.submit(collector, texture, List.of(base),
                out -> emitShine(mesh, groups, matrix, normal, toView, out, bright));
    }

    private static void emitBase(KnifeMesh mesh, Matrix4f[] groups, PoseStack.Pose pose, VertexConsumer out, int light,
                                 int overlay) {
        Vector3f p = new Vector3f();
        Vector3f n = new Vector3f();
        for (int g = 0; g < KnifeMesh.GROUPS; g++) {
            float[] d = mesh.group(g);
            Matrix4f gm = groups[g];
            for (int i = 0; i < d.length; i += KnifeMesh.STRIDE) {
                gm.transformPosition(d[i], d[i + 1], d[i + 2], p);
                gm.transformDirection(d[i + 3], d[i + 4], d[i + 5], n);
                out.addVertex(pose, p.x, p.y, p.z).setColor(-1).setUv(d[i + 6], d[i + 7]).setOverlay(overlay)
                        .setLight(light).setNormal(pose, n.x, n.y, n.z);
            }
        }
    }

    private static void emitShine(KnifeMesh mesh, Matrix4f[] groups, Matrix4f matrix, Matrix3f normal,
                                  Quaternionf toView, VertexConsumer out, float bright) {
        boolean gui = toView == null;
        Vector3f p = new Vector3f();
        Vector3f n = new Vector3f();
        Vector3f view = new Vector3f();
        Vector3f r = new Vector3f();
        for (int g = 0; g < KnifeMesh.GROUPS; g++) {
            float[] d = mesh.group(g);
            Matrix4f gm = groups[g];
            for (int i = 0; i < d.length; i += KnifeMesh.STRIDE) {
                gm.transformPosition(d[i], d[i + 1], d[i + 2], p);
                gm.transformDirection(d[i + 3], d[i + 4], d[i + 5], n);
                matrix.transformPosition(p);
                if (gui) {
                    n.normalize();
                    view.set(0f, 0f, -1f);
                } else {
                    normal.transform(n).normalize();
                    view.set(p).normalize();
                    p.mul(PULL);
                }
                float ndv = n.dot(view);
                int colour = 0xFF000000;
                if (ndv < 0f) {
                    r.set(n).mul(-2f * ndv).add(view);
                    if (toView != null) {
                        toView.transform(r);
                    }
                    float facing = 1f + ndv;
                    float fresnel = 1f + 1.4f * facing * facing * facing * facing * facing;
                    float c = Math.min(1f, environment(r) * fresnel * bright);
                    int v = Math.round(c * 255f);
                    colour = 0xFF000000 | (v << 16) | (v << 8) | v;
                }
                out.addVertex(p.x, p.y, p.z).setUv(d[i + 6], d[i + 7] + 0.5f).setColor(colour);
            }
        }
    }

    private static float environment(Vector3f r) {
        float softbox = smooth(0.5f, 0.92f, r.dot(SOFTBOX)) * 0.6f;
        float key = smooth(0.86f, 0.985f, r.dot(KEY)) * 1.5f;
        float rim = smooth(0.84f, 0.97f, r.dot(RIM)) * 0.9f;
        float fill = smooth(0.7f, 0.96f, r.dot(FILL)) * 0.35f;
        float sky = 0.18f * Math.max(0f, r.y);
        float horizon = 0.14f * (float) Math.exp(-r.y * r.y / 0.02f);
        return softbox + key + rim + fill + sky + horizon + 0.04f;
    }

    private static float brightness(int light, boolean gui) {
        if (gui) {
            return GUI_SHINE;
        }
        int block = (light >> 4) & 0xF;
        int sky = (light >> 20) & 0xF;
        float b = Math.max(block, sky) / 15f;
        return 0.18f + 0.82f * b * b;
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }
}
