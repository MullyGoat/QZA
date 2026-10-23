package com.qza.tweaks;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.qza.chat.ChatKeybind;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.CustomItemEntry;
import com.qza.config.QZAConfig;
import com.qza.custom.CustomItemState;
import com.qza.custom.CustomItems;
import com.qza.custom.LiveInspects;
import com.qza.custom.knife.Knives;
import com.qza.gui.QZAScreen;
import com.qza.keybind.CommandKeybinds;
import com.qza.render.HandSpace;
import com.qza.util.IgnUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemInspect {
    public static final double MIN_SPEED = 0.5;
    public static final double MAX_SPEED = 2.0;

    private static final Pattern TYPE_LINE = Pattern.compile(
            "^(?:A )?(?:VERY SPECIAL|SPECIAL|ULTIMATE|ADMIN|DIVINE|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON)\\b(.*)");
    private static final Pattern SWORD_TYPE = Pattern.compile("\\bSWORD\\b");
    private static final Pattern BOW_TYPE = Pattern.compile("\\bBOW\\b");
    private static final Pattern PICKAXE_TYPE = Pattern.compile("\\b(?:PICKAXE|DRILL|GAUNTLET)\\b");
    private static final int TYPE_LINES = 3;
    private static final float CANCEL_SECONDS = 0.18f;
    private static final float RESTART_SECONDS = 0.12f;
    private static final float TP_ARM_PITCH = -1.3f;
    private static final float TP_ARM_YAW = -0.5f;
    private static final float ARM_IN = 0.16f;
    private static final float ARM_OUT = 0.22f;
    private static final float LOW_ARM_TILT = 40f;
    private static final float BLOCK_EDGE = 0.1f;
    private static final double FLAT = 0.1;
    private static final float FLAT_HOLD = 0.22f;

    private static final Matrix4f ARM = new Matrix4f()
            .translate(1.13f / 16f, 3.2f / 16f, 1.13f / 16f)
            .rotateXYZ(0f, rad(-90f), rad(25f))
            .scale(0.68f / 0.85f)
            .rotateZYX(rad(-55f), rad(90f), 0f)
            .translate(0f, -4f / 16f, -0.5f / 16f)
            .translate(-1f / 16f, -2f / 16f, 10f / 16f)
            .rotateY(rad(-180f))
            .rotateX(rad(90f))
            .rotateZ(-0.1f)
            .translate(5f / 16f, -2f / 16f, 0f);
    private static final Vector3f GRIP = HandSpace.GRIP;
    private static final Vector3f FACE = HandSpace.FACE;
    private static final Vector3f UP = HandSpace.UP;
    private static final Vector3f HANDLE = HandSpace.HANDLE;
    private static final Vector3f BOW_GRIP = HandSpace.texel(4f, 6f);
    private static final Vector3f RING = HandSpace.texel(2.5f, 13.5f);
    private static final Vector3f PIVOT = CustomItems.BUTTERFLY_PIVOT;
    private static final Vector3f LIMBS = HandSpace.direction(12.5f, 13f);
    private static final Vector3f ARM_AWAY = new Vector3f(0.05f, -0.6f, 0.15f);
    private static final Vector3f REVERSE_RING = HandSpace.KARAMBIT_REVERSE.transformPosition(RING, new Vector3f());
    private static final Vector3f REVERSE_HOLD = HandSpace.KARAMBIT_REVERSE.transformPosition(HandSpace.KARAMBIT_HOLD,
            new Vector3f());
    private static final long EQUIP_WAIT = 800_000_000L;
    private static final List<String> TOSS_KNIVES = List.of("huntsman", "bowie", "kukri", "classic", "nomad");
    private static final float REVERSE_ARM_TILT = 50f;
    private static final float[] HOLD = {-0.08f, 0.11f, -0.08f, 30f, -12f, 18f, 0.7f, 2.5f, 1.6f, 0f};
    private static final float[] HOLD_KARAMBIT = {-0.06f, -0.1f, -0.1f, 15f, 5f, 20f, 0.6f, 0f, 1.8f, 20f};
    private static final float[] HOLD_DAGGERS = {-0.22f, 0.12f, -0.12f, 10f, -25f, 10f, 0.7f, 0f, 1.6f, 0f};
    private static final float[] NO_HOLD = {0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, Float.NaN};
    private static final Vector3f ARM_LENGTH = new Vector3f(0f, 1f, 0f);
    private static final Vector3f FIST = ARM.invert(new Matrix4f()).transformPosition(HandSpace.GRIP, new Vector3f());

    private static final Frame frame = new Frame();
    private static final Frame restartFrom = new Frame();
    private static long restartAt = -1L;
    private static final Matrix4f heldMatrix = new Matrix4f();
    private static boolean heldReady;
    private static boolean capturing;
    private static Kind kind;
    private static Anim anim;
    private static long started;
    private static long cancelled = -1L;
    private static float frozen;
    private static float weight;
    private static int slot;
    private static Item item;
    private static boolean equipping;
    private static boolean pending;
    private static long pendingSince;
    private static String equipUuid = "";

    private ItemInspect() {
    }

    private enum Kind {
        SWORD,
        PICKAXE,
        BOW,
        OTHER,
        KARAMBIT,
        BUTTERFLY,
        KNIFE,
        SABER
    }

    private enum Pivot {
        GRIP,
        BOW_GRIP,
        RING,
        PIVOT,
        REVERSE_RING,
        REVERSE_HOLD,
        CENTRE
    }

    private interface Motion {
        void pose(float t, Frame f, boolean block);
    }

    private record Anim(float length, Motion motion, Pivot turnAt, Pivot spinAt, Vector3f spinAxis,
                        Pivot twirlAt, Vector3f twirlAxis, float armTilt, boolean reverse, float again) {
        private Anim(float length, Motion motion, Pivot turnAt, Pivot spinAt, Vector3f spinAxis,
                     Pivot twirlAt, Vector3f twirlAxis) {
            this(length, motion, turnAt, spinAt, spinAxis, twirlAt, twirlAxis, Float.NaN, false, 0f);
        }

        private Anim(float length, Motion motion, Pivot turnAt, Pivot spinAt, Vector3f spinAxis,
                     Pivot twirlAt, Vector3f twirlAxis, float armTilt, boolean reverse) {
            this(length, motion, turnAt, spinAt, spinAxis, twirlAt, twirlAxis, armTilt, reverse, 0f);
        }

        private Anim from(float time) {
            return new Anim(length, motion, turnAt, spinAt, spinAxis, twirlAt, twirlAxis, armTilt, reverse, time);
        }
    }

    private static final class Frame {
        private final Vector3f move = new Vector3f();
        private final Vector3f lift = new Vector3f();
        private float yaw;
        private float pitch;
        private float roll;
        private float turnX;
        private float turnY;
        private float turnZ;
        private float spin;
        private float twirl;
        private float blade;
        private float back;
        private float arm;

        private void reset() {
            move.zero();
            lift.zero();
            yaw = 0f;
            pitch = 0f;
            roll = 0f;
            turnX = 0f;
            turnY = 0f;
            turnZ = 0f;
            spin = 0f;
            twirl = 0f;
            blade = 0f;
            back = 0f;
            arm = 0f;
        }

        private void scale(float w) {
            move.mul(w);
            lift.mul(w);
            yaw *= w;
            pitch *= w;
            roll *= w;
            turnX = Mth.wrapDegrees(turnX) * w;
            turnY = Mth.wrapDegrees(turnY) * w;
            turnZ = Mth.wrapDegrees(turnZ) * w;
            spin = Mth.wrapDegrees(spin) * w;
            twirl = Mth.wrapDegrees(twirl) * w;
            blade = Mth.wrapDegrees(blade) * w;
            back = Mth.wrapDegrees(back) * w;
            arm *= w;
        }

        private void copy(Frame from) {
            move.set(from.move);
            lift.set(from.lift);
            yaw = from.yaw;
            pitch = from.pitch;
            roll = from.roll;
            turnX = from.turnX;
            turnY = from.turnY;
            turnZ = from.turnZ;
            spin = from.spin;
            twirl = from.twirl;
            blade = from.blade;
            back = from.back;
            arm = from.arm;
        }

        private void blend(Frame from, float k) {
            move.set(from.move).lerp(move, k);
            lift.set(from.lift).lerp(lift, k);
            yaw = Mth.lerp(k, from.yaw, yaw);
            pitch = Mth.lerp(k, from.pitch, pitch);
            roll = Mth.lerp(k, from.roll, roll);
            turnX = from.turnX + Mth.wrapDegrees(turnX - from.turnX) * k;
            turnY = from.turnY + Mth.wrapDegrees(turnY - from.turnY) * k;
            turnZ = from.turnZ + Mth.wrapDegrees(turnZ - from.turnZ) * k;
            spin = from.spin + Mth.wrapDegrees(spin - from.spin) * k;
            twirl = from.twirl + Mth.wrapDegrees(twirl - from.twirl) * k;
            blade = from.blade + Mth.wrapDegrees(blade - from.blade) * k;
            back = from.back + Mth.wrapDegrees(back - from.back) * k;
            arm = Mth.lerp(k, from.arm, arm);
        }
    }

    private static final class Track {
        private final float[] times;
        private final float[] values;
        private final float[] slopes;

        private Track(float[] keys) {
            int count = keys.length / 2;
            times = new float[count];
            values = new float[count];
            slopes = new float[count];
            for (int i = 0; i < count; i++) {
                times[i] = keys[i * 2];
                values[i] = keys[i * 2 + 1];
            }
            for (int i = 1; i < count - 1; i++) {
                float h0 = times[i] - times[i - 1];
                float h1 = times[i + 1] - times[i];
                float d0 = (values[i] - values[i - 1]) / h0;
                float d1 = (values[i + 1] - values[i]) / h1;
                if (d0 * d1 <= 0f) {
                    continue;
                }
                float w0 = 2f * h1 + h0;
                float w1 = h1 + 2f * h0;
                slopes[i] = (w0 + w1) / (w0 / d0 + w1 / d1);
            }
        }

        private static Track of(float... keys) {
            return new Track(keys);
        }

        private float at(float t) {
            int last = times.length - 1;
            if (t <= times[0]) {
                return values[0];
            }
            if (t >= times[last]) {
                return values[last];
            }
            int i = 0;
            while (t > times[i + 1]) {
                i++;
            }
            float h = times[i + 1] - times[i];
            float s = (t - times[i]) / h;
            float s2 = s * s;
            float s3 = s2 * s;
            return (2f * s3 - 3f * s2 + 1f) * values[i] + (s3 - 2f * s2 + s) * h * slopes[i]
                    + (3f * s2 - 2f * s3) * values[i + 1] + (s3 - s2) * h * slopes[i + 1];
        }
    }

    private static final class Twirl {
        private static final Track X = Track.of(0f, 0f, 0.22f, -0.05f, 0.42f, -0.02f, 0.62f, -0.14f, 1.55f, -0.12f,
                1.85f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.22f, 0.14f, 0.42f, 0.04f, 0.62f, 0.3f, 1.05f, 0.32f,
                1.55f, 0.28f, 1.85f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.62f, -0.06f, 1.55f, -0.06f, 1.85f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.22f, 35f, 0.62f, 45f, 1.55f, 45f, 1.85f, 0f);
        private static final Track PITCH = Track.of(0f, 0f, 0.22f, -8f, 0.42f, 12f, 0.62f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.22f, 22f, 0.42f, 35f, 0.62f, 15f, 1.55f, 15f,
                1.85f, 0f);
        private static final Track SPIN = Track.of(0.5f, 0f, 1.6f, 1080f);
        private static final Track FRONT = Track.of(0.45f, 0f, 0.6f, 0.12f, 1.5f, 0.12f, 1.65f, 0f);
        private static final Anim ANIM = new Anim(2.0f, Twirl::pose, Pivot.CENTRE, Pivot.GRIP, FACE, Pivot.GRIP, FACE).from(0.22f);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.pitch = PITCH.at(t);
            f.roll = ROLL.at(t);
            f.spin = SPIN.at(t);
            f.lift.set(FACE).mul(FRONT.at(t));
        }
    }

    private static final class Balance {
        private static final Track X = Track.of(0f, 0f, 0.3f, -0.22f, 2.15f, -0.22f, 2.6f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.1f, 1.85f, 0.1f, 1.95f, 0.05f, 2.1f, 0.1f,
                2.6f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.3f, 30f, 2.15f, 30f, 2.6f, 0f);
        private static final Track RISE = Track.of(0.3f, 0f, 0.42f, 0.26f, 0.55f, 0.16f, 1.75f, 0.16f,
                1.92f, 0f);
        private static final Track BACK = Track.of(0.3f, 0f, 0.5f, -0.085f, 1.75f, -0.085f, 1.92f, 0f);
        private static final Track UPRIGHT = Track.of(0.3f, 0f, 0.5f, -20f, 1.75f, -20f, 1.92f, 0f);
        private static final Track TOP = Track.of(0.45f, 0f, 1.75f, 540f, 1.95f, 720f);
        private static final Anim ANIM = new Anim(2.6f, Balance::pose, Pivot.CENTRE, Pivot.GRIP, HANDLE,
                Pivot.GRIP, FACE).from(0.3f);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), 0f);
            f.yaw = YAW.at(t);
            f.lift.set(0f, RISE.at(t), BACK.at(t));
            f.turnX = UPRIGHT.at(t);
            f.turnY = TOP.at(t);
            f.turnZ = 6f * Mth.sin(t * 1.3f * Mth.TWO_PI) * window(t, 0.55f, 1.75f);
        }
    }

    private static final class Boomerang {
        private static final float THROWN = 0.44f;
        private static final float CAUGHT = 2.14f;
        private static final float DISTANCE = 3.0f;
        private static final float WIDTH = 0.9f;
        private static final float RISE = 0.3f;
        private static final Track X = Track.of(0f, 0f, 0.3f, 0.08f, 0.45f, -0.22f, 0.7f, -0.12f, 1.9f, -0.08f,
                2.15f, 0f, 2.3f, -0.02f, 2.8f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.04f, 0.45f, 0.16f, 0.7f, 0.1f, 1.9f, 0.1f,
                2.15f, 0.06f, 2.3f, 0.1f, 2.8f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.3f, 0.06f, 0.45f, -0.12f, 0.7f, 0f, 2.8f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.3f, -25f, 0.45f, 25f, 0.7f, 10f, 2.3f, 10f, 2.8f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.3f, -30f, 0.45f, 10f, 0.7f, -5f, 2.3f, -5f, 2.8f, 0f);
        private static final Track FACING = Track.of(0f, 0f, 0.3f, 240f, 2.3f, 240f, 2.8f, 360f);
        private static final Anim ANIM = new Anim(2.8f, Boomerang::pose, Pivot.CENTRE, Pivot.BOW_GRIP, LIMBS,
                Pivot.CENTRE, FACE).from(0.3f);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.roll = ROLL.at(t);
            f.spin = FACING.at(t);
            float air = air(t, THROWN, CAUGHT);
            if (air >= 0f) {
                float out = Mth.sin(air * Mth.PI);
                f.lift.set(-WIDTH * Mth.sin(air * Mth.TWO_PI), RISE * out, -DISTANCE * out);
                f.twirl = -2520f * air;
                f.turnX = -40f * out;
            }
        }
    }

    private static final class Juggle {
        private static final float FIRST = 0.5f;
        private static final float FIRST_CAUGHT = 0.95f;
        private static final float SECOND = 1.18f;
        private static final float SECOND_CAUGHT = 1.95f;
        private static final Track X = Track.of(0f, 0f, 0.3f, -0.22f, 2.1f, -0.22f, 2.5f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.14f, 0.42f, 0.07f, 0.52f, 0.16f, 0.9f, 0.13f,
                0.98f, 0.06f, 1.1f, 0.04f, 1.2f, 0.19f, 1.85f, 0.13f, 1.96f, 0.05f, 2.1f, 0.12f, 2.5f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.3f, -0.02f, 2.1f, -0.02f, 2.5f, 0f);
        private static final Anim ANIM = new Anim(2.5f, Juggle::pose, Pivot.CENTRE, Pivot.CENTRE, UP,
                Pivot.CENTRE, FACE).from(0.3f);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t) * (block ? 2f : 1f), Z.at(t));
            float first = air(t, FIRST, FIRST_CAUGHT);
            if (first >= 0f) {
                f.lift.y = 0.22f * 4f * first * (1f - first);
                f.turnY = 360f * first;
            }
            float second = air(t, SECOND, SECOND_CAUGHT);
            if (second >= 0f) {
                float arc = 4f * second * (1f - second);
                f.lift.set(-0.12f * arc, 0.32f * arc, 0f);
                f.turnX = -360f * second;
            }
        }
    }

    private static final class KarambitEquip {
        private static final Track Y = Track.of(0f, -0.04f, 0.2f, 0.05f, 0.55f, 0.02f, 0.8f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.2f, -12f, 0.55f, 4f, 0.8f, 0f);
        private static final Track SPIN = Track.of(0.05f, 0f, 0.5f, -360f);
        private static final Anim ANIM = new Anim(0.8f, KarambitEquip::pose, Pivot.REVERSE_HOLD, Pivot.REVERSE_RING,
                FACE, Pivot.REVERSE_RING, FACE, REVERSE_ARM_TILT, true);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(0f, Y.at(t), 0f);
            f.roll = ROLL.at(t);
            f.spin = SPIN.at(t);
        }
    }

    private static final class FixedDraw {
        private static final Track Y = Track.of(0f, -0.06f, 0.35f, 0.03f, 0.75f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.2f, -10f, 0.5f, 3f, 0.75f, 0f);
        private static final Track SPIN = Track.of(0f, -360f, 0.5f, 0f);
        private static final Anim ANIM = new Anim(0.75f, FixedDraw::pose, Pivot.GRIP, Pivot.GRIP, FACE, Pivot.GRIP, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(0f, Y.at(t), 0f);
            f.roll = ROLL.at(t);
            f.spin = SPIN.at(t);
        }
    }

    private static final class FoldDraw {
        private static final Track Y = Track.of(0f, -0.03f, 0.3f, 0.02f, 0.65f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.12f, 0f, 0.27f, -16f, 0.45f, 3f, 0.65f, 0f);
        private static final Track BLADE = Track.of(0f, 180f, 0.1f, 180f, 0.3f, -10f, 0.4f, 4f, 0.5f, 0f);
        private static final Anim ANIM = new Anim(0.65f, FoldDraw::pose, Pivot.GRIP, Pivot.GRIP, FACE, Pivot.GRIP, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(0f, Y.at(t), 0f);
            f.roll = ROLL.at(t);
            f.blade = BLADE.at(t);
        }
    }

    private static final class DaggerDraw {
        private static final Track Y = Track.of(0f, -0.1f, 0.35f, 0.02f, 0.6f, 0f);
        private static final Track PITCH = Track.of(0f, -30f, 0.4f, 4f, 0.6f, 0f);
        private static final Anim ANIM = new Anim(0.6f, DaggerDraw::pose, Pivot.GRIP, Pivot.GRIP, FACE, Pivot.GRIP, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(0f, Y.at(t), 0f);
            f.pitch = PITCH.at(t);
        }
    }

    private static final class ButterflyEquip {
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.04f, 0.8f, 0.03f, 1.0f, 0f);
        private static final Track BLADE = Track.of(0f, 180f, 0.25f, 180f, 0.5f, 360f);
        private static final Track BACK = Track.of(0.08f, 0f, 0.3f, 180f, 0.55f, 180f, 0.78f, 360f);
        private static final Track SPIN = Track.of(0.3f, 0f, 0.7f, 360f);
        private static final Anim ANIM = new Anim(1.0f, ButterflyEquip::pose, Pivot.GRIP, Pivot.PIVOT, FACE,
                Pivot.PIVOT, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(0f, Y.at(t), 0f);
            f.blade = BLADE.at(t);
            f.back = BACK.at(t);
            f.spin = SPIN.at(t);
        }
    }

    private static final class BladeFlip {
        private static final Track X = Track.of(0f, 0f, 0.45f, -0.17f, 1.3f, -0.2f, 2.2f, -0.18f, 2.3f, -0.2f,
                3.05f, -0.22f, 3.5f, -0.15f, 4.1f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.45f, 0.1f, 1.3f, 0.13f, 2.2f, 0.12f, 2.35f, 0.1f,
                2.6f, 0.2f, 3.05f, 0.15f, 3.5f, 0.12f, 4.1f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.45f, -0.02f, 3.5f, -0.02f, 4.1f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.45f, 30f, 1.3f, 55f, 1.75f, 20f, 2.25f, 25f, 3.05f, 25f,
                3.5f, 35f, 3.85f, -4f, 4.1f, 0f);
        private static final Track PITCH = Track.of(0f, 0f, 0.45f, -10f, 1.3f, -16f, 2.25f, -6f, 3.05f, -6f,
                3.5f, -10f, 4.1f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.45f, 16f, 1.3f, -10f, 1.75f, 8f, 2.25f, 12f, 3.05f, 12f,
                3.5f, 10f, 3.85f, -3f, 4.1f, 0f);
        private static final Track TWIRL = Track.of(1.3f, 0f, 1.75f, 180f, 3.05f, 180f, 3.45f, 360f);
        private static final Track SPIN = Track.of(2.25f, 0f, 2.4f, -25f, 2.95f, 375f, 3.1f, 360f);
        private static final Anim ANIM = new Anim(4.1f, BladeFlip::pose, Pivot.GRIP, Pivot.GRIP, FACE, Pivot.GRIP, HANDLE).from(0.45f);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.pitch = PITCH.at(t);
            f.roll = ROLL.at(t);
            f.twirl = TWIRL.at(t);
            f.spin = SPIN.at(t);
        }
    }

    private static final class HeavyToss {
        private static final float THROWN = 1.05f;
        private static final float CAUGHT = 1.85f;
        private static final float HEIGHT = 0.42f;
        private static final Track X = Track.of(0f, 0f, 0.45f, -0.16f, 1.05f, -0.15f, 1.85f, -0.17f, 3.6f, -0.16f,
                4.4f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.45f, 0.12f, 0.6f, 0.14f, 0.72f, 0.1f, 0.84f, 0.14f,
                0.95f, 0.07f, 1.05f, 0.15f, 1.3f, 0.19f, 1.8f, 0.14f, 1.9f, 0.07f, 2.05f, 0.12f, 3.6f, 0.12f,
                3.75f, 0.15f, 4.4f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.45f, 35f, 1.05f, 30f, 2.05f, 30f, 2.5f, 50f, 3.0f, 50f,
                3.6f, 30f, 4.1f, -4f, 4.4f, 0f);
        private static final Track PITCH = Track.of(0f, 0f, 0.45f, -8f, 0.95f, -4f, 1.05f, -14f, 1.9f, 4f, 2.05f, -6f,
                3.6f, -6f, 4.4f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.45f, 10f, 2.05f, 10f, 2.5f, 22f, 3.0f, 22f, 3.6f, 10f,
                4.1f, -3f, 4.4f, 0f);
        private static final Track TWIRL = Track.of(2.1f, 0f, 2.45f, -75f, 2.95f, -75f, 3.25f, 0f);
        private static final Track FLOURISH = Track.of(3.2f, 0f, 3.6f, 360f);
        private static final Anim ANIM = new Anim(4.4f, HeavyToss::pose, Pivot.CENTRE, Pivot.GRIP, FACE,
                Pivot.GRIP, HANDLE).from(0.45f);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), 0f);
            f.yaw = YAW.at(t);
            f.pitch = PITCH.at(t);
            f.roll = ROLL.at(t);
            f.twirl = TWIRL.at(t);
            f.spin = FLOURISH.at(t);
            float air = air(t, THROWN, CAUGHT);
            if (air >= 0f) {
                float arc = 4f * air * (1f - air);
                f.lift.set(-0.05f * arc, HEIGHT * arc, 0f);
                f.turnX = 720f * air;
            }
        }
    }

    private static final class FoldTrick {
        private static final Track X = Track.of(0f, 0f, 0.4f, -0.16f, 2.9f, -0.18f, 3.5f, -0.17f, 4.3f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.4f, 0.12f, 0.95f, 0.13f, 1.0f, 0.1f, 1.1f, 0.13f,
                2.45f, 0.13f, 2.9f, 0.15f, 3.5f, 0.13f, 4.3f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.4f, 30f, 2.45f, 30f, 2.9f, 50f, 3.5f, 45f, 4.0f, -4f,
                4.3f, 0f);
        private static final Track PITCH = Track.of(0f, 0f, 0.4f, -8f, 2.9f, -8f, 3.2f, -14f, 3.5f, -8f, 4.3f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.4f, 10f, 0.7f, -6f, 0.86f, 22f, 1.0f, 10f, 2.2f, 4f,
                2.33f, 20f, 2.45f, 10f, 3.5f, 12f, 4.0f, -3f, 4.3f, 0f);
        private static final Track BLADE = Track.of(0.4f, 0f, 0.7f, 180f, 0.8f, 180f, 0.92f, -12f, 1.02f, 5f,
                1.1f, 0f, 1.9f, 0f, 2.15f, 180f, 2.25f, 180f, 2.37f, -12f, 2.47f, 4f, 2.55f, 0f);
        private static final Track SPIN = Track.of(1.05f, 0f, 1.85f, 720f);
        private static final Track TWIRL = Track.of(2.45f, 0f, 2.85f, 180f, 3.45f, 180f, 3.85f, 360f);
        private static final Anim ANIM = new Anim(4.3f, FoldTrick::pose, Pivot.GRIP, Pivot.GRIP, FACE, Pivot.GRIP, HANDLE).from(0.4f);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), 0f);
            f.yaw = YAW.at(t);
            f.pitch = PITCH.at(t);
            f.roll = ROLL.at(t);
            f.blade = BLADE.at(t);
            f.spin = SPIN.at(t);
            f.twirl = TWIRL.at(t);
        }
    }

    private static final class KarambitSpin {
        private static final Track X = Track.of(0f, 0f, 0.35f, -0.1f, 1.55f, -0.1f, 1.95f, -0.18f, 2.4f, -0.18f,
                3.5f, -0.12f, 4.2f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.35f, 0.08f, 1.55f, 0.08f, 1.95f, 0.15f, 2.4f, 0.15f,
                3.1f, 0.1f, 3.3f, 0.14f, 3.5f, 0.1f, 4.2f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.35f, 5f, 1.55f, 5f, 1.95f, 45f, 2.4f, 50f, 3.1f, 15f,
                3.5f, 10f, 4.0f, -3f, 4.2f, 0f);
        private static final Track PITCH = Track.of(0f, 0f, 1.55f, 0f, 1.95f, -15f, 2.4f, -18f, 3.1f, -4f, 4.2f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.35f, -8f, 1.35f, -8f, 1.45f, 6f, 1.55f, -4f, 3.1f, -4f,
                3.5f, -6f, 4.0f, 2f, 4.2f, 0f);
        private static final Track SPIN = Track.of(0.35f, 0f, 0.6f, -300f, 1.0f, -780f, 1.35f, -1100f, 1.5f, -1080f,
                2.4f, -1080f, 2.75f, -720f, 3.1f, -360f, 3.5f, 0f);
        private static final Anim ANIM = new Anim(4.2f, KarambitSpin::pose, Pivot.REVERSE_HOLD, Pivot.REVERSE_RING,
                FACE, Pivot.REVERSE_RING, FACE, REVERSE_ARM_TILT, true).from(0.35f);

        private static void pose(float t, Frame f, boolean block) {
            float drive = window(t, 0.35f, 1.35f);
            f.move.set(X.at(t) + 0.012f * drive * Mth.sin(t * 3f * Mth.TWO_PI),
                    Y.at(t) + 0.012f * drive * Mth.cos(t * 3f * Mth.TWO_PI), 0f);
            f.yaw = YAW.at(t);
            f.pitch = PITCH.at(t);
            f.roll = ROLL.at(t);
            f.spin = SPIN.at(t);
        }
    }

    private static final class BalisongTricks {
        private static final float THROWN = 1.25f;
        private static final float CAUGHT = 1.9f;
        private static final Track X = Track.of(0f, 0f, 0.3f, -0.2f, 3.3f, -0.2f, 3.8f, -0.18f, 4.4f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.18f, 1.2f, 0.18f, 1.25f, 0.12f, 1.4f, 0.2f,
                1.85f, 0.18f, 1.95f, 0.11f, 2.1f, 0.18f, 3.8f, 0.18f, 4.4f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.3f, -0.05f, 3.8f, -0.05f, 4.4f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.3f, 40f, 3.3f, 40f, 3.6f, 60f, 3.8f, 55f, 4.2f, -4f,
                4.4f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.3f, 10f, 3.3f, 10f, 3.6f, 18f, 4.2f, -3f, 4.4f, 0f);
        private static final Track BACK = Track.of(0.3f, 0f, 0.42f, 180f, 0.54f, 360f, 0.66f, 540f, 0.78f, 720f,
                0.9f, 900f, 1.02f, 1080f, 1.25f, 1080f, 1.9f, 1800f, 2.6f, 2160f, 2.75f, 2340f, 2.9f, 2520f,
                3.05f, 2700f, 3.2f, 2880f);
        private static final Track BLADE = Track.of(0.42f, 0f, 0.6f, 180f, 0.84f, 180f, 1.02f, 360f, 2.75f, 360f,
                2.95f, 540f, 3.05f, 540f, 3.2f, 720f);
        private static final Track SPIN = Track.of(1.95f, 0f, 2.6f, 720f);
        private static final Anim ANIM = new Anim(4.4f, BalisongTricks::pose, Pivot.CENTRE, Pivot.PIVOT, FACE,
                Pivot.PIVOT, FACE).from(0.3f);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.roll = ROLL.at(t);
            f.back = BACK.at(t);
            f.blade = BLADE.at(t);
            f.spin = SPIN.at(t);
            float air = air(t, THROWN, CAUGHT);
            if (air >= 0f) {
                float arc = 4f * air * (1f - air);
                f.lift.set(-0.04f * arc, 0.32f * arc, 0f);
                f.turnX = -360f * air;
            }
        }
    }

    private static final class DaggerTwirl {
        private static final Track X = Track.of(0f, 0f, 0.4f, -0.08f, 1.2f, -0.08f, 1.6f, -0.06f, 2.4f, -0.1f,
                3.2f, -0.08f, 3.8f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.4f, 0.08f, 1.2f, 0.08f, 1.6f, 0.06f, 2.4f, 0.1f,
                3.2f, 0.09f, 3.8f, 0f);
        private static final Track Z = Track.of(0f, 0f, 1.2f, 0f, 1.32f, -0.1f, 1.45f, -0.02f, 1.6f, 0f, 3.8f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.4f, -10f, 1.6f, -10f, 2.0f, -25f, 2.4f, -28f, 3.2f, -12f,
                3.6f, 3f, 3.8f, 0f);
        private static final Track PITCH = Track.of(0f, 0f, 1.2f, 0f, 1.32f, -22f, 1.6f, -4f, 3.8f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.4f, 8f, 1.6f, 8f, 2.0f, 18f, 2.4f, 20f, 3.2f, 10f,
                3.6f, -3f, 3.8f, 0f);
        private static final Track SPIN = Track.of(0.4f, 0f, 1.15f, 720f, 2.6f, 720f, 3.2f, 1080f);
        private static final Track TWIRL = Track.of(1.9f, 0f, 2.3f, 180f, 2.45f, 180f, 2.85f, 360f);
        private static final Anim ANIM = new Anim(3.8f, DaggerTwirl::pose, Pivot.GRIP, Pivot.GRIP, FACE, Pivot.GRIP, HANDLE).from(0.4f);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.pitch = PITCH.at(t);
            f.roll = ROLL.at(t);
            f.spin = SPIN.at(t);
            f.twirl = TWIRL.at(t);
        }
    }

    private static final class SaberFlourish {
        private static final Track X = Track.of(0f, 0f, 0.4f, -0.24f, 1.3f, -0.24f, 1.6f, -0.02f, 1.85f, -0.4f,
                2.05f, -0.42f, 2.45f, -0.08f, 2.9f, -0.22f, 3.15f, -0.3f, 3.6f, -0.3f, 4.2f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.4f, 0.34f, 1.3f, 0.34f, 1.6f, 0.4f, 1.85f, 0.3f,
                2.05f, 0.28f, 2.45f, 0.38f, 2.9f, 0.34f, 3.15f, 0.26f, 3.6f, 0.26f, 4.2f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.4f, -0.16f, 1.3f, -0.16f, 1.6f, -0.08f, 1.85f, -0.16f,
                2.45f, -0.1f, 3.15f, -0.1f, 3.6f, -0.1f, 4.2f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.4f, 10f, 1.3f, 10f, 1.85f, 20f, 2.45f, 5f, 3.15f, 25f,
                3.6f, 25f, 4.0f, -3f, 4.2f, 0f);
        private static final Track SWEEP = Track.of(0f, 0f, 0.4f, 20f, 0.55f, 60f, 1.15f, 700f, 1.3f, 740f,
                1.6f, 680f, 1.85f, 800f, 2.05f, 810f, 2.45f, 680f, 2.9f, 340f, 3.15f, 360f, 4.2f, 360f);
        private static final Anim ANIM = new Anim(4.2f, SaberFlourish::pose, Pivot.GRIP, Pivot.GRIP, FACE,
                Pivot.GRIP, FACE).from(0.4f);

        private static void pose(float t, Frame f, boolean block) {
            float circle = window(t, 0.55f, 1.15f);
            f.move.set(X.at(t) + 0.03f * circle * Mth.sin(t * 2f * Mth.TWO_PI),
                    Y.at(t) + 0.03f * circle * Mth.cos(t * 2f * Mth.TWO_PI), Z.at(t));
            f.yaw = YAW.at(t);
            f.turnZ = SWEEP.at(t);
        }
    }

    private static final Map<String, Anim> NAMES = Map.ofEntries(
            Map.entry("Twirl", Twirl.ANIM),
            Map.entry("Balance", Balance.ANIM),
            Map.entry("Boomerang", Boomerang.ANIM),
            Map.entry("Juggle", Juggle.ANIM),
            Map.entry("BladeFlip", BladeFlip.ANIM),
            Map.entry("HeavyToss", HeavyToss.ANIM),
            Map.entry("FoldTrick", FoldTrick.ANIM),
            Map.entry("KarambitSpin", KarambitSpin.ANIM),
            Map.entry("BalisongTricks", BalisongTricks.ANIM),
            Map.entry("DaggerTwirl", DaggerTwirl.ANIM),
            Map.entry("SaberFlourish", SaberFlourish.ANIM));
    private static final Map<String, Kind> KINDS = Map.ofEntries(
            Map.entry("Twirl", Kind.SWORD),
            Map.entry("Balance", Kind.PICKAXE),
            Map.entry("Boomerang", Kind.BOW),
            Map.entry("Juggle", Kind.OTHER),
            Map.entry("BladeFlip", Kind.KNIFE),
            Map.entry("HeavyToss", Kind.KNIFE),
            Map.entry("FoldTrick", Kind.KNIFE),
            Map.entry("KarambitSpin", Kind.KARAMBIT),
            Map.entry("BalisongTricks", Kind.BUTTERFLY),
            Map.entry("DaggerTwirl", Kind.KNIFE),
            Map.entry("SaberFlourish", Kind.SABER));
    private static final Map<Anim, String> NAME_OF = nameOf();
    private static final Map<Integer, Remote> REMOTES = new HashMap<>();

    private static Map<Anim, String> nameOf() {
        Map<Anim, String> names = new IdentityHashMap<>();
        NAMES.forEach((name, a) -> names.put(a, name));
        return names;
    }

    private static final class Remote {
        private final Anim anim;
        private final Kind kind;
        private final long started;
        private final float speed;
        private final Item item;
        private final Frame frame = new Frame();
        private final Frame from;
        private final long fromAt;
        private long cancelled = -1L;
        private float frozen;
        private float weight = 1f;
        private float now;

        private Remote(Anim anim, Kind kind, long started, float speed, Item item, Frame was, long at) {
            this.anim = anim;
            this.kind = kind;
            this.started = started;
            this.speed = speed;
            this.item = item;
            if (was != null) {
                this.from = new Frame();
                this.from.copy(was);
                this.fromAt = at;
            } else {
                this.from = null;
                this.fromAt = -1L;
            }
        }

        private float time(long nanos, Player player) {
            if (cancelled < 0L && (!player.getMainHandItem().is(item) || Mc.swinging(player) || player.isUsingItem())) {
                cancelled = nanos;
                frozen = (float) ((nanos - started) / 1.0e9 * speed);
            }
            if (cancelled >= 0L) {
                float since = (nanos - cancelled) / 1.0e9f;
                if (since >= CANCEL_SECONDS) {
                    return -1f;
                }
                weight = 1f - smooth(since / CANCEL_SECONDS);
                return frozen;
            }
            float t = (float) ((nanos - started) / 1.0e9 * speed);
            return t >= anim.length() ? -1f : t;
        }
    }

    public static boolean capturing() {
        return capturing;
    }

    public static void startCapture() {
        capturing = true;
    }

    public static String keyLabel() {
        return CommandKeybinds.keyLabel(ConfigManager.get().itemInspectKey);
    }

    public static float karambitGrip() {
        if (kind != Kind.KARAMBIT || anim == null) {
            return 1f;
        }
        return anim.reverse() ? 1f : 1f - frame.arm;
    }

    public static void equip(ItemStack stack, String uuid, String type) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || stack.isEmpty() || busy(player)) {
            return;
        }
        Knives.Knife knife = Knives.get(type);
        if (knife == null) {
            return;
        }
        kind = switch (knife.kind()) {
            case KARAMBIT -> Kind.KARAMBIT;
            case BUTTERFLY -> Kind.BUTTERFLY;
            default -> Kind.KNIFE;
        };
        anim = switch (knife.kind()) {
            case KARAMBIT -> KarambitEquip.ANIM;
            case BUTTERFLY -> ButterflyEquip.ANIM;
            case FOLDING -> FoldDraw.ANIM;
            case DAGGERS -> DaggerDraw.ANIM;
            default -> FixedDraw.ANIM;
        };
        equipping = true;
        pending = true;
        pendingSince = Util.getNanos();
        equipUuid = uuid;
        started = pendingSince;
        cancelled = -1L;
        slot = player.getInventory().getSelectedSlot();
        item = stack.getItem();
    }

    public static float butterflyBlade() {
        return kind == Kind.BUTTERFLY ? frame.blade : 0f;
    }

    public static float butterflyHandle() {
        return kind == Kind.BUTTERFLY ? frame.back : 0f;
    }

    public static float knifeFold() {
        return kind == Kind.KNIFE ? frame.blade : 0f;
    }

    public static boolean onKey(KeyEvent event, int action) {
        QZAConfig cfg = ConfigManager.get();
        if (capturing) {
            if (!(Mc.screen() instanceof QZAScreen)) {
                capturing = false;
                return false;
            }
            if (action != InputConstants.PRESS) {
                return false;
            }
            capturing = false;
            int code = event.key();
            if (code != InputConstants.KEY_ESCAPE) {
                cfg.itemInspectKey = code == InputConstants.KEY_DELETE || code == InputConstants.KEY_BACKSPACE
                        ? "" : InputConstants.getKey(event).getName();
                ConfigManager.save();
            }
            ChatKeybind.swallowNextChar(event);
            return true;
        }
        String key = cfg.itemInspectKey;
        if (action == InputConstants.PRESS && cfg.itemInspectEnabled && key != null && !key.isBlank()
                && Mc.screen() == null && Mc.overlay() == null && InputConstants.getKey(event).getName().equals(key)) {
            start();
        }
        return false;
    }

    public static void start() {
        LocalPlayer player = Minecraft.getInstance().player;
        long now = Util.getNanos();
        if (player == null || !ConfigManager.get().itemInspectEnabled) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || busy(player)) {
            return;
        }
        Kind next = kindOf(stack);
        Anim nextAnim = animFor(next, stack);
        boolean again = kind != null && !equipping && !pending && cancelled < 0L && anim == nextAnim
                && elapsed(now) < anim.length();
        if (again && elapsed(now) <= anim.again()) {
            return;
        }
        if (kind != null && !pending) {
            restartFrom.copy(frame);
            restartAt = now;
        } else {
            restartAt = -1L;
        }
        kind = next;
        anim = nextAnim;
        equipping = false;
        pending = false;
        started = again ? now - (long) (anim.again() / speed() * 1.0e9) : now;
        LiveInspects.sent(NAME_OF.get(anim), speed());
        cancelled = -1L;
        slot = player.getInventory().getSelectedSlot();
        item = stack.getItem();
    }

    private static Anim animFor(Kind kind, ItemStack stack) {
        return switch (kind) {
            case SWORD -> Twirl.ANIM;
            case PICKAXE -> Balance.ANIM;
            case BOW -> Boomerang.ANIM;
            case KARAMBIT -> KarambitSpin.ANIM;
            case BUTTERFLY -> BalisongTricks.ANIM;
            case KNIFE -> knifeAnim(stack);
            case SABER -> SaberFlourish.ANIM;
            default -> Juggle.ANIM;
        };
    }

    private static Anim knifeAnim(ItemStack stack) {
        CustomItemEntry entry = CustomItems.active(stack);
        Knives.Knife knife = entry == null ? null : Knives.get(entry.type);
        if (knife == null) {
            return BladeFlip.ANIM;
        }
        return switch (knife.kind()) {
            case FOLDING -> FoldTrick.ANIM;
            case DAGGERS -> DaggerTwirl.ANIM;
            default -> TOSS_KNIVES.contains(knife.id()) ? HeavyToss.ANIM : BladeFlip.ANIM;
        };
    }

    private static boolean busy(LocalPlayer player) {
        boolean swinging = Mc.swinging(player) && !ConfigManager.get().itemInspectKeepOnSwing;
        return player.isUsingItem() || swinging || SwordBlock.localBlocking();
    }

    private static Kind kindOf(ItemStack stack) {
        if (CustomItems.is(stack, CustomItems.KARAMBIT)) {
            return Kind.KARAMBIT;
        }
        if (CustomItems.is(stack, CustomItems.BUTTERFLY)) {
            return Kind.BUTTERFLY;
        }
        if (CustomItems.is(stack, CustomItems.LIGHTSABER)) {
            return Kind.SABER;
        }
        CustomItemEntry custom = CustomItems.active(stack);
        if (custom != null && Knives.is(custom.type)) {
            return Kind.KNIFE;
        }
        String type = typeLine(stack);
        if (SWORD_TYPE.matcher(type).find()) {
            return Kind.SWORD;
        }
        if (BOW_TYPE.matcher(type).find()) {
            return Kind.BOW;
        }
        if (PICKAXE_TYPE.matcher(type).find()) {
            return Kind.PICKAXE;
        }
        if (!type.isEmpty()) {
            return Kind.OTHER;
        }
        if (stack.is(ItemTags.SWORDS)) {
            return Kind.SWORD;
        }
        if (stack.is(ItemTags.PICKAXES)) {
            return Kind.PICKAXE;
        }
        return stack.is(Items.BOW) ? Kind.BOW : Kind.OTHER;
    }

    private static String typeLine(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return "";
        }
        List<Component> lines = lore.lines();
        int checked = 0;
        for (int i = lines.size() - 1; i >= 0 && checked < TYPE_LINES; i--) {
            String line = IgnUtil.stripCodes(lines.get(i).getString()).trim().toUpperCase(Locale.ROOT);
            if (line.isEmpty()) {
                continue;
            }
            checked++;
            Matcher m = TYPE_LINE.matcher(line);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return "";
    }

    private static float time(LocalPlayer player) {
        long now = Util.getNanos();
        if (cancelled < 0L && stop(player)) {
            cancelled = now;
            frozen = elapsed(now);
        }
        if (cancelled >= 0L) {
            float since = (now - cancelled) / 1.0e9f;
            if (since >= CANCEL_SECONDS) {
                kind = null;
                return -1f;
            }
            weight = 1f - smooth(since / CANCEL_SECONDS);
            return frozen;
        }
        float t = elapsed(now);
        if (t >= anim.length()) {
            kind = null;
            return -1f;
        }
        weight = 1f;
        return t;
    }

    private static boolean stop(LocalPlayer player) {
        boolean allowed = equipping ? CustomItems.enabled() : ConfigManager.get().itemInspectEnabled;
        return !allowed || player.getInventory().getSelectedSlot() != slot
                || !player.getMainHandItem().is(item) || busy(player);
    }

    private static float elapsed(long now) {
        return (float) ((now - started) / 1.0e9 * speed());
    }

    private static double speed() {
        return Mth.clamp(ConfigManager.get().itemInspectSpeed, MIN_SPEED, MAX_SPEED);
    }

    private static void pose(float t, Frame f, boolean block) {
        if (restartAt >= 0L && (Util.getNanos() - restartAt) / 1.0e9f >= RESTART_SECONDS) {
            restartAt = -1L;
        }
        poseWith(anim, t, f, block, weight, restartFrom, restartAt);
    }

    private static void poseWith(Anim a, float t, Frame f, boolean block, float w, Frame from, long fromAt) {
        f.reset();
        a.motion().pose(t, f, block);
        f.arm = smooth(Math.min(1f, Math.min(t / ARM_IN, (a.length() - t) / ARM_OUT)));
        f.scale(w);
        if (from != null && fromAt >= 0L) {
            float since = (Util.getNanos() - fromAt) / 1.0e9f;
            if (since < RESTART_SECONDS) {
                f.blend(from, smooth(since / RESTART_SECONDS));
            }
        }
    }

    private static float inspectTime(ItemStackRenderState state, LocalPlayer player) {
        if (kind == null) {
            return -1f;
        }
        if (equipping && !equipUuid.equals(((CustomItemState) state).qzaUuid())) {
            if (pending && Util.getNanos() - pendingSince > EQUIP_WAIT) {
                kind = null;
            }
            return -1f;
        }
        if (pending) {
            pending = false;
            started = Util.getNanos();
        }
        return time(player);
    }

    public static boolean pushHand(ItemStackRenderState state, ItemDisplayContext context, PoseStack pose,
                                   SubmitNodeCollector collector, int light) {
        heldReady = false;
        if (!HandSpace.firstPerson(context)) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            kind = null;
            return false;
        }
        boolean right = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        if ((player.getMainArm() == HumanoidArm.RIGHT) != right) {
            return false;
        }
        String knife = CustomItems.knife(state);
        boolean hand = knife != null && CustomItems.handOn();
        float t = inspectTime(state, player);
        boolean active = t >= 0f;
        if (!active && !hand) {
            return false;
        }
        AABB box = state.getModelBoundingBox();
        boolean flat = box.getXsize() < FLAT;
        if (active) {
            pose(t, frame, !flat);
        } else {
            frame.reset();
        }
        if (hand) {
            frame.arm = 1f;
        }

        Vector3f centre = new Vector3f((float) (right ? box.getCenter().x : -box.getCenter().x),
                (float) box.getCenter().y, (float) box.getCenter().z);
        Vector3f hold;
        Vector3f anchor;
        float armTilt = 0f;
        Kind held = active ? kind : Kind.KNIFE;
        switch (held) {
            case SWORD, PICKAXE, KARAMBIT, BUTTERFLY, KNIFE, SABER -> {
                hold = GRIP;
                anchor = GRIP;
            }
            case BOW -> {
                hold = BOW_GRIP;
                anchor = BOW_GRIP;
                armTilt = LOW_ARM_TILT;
            }
            default -> {
                hold = centre;
                if (flat) {
                    anchor = new Vector3f(centre.x, centre.y - FLAT_HOLD, centre.z);
                } else {
                    anchor = new Vector3f(centre.x, (float) box.minY, (float) box.maxZ - BLOCK_EDGE);
                    armTilt = LOW_ARM_TILT;
                }
            }
        }
        boolean reverse = active ? anim.reverse() : CustomItems.KARAMBIT.equals(knife);
        if (reverse || (hand && CustomItems.KARAMBIT.equals(knife))) {
            hold = REVERSE_HOLD;
            anchor = REVERSE_HOLD;
            armTilt = REVERSE_ARM_TILT;
        }
        if (active && !Float.isNaN(anim.armTilt())) {
            armTilt = anim.armTilt();
        }
        float[] base = hand ? holdFor(knife) : NO_HOLD;
        if (!Float.isNaN(base[9])) {
            armTilt = base[9];
        }
        if (base[7] != 0f) {
            Vector3f along = new Vector3f(HANDLE).mul(base[7] * HandSpace.PIXEL);
            hold = new Vector3f(hold).add(along);
            anchor = new Vector3f(anchor).add(along);
        }
        Matrix4f view = CustomItems.viewmodel(state);
        if (view != null) {
            hold = view.transformPosition(hold, new Vector3f());
            anchor = view.transformPosition(anchor, new Vector3f());
        }

        Frame f = frame;
        Vector3f lifted = new Vector3f(hold).add(base[0], base[1], base[2]);
        Matrix4f handMatrix = new Matrix4f()
                .translate(lifted.x + f.move.x, lifted.y + f.move.y, lifted.z + f.move.z)
                .rotateY(rad(f.yaw))
                .rotateX(rad(f.pitch))
                .rotateZ(rad(f.roll))
                .translate(-lifted.x, -lifted.y, -lifted.z)
                .translate(lifted)
                .rotateY(rad(base[3]))
                .rotateX(rad(base[4]))
                .rotateZ(rad(base[5]))
                .translate(-hold.x, -hold.y, -hold.z);
        pose.pushPose();
        pose.mulPose(HandSpace.side(handMatrix, right));
        boolean showArm = hand || (ConfigManager.get().itemInspectShowHand && f.arm > 0.001f);
        if (showArm && !player.isInvisible()) {
            float away = 1f - f.arm;
            Matrix4f arm = new Matrix4f()
                    .translate(ARM_AWAY.x * away, ARM_AWAY.y * away, ARM_AWAY.z * away)
                    .translate(anchor)
                    .scale(base[6])
                    .rotateX(rad(armTilt))
                    .translate(-GRIP.x, -GRIP.y, -GRIP.z)
                    .mul(ARM);
            if (base[8] != 1f) {
                Vector3f fist = arm.transformPosition(FIST, new Vector3f());
                Vector3f length = arm.transformDirection(ARM_LENGTH, new Vector3f()).normalize();
                arm = HandSpace.stretch(fist, length, base[8]).mul(arm);
            }
            pose.pushPose();
            pose.mulPose(HandSpace.side(arm, right));
            AvatarRenderer<?> renderer = (AvatarRenderer<?>) client.getEntityRenderDispatcher().getRenderer(player);
            Identifier skin = player.getSkin().body().texturePath();
            boolean rightSleeve = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
            boolean leftSleeve = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
            if (right) {
                renderer.renderRightHand(pose, collector, light, skin, rightSleeve);
            } else {
                renderer.renderLeftHand(pose, collector, light, skin, leftSleeve);
            }
            if (hand && pairedDagger(state, knife, player)) {
                PoseStack mirror = mirrored(pose);
                if (right) {
                    renderer.renderLeftHand(mirror, collector, light, skin, leftSleeve);
                } else {
                    renderer.renderRightHand(mirror, collector, light, skin, rightSleeve);
                }
            }
            pose.popPose();
        }
        if (active) {
            held(anim, f, centre, heldMatrix);
            HandSpace.side(heldMatrix, right).get(heldMatrix);
            heldReady = true;
        }
        return true;
    }

    private static Matrix4f held(Anim a, Frame f, Vector3f centre, Matrix4f dest) {
        Vector3f turnAt = point(a.turnAt(), centre);
        Vector3f spinAt = point(a.spinAt(), centre);
        Vector3f twirlAt = point(a.twirlAt(), centre);
        return dest.identity()
                .translate(f.lift)
                .translate(turnAt)
                .rotateY(rad(f.turnY))
                .rotateX(rad(f.turnX))
                .rotateZ(rad(f.turnZ))
                .translate(-turnAt.x, -turnAt.y, -turnAt.z)
                .translate(spinAt)
                .rotate(rad(f.spin), a.spinAxis())
                .translate(-spinAt.x, -spinAt.y, -spinAt.z)
                .translate(twirlAt)
                .rotate(rad(f.twirl), a.twirlAxis())
                .translate(-twirlAt.x, -twirlAt.y, -twirlAt.z);
    }

    private static boolean thirdPerson() {
        return ConfigManager.get().itemInspectThirdPerson && kind != null && !equipping
                && !Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    private static boolean othersShown() {
        QZAConfig cfg = ConfigManager.get();
        return cfg.sharedItemsEnabled && cfg.sharedItemsShow;
    }

    public static void remote(UUID uuid, String name, double speed) {
        Minecraft client = Minecraft.getInstance();
        Anim next = NAMES.get(name);
        if (next == null || client.level == null || !othersShown()) {
            return;
        }
        Player player = client.level.getPlayerByUUID(uuid);
        if (player == null || player == client.player) {
            return;
        }
        long now = Util.getNanos();
        float rate = (float) Mth.clamp(speed, MIN_SPEED, MAX_SPEED);
        Remote old = REMOTES.get(player.getId());
        float from = 0f;
        Frame was = null;
        if (old != null) {
            float t = old.time(now, player);
            if (t >= 0f) {
                if (old.anim == next && old.cancelled < 0L) {
                    if (t <= next.again()) {
                        return;
                    }
                    from = next.again();
                }
                was = old.frame;
            }
        }
        REMOTES.put(player.getId(), new Remote(next, KINDS.get(name), now - (long) (from / rate * 1.0e9), rate,
                player.getMainHandItem().getItem(), was, now));
    }

    private static Remote remoteFor(int entity) {
        Remote remote = REMOTES.get(entity);
        if (remote == null) {
            return null;
        }
        Minecraft client = Minecraft.getInstance();
        if (!othersShown() || client.level == null || !(client.level.getEntity(entity) instanceof Player player)) {
            REMOTES.remove(entity);
            return null;
        }
        float t = remote.time(Util.getNanos(), player);
        if (t < 0f) {
            REMOTES.remove(entity);
            return null;
        }
        remote.now = t;
        return remote;
    }

    public static float thirdPersonArm(int entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.getId() == entity) {
            if (!thirdPerson()) {
                return 0f;
            }
            float t = time(player);
            if (t < 0f) {
                return 0f;
            }
            return smooth(Math.min(1f, Math.min(t / ARM_IN, (anim.length() - t) / ARM_OUT))) * weight;
        }
        Remote remote = remoteFor(entity);
        if (remote == null) {
            return 0f;
        }
        float t = remote.now;
        return smooth(Math.min(1f, Math.min(t / ARM_IN, (remote.anim.length() - t) / ARM_OUT))) * remote.weight;
    }

    public static void poseArm(ModelPart arm, boolean left, float raise) {
        float side = left ? -1f : 1f;
        arm.xRot = Mth.lerp(raise, arm.xRot, TP_ARM_PITCH);
        arm.yRot = Mth.lerp(raise, arm.yRot, TP_ARM_YAW * side);
        arm.zRot = Mth.lerp(raise, arm.zRot, 0f);
    }

    public static boolean pushThirdPerson(ItemStackRenderState state, ItemDisplayContext context, PoseStack pose) {
        if (!HandSpace.thirdPerson(context)) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer local = client.player;
        int owner = ((CustomItemState) state).qzaOwner();
        if (local == null || owner < 0) {
            return false;
        }
        boolean right = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        Anim a;
        Kind k;
        Frame f;
        if (owner == local.getId()) {
            if (!thirdPerson() || (local.getMainArm() == HumanoidArm.RIGHT) != right) {
                return false;
            }
            float t = time(local);
            if (t < 0f) {
                return false;
            }
            a = anim;
            k = kind;
            f = frame;
            pose(t, f, state.getModelBoundingBox().getXsize() >= FLAT);
        } else {
            Remote remote = remoteFor(owner);
            if (remote == null || !(client.level.getEntity(owner) instanceof Player player)
                    || (player.getMainArm() == HumanoidArm.RIGHT) != right) {
                return false;
            }
            a = remote.anim;
            k = remote.kind;
            f = remote.frame;
            poseWith(a, remote.now, f, state.getModelBoundingBox().getXsize() >= FLAT, remote.weight, remote.from,
                    remote.fromAt);
        }
        boolean karambit = CustomItems.KARAMBIT.equals(CustomItems.knife(state));
        Matrix4f firstDisplay = karambit ? new Matrix4f(HandSpace.KARAMBIT_REVERSE).mul(HandSpace.HELD)
                : new Matrix4f(HandSpace.HELD);
        Matrix4f thirdDisplay = karambit ? new Matrix4f(HandSpace.KARAMBIT_REVERSE_TP).mul(HandSpace.HELD_TP)
                : new Matrix4f(HandSpace.HELD_TP);
        Matrix4f toFirst = firstDisplay.mul(thirdDisplay.invert());
        AABB box = state.getModelBoundingBox();
        Vector3f centre = toFirst.transformPosition((float) (right ? box.getCenter().x : -box.getCenter().x),
                (float) box.getCenter().y, (float) box.getCenter().z, new Vector3f());
        Vector3f hold = a.reverse() ? REVERSE_HOLD : switch (k) {
            case BOW -> BOW_GRIP;
            case OTHER -> centre;
            default -> GRIP;
        };
        Matrix4f item = new Matrix4f(toFirst).invert()
                .translate(hold)
                .rotateY(rad(f.yaw))
                .rotateX(rad(f.pitch))
                .rotateZ(rad(f.roll))
                .translate(-hold.x, -hold.y, -hold.z)
                .mul(held(a, f, centre, new Matrix4f()))
                .mul(toFirst);
        pose.pushPose();
        pose.mulPose(HandSpace.side(item, right));
        return true;
    }

    public static Vector3f karambitPivot(ItemStackRenderState state) {
        Matrix4f view = CustomItems.viewmodel(state);
        Vector3f pivot = view == null ? new Vector3f(REVERSE_HOLD) : view.transformPosition(REVERSE_HOLD, new Vector3f());
        if (CustomItems.handOn()) {
            pivot.add(HOLD_KARAMBIT[0], HOLD_KARAMBIT[1], HOLD_KARAMBIT[2]);
        }
        return pivot;
    }

    private static float[] holdFor(String knife) {
        Knives.Knife type = Knives.get(knife);
        if (type == null) {
            return HOLD;
        }
        return switch (type.kind()) {
            case KARAMBIT -> HOLD_KARAMBIT;
            case DAGGERS -> HOLD_DAGGERS;
            default -> HOLD;
        };
    }

    private static boolean pairedDagger(ItemStackRenderState state, String knife, LocalPlayer player) {
        Knives.Knife type = Knives.get(knife);
        return type != null && type.kind() == Knives.Kind.DAGGERS && CustomItems.realistic(state)
                && player.getOffhandItem().isEmpty();
    }

    private static PoseStack mirrored(PoseStack pose) {
        Vector3f side = Mc.cameraRotation().transform(new Vector3f(1f, 0f, 0f));
        Matrix3f reflect = new Matrix3f(
                1f - 2f * side.x * side.x, -2f * side.x * side.y, -2f * side.x * side.z,
                -2f * side.y * side.x, 1f - 2f * side.y * side.y, -2f * side.y * side.z,
                -2f * side.z * side.x, -2f * side.z * side.y, 1f - 2f * side.z * side.z);
        PoseStack mirror = new PoseStack();
        PoseStack.Pose last = mirror.last();
        last.pose().set(reflect).mul(pose.last().pose()).scale(-1f, 1f, 1f);
        last.normal().set(reflect).mul(pose.last().normal()).scale(-1f, 1f, 1f);
        return mirror;
    }

    public static boolean pushHeld(PoseStack pose) {
        if (!heldReady) {
            return false;
        }
        heldReady = false;
        pose.pushPose();
        pose.mulPose(heldMatrix);
        return true;
    }

    private static Vector3f point(Pivot pivot, Vector3f centre) {
        return switch (pivot) {
            case GRIP -> GRIP;
            case BOW_GRIP -> BOW_GRIP;
            case RING -> RING;
            case PIVOT -> PIVOT;
            case REVERSE_RING -> REVERSE_RING;
            case REVERSE_HOLD -> REVERSE_HOLD;
            default -> centre;
        };
    }

    private static float air(float t, float from, float to) {
        return t > from && t < to ? (t - from) / (to - from) : -1f;
    }

    private static float window(float t, float from, float to) {
        return Math.min(smooth((t - from) / 0.15f), smooth((to - t) / 0.15f));
    }

    private static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    private static float rad(float degrees) {
        return degrees * Mth.DEG_TO_RAD;
    }
}
