package com.qza.tweaks;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.qza.chat.ChatKeybind;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.custom.CustomItemState;
import com.qza.custom.CustomItems;
import com.qza.gui.QZAScreen;
import com.qza.keybind.CommandKeybinds;
import com.qza.render.HandSpace;
import com.qza.util.IgnUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
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
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemInspect {
    public static final double MIN_SPEED = 0.5;
    public static final double MAX_SPEED = 2.0;
    public static final List<String> ANIMATIONS = List.of("1", "2", "3");

    private static final Pattern TYPE_LINE = Pattern.compile(
            "^(?:A )?(?:VERY SPECIAL|SPECIAL|ULTIMATE|ADMIN|DIVINE|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON)\\b(.*)");
    private static final Pattern SWORD_TYPE = Pattern.compile("\\bSWORD\\b");
    private static final Pattern BOW_TYPE = Pattern.compile("\\bBOW\\b");
    private static final Pattern PICKAXE_TYPE = Pattern.compile("\\b(?:PICKAXE|DRILL|GAUNTLET)\\b");
    private static final int TYPE_LINES = 3;
    private static final float CANCEL_SECONDS = 0.18f;
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
    private static final Vector3f BOW_HOLE = HandSpace.texel(5.8f, 7.8f);
    private static final Vector3f RING = HandSpace.texel(2.5f, 13.5f);
    private static final Vector3f PIVOT = CustomItems.BUTTERFLY_PIVOT;
    private static final Vector3f LIMBS = HandSpace.direction(12.5f, 13f);
    private static final Vector3f ARM_AWAY = new Vector3f(0.05f, -0.6f, 0.15f);
    private static final float WRIST_ARM_TILT = -10f;
    private static final Vector3f FOREARM = new Vector3f(0f, -0.5f, 0.866f).rotateX(rad(WRIST_ARM_TILT));
    private static final Vector3f WRIST = new Vector3f(FOREARM).mul(0.17f).add(BOW_GRIP).sub(BOW_HOLE);
    private static final Vector3f REVERSE_RING = HandSpace.KARAMBIT_REVERSE.transformPosition(RING, new Vector3f());
    private static final Vector3f REVERSE_HOLD = HandSpace.KARAMBIT_REVERSE.transformPosition(HandSpace.KARAMBIT_HOLD,
            new Vector3f());
    private static final long EQUIP_WAIT = 800_000_000L;
    private static final float REVERSE_ARM_TILT = 50f;

    private static final Anim[][] ANIMS = {
            {Twirl.ANIM, Salute.ANIM, FlipCatch.ANIM},
            {Toss.ANIM, Balance.ANIM, DrillRev.ANIM},
            {Spin.ANIM, Boomerang.ANIM, WristSpin.ANIM},
            {LookOver.ANIM, Juggle.ANIM, Levitate.ANIM},
            {FingerSpin.ANIM, IonTwirl.ANIM, RingCombo.ANIM},
            {BalisongFlips.ANIM, StarFlips.ANIM, Aerial.ANIM}
    };

    private static final Frame frame = new Frame();
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
        BUTTERFLY
    }

    private enum Pivot {
        GRIP,
        BOW_GRIP,
        BOW_HOLE,
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
                        Pivot twirlAt, Vector3f twirlAxis, float armTilt, boolean reverse) {
        private Anim(float length, Motion motion, Pivot turnAt, Pivot spinAt, Vector3f spinAxis,
                     Pivot twirlAt, Vector3f twirlAxis) {
            this(length, motion, turnAt, spinAt, spinAxis, twirlAt, twirlAxis, Float.NaN, false);
        }

        private Anim(float length, Motion motion, Pivot turnAt, Pivot spinAt, Vector3f spinAxis,
                     Pivot twirlAt, Vector3f twirlAxis, float armTilt) {
            this(length, motion, turnAt, spinAt, spinAxis, twirlAt, twirlAxis, armTilt, false);
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
        private static final Anim ANIM = new Anim(2.0f, Twirl::pose, Pivot.CENTRE, Pivot.GRIP, FACE, Pivot.GRIP, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.pitch = PITCH.at(t);
            f.roll = ROLL.at(t);
            f.spin = SPIN.at(t);
            f.lift.set(FACE).mul(FRONT.at(t));
        }
    }

    private static final class Salute {
        private static final Track X = Track.of(0f, 0f, 0.45f, -0.48f, 1.1f, -0.46f, 1.55f, -0.16f, 1.95f, -0.14f,
                2.4f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.45f, 0.26f, 1.1f, 0.29f, 1.55f, 0.12f, 1.95f, 0.12f,
                2.4f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.45f, 0.02f, 1.1f, 0.02f, 1.55f, -0.06f, 1.95f, -0.06f,
                2.4f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.45f, 60f, 1.1f, 60f, 1.55f, 20f, 1.95f, 20f, 2.4f, 0f);
        private static final Track TILT = Track.of(0f, 0f, 0.45f, -20f, 1.1f, -22f, 1.55f, -120f, 1.95f, -120f,
                2.4f, 0f);
        private static final Track LEAN = Track.of(0f, 0f, 1.1f, 0f, 1.55f, -20f, 1.95f, -20f, 2.4f, 0f);
        private static final Anim ANIM = new Anim(2.4f, Salute::pose, Pivot.GRIP, Pivot.GRIP, FACE, Pivot.GRIP, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t) + 0.006f * Mth.sin(t * 1.2f * Mth.TWO_PI) * window(t, 0.45f, 1.1f), Z.at(t));
            f.yaw = YAW.at(t);
            f.turnX = TILT.at(t);
            f.turnZ = LEAN.at(t);
        }
    }

    private static final class FlipCatch {
        private static final float THROWN = 0.45f;
        private static final float CAUGHT = 1.35f;
        private static final float HEIGHT = 0.32f;
        private static final float DRIFT = -0.14f;
        private static final Track X = Track.of(0f, 0f, 0.3f, -0.22f, 0.42f, -0.2f, 0.56f, -0.3f, 1.3f, -0.3f,
                1.42f, -0.24f, 1.8f, -0.18f, 2.3f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.14f, 0.42f, 0.04f, 0.56f, 0.2f, 1.3f, 0.14f,
                1.42f, 0.05f, 1.8f, 0.1f, 2.3f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.3f, -0.06f, 0.56f, -0.14f, 1.3f, -0.14f, 1.8f, -0.06f,
                2.3f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.3f, 40f, 1.8f, 40f, 2.3f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.3f, 15f, 1.8f, 15f, 2.3f, 0f);
        private static final Anim ANIM = new Anim(2.3f, FlipCatch::pose, Pivot.CENTRE, Pivot.CENTRE, FACE,
                Pivot.GRIP, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.roll = ROLL.at(t);
            float air = air(t, THROWN, CAUGHT);
            if (air >= 0f) {
                float arc = 4f * air * (1f - air);
                f.lift.set(DRIFT * arc, HEIGHT * arc, 0f);
                f.spin = 720f * air;
            }
        }
    }

    private static final class Toss {
        private static final float THROWN = 1.35f;
        private static final float CAUGHT = 1.9f;
        private static final float HEIGHT = 0.35f;
        private static final float DRIFT = -0.1f;
        private static final Track X = Track.of(0f, 0f, 0.3f, -0.1f, 1.0f, -0.1f, 1.2f, -0.05f, 1.35f, -0.08f,
                1.9f, -0.06f, 2.0f, -0.05f, 2.3f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.14f, 1.0f, 0.14f, 1.2f, 0.03f, 1.35f, 0.17f,
                1.6f, 0.1f, 1.9f, 0.12f, 2.0f, 0.06f, 2.3f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.3f, 0.05f, 1.9f, 0.05f, 2.3f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.3f, 50f, 1.0f, 50f, 1.25f, 20f, 1.9f, 20f, 2.3f, 0f);
        private static final Track PITCH = Track.of(0f, 0f, 0.3f, -10f, 1.0f, -10f, 1.25f, 0f, 1.9f, 0f,
                2.0f, 6f, 2.3f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.3f, 30f, 1.0f, 30f, 1.25f, 10f, 1.9f, 10f, 2.3f, 0f);
        private static final Track TURN = Track.of(0.3f, 0f, 0.95f, 360f);
        private static final Anim ANIM = new Anim(2.3f, Toss::pose, Pivot.CENTRE, Pivot.GRIP, HANDLE,
                Pivot.GRIP, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.pitch = PITCH.at(t);
            f.roll = ROLL.at(t);
            f.spin = TURN.at(t);
            float air = air(t, THROWN, CAUGHT);
            if (air >= 0f) {
                float arc = 4f * air * (1f - air);
                f.lift.set(DRIFT * arc, HEIGHT * arc, 0f);
                f.turnX = 360f * air;
            }
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
                Pivot.GRIP, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), 0f);
            f.yaw = YAW.at(t);
            f.lift.set(0f, RISE.at(t), BACK.at(t));
            f.turnX = UPRIGHT.at(t);
            f.turnY = TOP.at(t);
            f.turnZ = 6f * Mth.sin(t * 1.3f * Mth.TWO_PI) * window(t, 0.55f, 1.75f);
        }
    }

    private static final class DrillRev {
        private static final Track X = Track.of(0f, 0f, 0.35f, -0.26f, 2.15f, -0.26f, 2.6f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.35f, 0.14f, 2.15f, 0.14f, 2.6f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.35f, -0.04f, 1.0f, -0.04f, 1.45f, -0.12f, 1.8f, -0.06f,
                2.15f, -0.04f, 2.6f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.35f, -6f, 2.15f, -6f, 2.6f, 0f);
        private static final Track AIM = Track.of(0f, 0f, 0.35f, -100f, 2.15f, -100f, 2.6f, 0f);
        private static final Track REV = Track.of(0.4f, 0f, 0.75f, 300f, 1.15f, 1500f, 1.6f, 3300f, 2.0f, 4140f,
                2.15f, 4320f);
        private static final Anim ANIM = new Anim(2.6f, DrillRev::pose, Pivot.GRIP, Pivot.GRIP, HANDLE,
                Pivot.GRIP, FACE);

        private static void pose(float t, Frame f, boolean block) {
            float buzz = Mth.sin(Mth.clamp((t - 0.6f) / 1.5f, 0f, 1f) * Mth.PI);
            f.move.set(X.at(t) + 0.007f * buzz * Mth.sin(t * 27f * Mth.TWO_PI),
                    Y.at(t) + 0.006f * buzz * Mth.sin(t * 33f * Mth.TWO_PI + 1.3f), Z.at(t));
            f.yaw = YAW.at(t);
            f.turnX = AIM.at(t) + 1.5f * buzz * Mth.sin(t * 21f * Mth.TWO_PI);
            f.spin = REV.at(t);
        }
    }

    private static final class Spin {
        private static final float[] PLUCKS = {0.35f, 0.62f};
        private static final Track X = Track.of(0f, 0f, 0.3f, -0.2f, 1.6f, -0.2f, 2.0f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.12f, 1.6f, 0.12f, 2.0f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.3f, -0.08f, 1.6f, -0.08f, 2.0f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.3f, 10f, 1.6f, 10f, 2.0f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.3f, -10f, 1.6f, -10f, 2.0f, 0f);
        private static final Track SPIN = Track.of(0f, 0f, 0.3f, 240f, 0.95f, 240f, 1.6f, 960f, 2.0f, 1080f);
        private static final Anim ANIM = new Anim(2.0f, Spin::pose, Pivot.CENTRE, Pivot.BOW_GRIP, LIMBS,
                Pivot.BOW_GRIP, FACE);

        private static void pose(float t, Frame f, boolean block) {
            float shake = 0f;
            for (float pluck : PLUCKS) {
                shake += ring(t - pluck, 12f, 9f);
            }
            f.move.set(X.at(t) + 0.012f * shake, Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.roll = ROLL.at(t) + 4f * shake;
            f.spin = SPIN.at(t);
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
                Pivot.CENTRE, FACE);

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

    private static final class WristSpin {
        private static final Track X = Track.of(0f, 0f, 0.3f, -0.3f, 2.2f, -0.3f, 2.6f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.08f, 2.2f, 0.08f, 2.6f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.3f, -0.08f, 2.2f, -0.08f, 2.6f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.3f, 10f, 2.2f, 10f, 2.6f, 0f);
        private static final Track ON = Track.of(0.3f, 0f, 0.62f, 1f, 1.95f, 1f, 2.25f, 0f);
        private static final Track SPIN = Track.of(0.62f, 0f, 1.95f, 1440f);
        private static final Anim ANIM = new Anim(2.6f, WristSpin::pose, Pivot.BOW_HOLE, Pivot.BOW_HOLE, FACE,
                Pivot.BOW_HOLE, FACE, WRIST_ARM_TILT);

        private static void pose(float t, Frame f, boolean block) {
            float on = ON.at(t);
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.turnZ = 90f * on;
            f.turnX = -70f * on;
            f.lift.set(WRIST).mul(on).add(0f, 0.08f * Mth.sin(on * Mth.PI), 0f);
            f.spin = SPIN.at(t);
        }
    }

    private static final class LookOver {
        private static final Track X = Track.of(0f, 0f, 0.35f, -0.18f, 1.75f, -0.18f, 2.2f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.35f, 0.16f, 1.75f, 0.16f, 2.2f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.35f, -0.02f, 1.75f, -0.02f, 2.2f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.35f, 35f, 0.8f, 70f, 1.2f, 10f, 1.75f, 10f, 2.2f, 0f);
        private static final Track PITCH = Track.of(0f, 0f, 0.35f, -10f, 0.8f, -18f, 1.2f, 8f, 1.75f, 0f);
        private static final Track SPIN = Track.of(1.25f, 0f, 1.75f, 360f);
        private static final Anim ANIM = new Anim(2.2f, LookOver::pose, Pivot.CENTRE, Pivot.CENTRE, UP,
                Pivot.CENTRE, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t) * (block ? 2f : 1f), Z.at(t));
            f.yaw = YAW.at(t);
            f.pitch = PITCH.at(t);
            f.spin = SPIN.at(t);
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
                Pivot.CENTRE, FACE);

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

    private static final class Levitate {
        private static final Track X = Track.of(0f, 0f, 0.3f, -0.24f, 2.2f, -0.24f, 2.6f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.1f, 2.2f, 0.1f, 2.6f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.3f, -0.06f, 2.2f, -0.06f, 2.6f, 0f);
        private static final Track RISE = Track.of(0.3f, 0f, 0.65f, 0.2f, 1.95f, 0.2f, 2.15f, 0f, 2.25f, 0.03f,
                2.35f, 0f);
        private static final Track TURN = Track.of(0.35f, 0f, 1.95f, 540f, 2.25f, 720f);
        private static final Anim ANIM = new Anim(2.6f, Levitate::pose, Pivot.CENTRE, Pivot.CENTRE, UP,
                Pivot.CENTRE, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t) * (block ? 2f : 1f), Z.at(t));
            float bob = 0.025f * Mth.sin((t - 0.65f) * 0.9f * Mth.TWO_PI) * window(t, 0.65f, 1.95f);
            f.lift.y = RISE.at(t) + bob;
            f.turnY = TURN.at(t);
        }
    }

    private static final class FingerSpin {
        private static final Track X = Track.of(0f, 0f, 0.35f, -0.22f, 1.9f, -0.2f, 2.4f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.35f, 0.22f, 0.8f, 0.24f, 1.55f, 0.26f, 1.7f, 0.18f,
                1.9f, 0.22f, 2.4f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.35f, -0.04f, 1.9f, -0.04f, 2.4f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.35f, 40f, 0.55f, 75f, 0.75f, 15f, 0.9f, 40f, 1.9f, 40f,
                2.4f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.35f, 10f, 0.55f, -15f, 0.75f, 25f, 0.9f, 10f, 1.9f, 10f,
                2.4f, 0f);
        private static final Track SPIN = Track.of(0.9f, 0f, 1.55f, 720f);
        private static final Track FRONT = Track.of(0.82f, 0f, 0.92f, 0.12f, 1.55f, 0.12f, 1.65f, 0f);
        private static final Anim ANIM = new Anim(2.4f, FingerSpin::pose, Pivot.GRIP, Pivot.RING, FACE,
                Pivot.RING, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.roll = ROLL.at(t);
            f.spin = SPIN.at(t);
            f.lift.set(FACE).mul(FRONT.at(t));
        }
    }

    private static final class RingCombo {
        private static final float THROWN = 1.35f;
        private static final float CAUGHT = 2.05f;
        private static final float HEIGHT = 0.34f;
        private static final float DRIFT = -0.1f;
        private static final Track X = Track.of(0f, 0f, 0.3f, -0.22f, 2.2f, -0.22f, 2.6f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.2f, 1.2f, 0.2f, 1.3f, 0.12f, 1.4f, 0.26f, 2.0f, 0.18f,
                2.1f, 0.12f, 2.2f, 0.2f, 2.6f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.3f, -0.04f, 2.2f, -0.04f, 2.6f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.3f, 40f, 2.2f, 40f, 2.6f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.3f, 10f, 2.2f, 10f, 2.6f, 0f);
        private static final Track SPIN = Track.of(0.35f, 0f, 0.7f, 360f, 0.75f, 360f, 1.1f, 0f);
        private static final Track FRONT = Track.of(0.3f, 0f, 0.38f, 0.12f, 1.1f, 0.12f, 1.2f, 0f);
        private static final Anim ANIM = new Anim(2.6f, RingCombo::pose, Pivot.CENTRE, Pivot.RING, FACE,
                Pivot.CENTRE, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.roll = ROLL.at(t);
            f.spin = SPIN.at(t);
            f.lift.set(FACE).mul(FRONT.at(t));
            float air = air(t, THROWN, CAUGHT);
            if (air >= 0f) {
                float arc = 4f * air * (1f - air);
                f.lift.set(FACE).mul(0.12f).add(DRIFT * arc, HEIGHT * arc, 0f);
                f.twirl = 720f * air;
            }
        }
    }

    private static final class BalisongFlips {
        private static final Track X = Track.of(0f, 0f, 0.3f, -0.22f, 2.1f, -0.22f, 2.5f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.22f, 2.1f, 0.22f, 2.5f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.3f, -0.05f, 2.1f, -0.05f, 2.5f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.3f, 40f, 2.1f, 40f, 2.5f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.3f, 10f, 2.1f, 10f, 2.5f, 0f);
        private static final Track BACK = Track.of(0.3f, 0f, 0.5f, 180f, 0.75f, 360f, 0.95f, 540f, 1.15f, 720f,
                1.75f, 720f, 1.9f, 900f, 2.05f, 1080f);
        private static final Track BLADE = Track.of(0.5f, 0f, 0.75f, 180f, 0.95f, 180f, 1.15f, 360f);
        private static final Track SPIN = Track.of(1.2f, 0f, 1.7f, 360f);
        private static final Track FRONT = Track.of(0.25f, 0f, 0.32f, 0.1f, 2.05f, 0.1f, 2.15f, 0f);
        private static final Anim ANIM = new Anim(2.5f, BalisongFlips::pose, Pivot.GRIP, Pivot.PIVOT, FACE,
                Pivot.PIVOT, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.roll = ROLL.at(t);
            f.spin = SPIN.at(t);
            f.back = BACK.at(t);
            f.blade = BLADE.at(t);
            f.lift.set(FACE).mul(FRONT.at(t));
        }
    }

    private static final class Aerial {
        private static final float THROWN = 0.65f;
        private static final float CAUGHT = 1.5f;
        private static final float HEIGHT = 0.36f;
        private static final float DRIFT = -0.1f;
        private static final Track X = Track.of(0f, 0f, 0.3f, -0.22f, 2.0f, -0.22f, 2.4f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.2f, 0.55f, 0.1f, 0.68f, 0.24f, 1.45f, 0.18f,
                1.55f, 0.1f, 1.7f, 0.2f, 2.0f, 0.2f, 2.4f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.3f, -0.05f, 2.0f, -0.05f, 2.4f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.3f, 40f, 2.0f, 40f, 2.4f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.3f, 10f, 2.0f, 10f, 2.4f, 0f);
        private static final Track BACK = Track.of(0.3f, 0f, 0.55f, 360f, 1.6f, 360f, 1.9f, 720f);
        private static final Track FRONT = Track.of(0.25f, 0f, 0.32f, 0.1f, 1.95f, 0.1f, 2.05f, 0f);
        private static final Anim ANIM = new Anim(2.4f, Aerial::pose, Pivot.GRIP, Pivot.PIVOT, FACE,
                Pivot.PIVOT, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.roll = ROLL.at(t);
            f.back = BACK.at(t);
            f.lift.set(FACE).mul(FRONT.at(t));
            float air = air(t, THROWN, CAUGHT);
            if (air >= 0f) {
                float arc = 4f * air * (1f - air);
                f.lift.add(DRIFT * arc, HEIGHT * arc, 0f);
                f.spin = 720f * air;
                f.back += 150f * Math.abs(Mth.sin(air * Mth.TWO_PI));
            }
        }
    }

    private static final class IonTwirl {
        private static final Track X = Track.of(0f, 0f, 0.3f, -0.06f, 1.0f, -0.1f, 2.0f, -0.06f, 2.6f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.2f, 0.06f, 0.7f, 0.1f, 1.0f, 0.02f, 1.3f, 0.08f, 2.1f, 0.06f,
                2.6f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.3f, -0.04f, 2.2f, -0.04f, 2.6f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.5f, 0f, 0.7f, 35f, 1.0f, 55f, 1.2f, 10f, 2.0f, 0f);
        private static final Track ROLL = Track.of(0.75f, 0f, 1.0f, 25f, 1.2f, 0f);
        private static final Track SPIN = Track.of(0.1f, 0f, 0.45f, -360f, 0.7f, -540f, 0.95f, -360f, 1.05f, -360f,
                2.0f, -1440f);
        private static final Anim ANIM = new Anim(2.6f, IonTwirl::pose, Pivot.REVERSE_HOLD, Pivot.REVERSE_RING, FACE,
                Pivot.REVERSE_RING, FACE, REVERSE_ARM_TILT, true);

        private static void pose(float t, Frame f, boolean block) {
            float bob = 0.012f * Mth.sin((t - 1.05f) * 3f * Mth.TWO_PI) * window(t, 1.05f, 2.0f);
            f.move.set(X.at(t), Y.at(t) + bob, Z.at(t));
            f.yaw = YAW.at(t);
            f.roll = ROLL.at(t);
            f.spin = SPIN.at(t);
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

    private static final class StarFlips {
        private static final Track X = Track.of(0f, 0f, 0.3f, -0.22f, 2.85f, -0.22f, 3.2f, 0f);
        private static final Track Y = Track.of(0f, 0f, 0.3f, 0.22f, 2.85f, 0.22f, 3.2f, 0f);
        private static final Track Z = Track.of(0f, 0f, 0.3f, -0.05f, 2.85f, -0.05f, 3.2f, 0f);
        private static final Track YAW = Track.of(0f, 0f, 0.3f, 40f, 1.15f, 40f, 1.4f, 85f, 1.6f, 40f, 2.85f, 40f,
                3.2f, 0f);
        private static final Track ROLL = Track.of(0f, 0f, 0.3f, 10f, 2.85f, 10f, 3.2f, 0f);
        private static final Track BLADE = Track.of(0.3f, 0f, 0.45f, -60f, 1.0f, -60f, 1.15f, 0f, 1.6f, 0f,
                1.75f, 180f, 1.85f, 180f, 2.0f, 360f, 2.15f, 300f, 2.6f, 300f, 2.75f, 360f);
        private static final Track BACK = Track.of(0.3f, 0f, 0.45f, 120f, 1.0f, 120f, 1.15f, 0f, 2.0f, 0f, 2.15f, 120f,
                2.6f, 120f, 2.75f, 0f);
        private static final Track SPIN = Track.of(0.4f, 0f, 1.0f, 720f, 2.05f, 720f, 2.6f, 1800f);
        private static final Track FRONT = Track.of(0.25f, 0f, 0.32f, 0.1f, 2.85f, 0.1f, 2.95f, 0f);
        private static final Anim ANIM = new Anim(3.2f, StarFlips::pose, Pivot.GRIP, Pivot.PIVOT, FACE,
                Pivot.PIVOT, FACE);

        private static void pose(float t, Frame f, boolean block) {
            f.move.set(X.at(t), Y.at(t), Z.at(t));
            f.yaw = YAW.at(t);
            f.roll = ROLL.at(t);
            f.spin = SPIN.at(t);
            f.blade = BLADE.at(t);
            f.back = BACK.at(t);
            f.lift.set(FACE).mul(FRONT.at(t));
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

    public static boolean capturing() {
        return capturing;
    }

    public static void startCapture() {
        capturing = true;
    }

    public static String keyLabel() {
        return CommandKeybinds.keyLabel(ConfigManager.get().itemInspectKey);
    }

    public static String animationLabel(String value) {
        return "Animation " + value;
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
        Kind wanted = CustomItems.KARAMBIT.equals(type) ? Kind.KARAMBIT : Kind.BUTTERFLY;
        kind = wanted;
        anim = wanted == Kind.KARAMBIT ? KarambitEquip.ANIM : ButterflyEquip.ANIM;
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
        if (player == null || !ConfigManager.get().itemInspectEnabled
                || (kind != null && cancelled < 0L && elapsed(now) < anim.length())) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || busy(player)) {
            return;
        }
        kind = kindOf(stack);
        anim = ANIMS[kind.ordinal()][choice(kind)];
        equipping = false;
        pending = false;
        started = now;
        cancelled = -1L;
        slot = player.getInventory().getSelectedSlot();
        item = stack.getItem();
    }

    private static int choice(Kind kind) {
        QZAConfig cfg = ConfigManager.get();
        String value = switch (kind) {
            case SWORD -> cfg.itemInspectSword;
            case PICKAXE -> cfg.itemInspectPickaxe;
            case BOW -> cfg.itemInspectBow;
            case KARAMBIT -> cfg.itemInspectKarambit;
            case BUTTERFLY -> cfg.itemInspectButterfly;
            default -> cfg.itemInspectOther;
        };
        int index = ANIMATIONS.indexOf(value);
        return Math.max(index, 0);
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
            return Kind.SWORD;
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
        double speed = Mth.clamp(ConfigManager.get().itemInspectSpeed, MIN_SPEED, MAX_SPEED);
        return (float) ((now - started) / 1.0e9 * speed);
    }

    private static void pose(float t, Frame f, boolean block) {
        f.reset();
        anim.motion().pose(t, f, block);
        f.arm = smooth(Math.min(1f, Math.min(t / ARM_IN, (anim.length() - t) / ARM_OUT)));
        f.scale(weight);
    }

    public static boolean push(ItemStackRenderState state, ItemDisplayContext context, PoseStack pose,
                               SubmitNodeCollector collector, int light) {
        if (kind == null || (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND)) {
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
        if (equipping && !equipUuid.equals(((CustomItemState) state).qzaUuid())) {
            if (pending && Util.getNanos() - pendingSince > EQUIP_WAIT) {
                kind = null;
            }
            return false;
        }
        if (pending) {
            pending = false;
            started = Util.getNanos();
        }
        float t = time(player);
        if (t < 0f) {
            return false;
        }
        AABB box = state.getModelBoundingBox();
        boolean flat = box.getXsize() < FLAT;
        pose(t, frame, !flat);

        Vector3f centre = new Vector3f((float) (right ? box.getCenter().x : -box.getCenter().x),
                (float) box.getCenter().y, (float) box.getCenter().z);
        Vector3f hold;
        Vector3f anchor;
        float armTilt = 0f;
        switch (kind) {
            case SWORD, PICKAXE, KARAMBIT, BUTTERFLY -> {
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
        if (anim.reverse()) {
            hold = REVERSE_HOLD;
            anchor = REVERSE_HOLD;
        }
        if (!Float.isNaN(anim.armTilt())) {
            armTilt = anim.armTilt();
        }
        Vector3f turnAt = point(anim.turnAt(), centre);
        Vector3f spinAt = point(anim.spinAt(), centre);
        Vector3f twirlAt = point(anim.twirlAt(), centre);

        Frame f = frame;
        Matrix4f hand = new Matrix4f()
                .translate(hold.x + f.move.x, hold.y + f.move.y, hold.z + f.move.z)
                .rotateY(rad(f.yaw))
                .rotateX(rad(f.pitch))
                .rotateZ(rad(f.roll))
                .translate(-hold.x, -hold.y, -hold.z);
        Matrix4f held = new Matrix4f()
                .translate(f.lift)
                .translate(turnAt)
                .rotateY(rad(f.turnY))
                .rotateX(rad(f.turnX))
                .rotateZ(rad(f.turnZ))
                .translate(-turnAt.x, -turnAt.y, -turnAt.z)
                .translate(spinAt)
                .rotate(rad(f.spin), anim.spinAxis())
                .translate(-spinAt.x, -spinAt.y, -spinAt.z)
                .translate(twirlAt)
                .rotate(rad(f.twirl), anim.twirlAxis())
                .translate(-twirlAt.x, -twirlAt.y, -twirlAt.z);

        pose.pushPose();
        pose.mulPose(HandSpace.side(hand, right));
        if (ConfigManager.get().itemInspectShowHand && f.arm > 0.001f && !player.isInvisible()) {
            float away = 1f - f.arm;
            Matrix4f arm = new Matrix4f()
                    .translate(ARM_AWAY.x * away, ARM_AWAY.y * away, ARM_AWAY.z * away)
                    .translate(anchor)
                    .rotateX(rad(armTilt))
                    .translate(-GRIP.x, -GRIP.y, -GRIP.z)
                    .mul(ARM);
            pose.pushPose();
            pose.mulPose(HandSpace.side(arm, right));
            AvatarRenderer<?> renderer = (AvatarRenderer<?>) client.getEntityRenderDispatcher().getRenderer(player);
            Identifier skin = player.getSkin().body().texturePath();
            if (right) {
                renderer.renderRightHand(pose, collector, light, skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
            } else {
                renderer.renderLeftHand(pose, collector, light, skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
            }
            pose.popPose();
        }
        pose.mulPose(HandSpace.side(held, right));
        return true;
    }

    private static Vector3f point(Pivot pivot, Vector3f centre) {
        return switch (pivot) {
            case GRIP -> GRIP;
            case BOW_GRIP -> BOW_GRIP;
            case BOW_HOLE -> BOW_HOLE;
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

    private static float ring(float since, float hz, float decay) {
        if (since <= 0f || since >= 0.35f) {
            return 0f;
        }
        return (float) Math.exp(-decay * since) * Mth.sin(since * hz * Mth.TWO_PI);
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
