package com.qza.cheat.movement;

import com.qza.cheat.CheatConfigManager;
import com.qza.cheat.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class RingCenter {
    private static final long LIMIT_MS = 6000L;
    private static final double DONE = 4.0E-5;
    private static final double STOP_SQ = 9.0E-6;
    private static final double MAX_TURN = 30.0;
    private static final double BETTER = 0.95;
    private static final int TRIPLE_STEPS = 60;
    private static final int[][] KEYS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    private record Tap(int key, double yaw) {
    }

    private final double cx;
    private final double cz;
    private final long started;
    private final ArrayDeque<Tap> taps = new ArrayDeque<>();
    private Tap next;
    private HumanTurn turn;
    private boolean coasting;
    private int tapped;

    private final double[][] input = new double[KEYS.length][];
    private double speed;
    private double drag;
    private double pixel;
    private double straight;
    private double diagonal;
    private double bestError;
    private List<Tap> bestPlan;

    RingCenter(double cx, double cz, long now) {
        this.cx = cx;
        this.cz = cz;
        this.started = now;
    }

    HumanTurn turn() {
        return turn;
    }

    boolean tick(Minecraft client, LocalPlayer player, long now) {
        Options options = client.options;
        press(options.keyShift, true);
        if (now - started > LIMIT_MS) {
            MovementLog.line(String.format(Locale.ROOT, "couldn't reach the centre %.4f, %.4f in time, stopped at "
                    + "%.5f, %.5f after %d taps", cx, cz, player.getX(), player.getZ(), tapped));
            return true;
        }
        boolean still = still(player);
        if (coasting) {
            if (!still) {
                return false;
            }
            coasting = false;
        }
        if (next == null) {
            if (taps.isEmpty()) {
                double[] at = restOf(player);
                double miss = Math.max(Math.abs(cx - at[0]), Math.abs(cz - at[1]));
                if (miss < DONE) {
                    if (still) {
                        MovementLog.line(String.format(Locale.ROOT, "walked to the centre %.5f, %.5f with %d taps "
                                + "in %dms", player.getX(), player.getZ(), tapped, now - started));
                    }
                    return still;
                }
                if (!player.onGround()) {
                    return false;
                }
                List<Tap> plan = plan(client, player, at[0], at[1]);
                if (plan.isEmpty()) {
                    if (still) {
                        MovementLog.line(String.format(Locale.ROOT, "closest to the centre with keys: %.5f, %.5f "
                                + "after %d taps", player.getX(), player.getZ(), tapped));
                    }
                    return still;
                }
                taps.addAll(plan);
            }
            begin(player, taps.poll());
        }
        if (!still || !player.onGround() || !player.isShiftKeyDown() || player.isSprinting() || player.isUsingItem()
                || (turn != null && !turn.done(System.nanoTime()))) {
            return false;
        }
        MovementCopier.mouseTowards(client, player, new double[]{next.yaw, player.getXRot()});
        int[] key = KEYS[next.key];
        press(options.keyLeft, key[0] > 0);
        press(options.keyRight, key[0] < 0);
        press(options.keyUp, key[1] > 0);
        press(options.keyDown, key[1] < 0);
        tapped++;
        coasting = true;
        next = null;
        turn = null;
        if (!taps.isEmpty()) {
            begin(player, taps.poll());
        }
        return false;
    }

    private void begin(LocalPlayer player, Tap tap) {
        next = tap;
        turn = HumanTurn.plan(player.getYRot(), player.getXRot(), (float) tap.yaw, player.getXRot(),
                CheatConfigManager.get().movementTurnSpeed, false, 0.0);
    }

    private static void press(KeyMapping key, boolean down) {
        ((KeyMappingAccessor) key).qzaSetDown(down);
    }

    private static boolean still(LocalPlayer player) {
        return player.getDeltaMovement().horizontalDistanceSqr() < STOP_SQ;
    }

    private double[] restOf(LocalPlayer player) {
        drag = friction(player) * 0.91f;
        return rest(player.getX(), player.getZ(), player.getDeltaMovement().x, player.getDeltaMovement().z);
    }

    private static float friction(LocalPlayer player) {
        return player.level().getBlockState(player.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction();
    }

    private List<Tap> plan(Minecraft client, LocalPlayer player, double x, double z) {
        float friction = friction(player);
        drag = friction * 0.91f;
        speed = player.getSpeed() * (0.21600002f / (friction * friction * friction));
        pixel = MovementCopier.mouseSens(client) * 0.15;
        setUp((float) player.getAttributeValue(Attributes.SNEAKING_SPEED));
        return search(x, z, player.getYRot());
    }

    private void setUp(float sneak) {
        for (int i = 0; i < KEYS.length; i++) {
            Vec2 move = squared(new Vec2(KEYS[i][0], KEYS[i][1]).normalized().scale(0.98f).scale(sneak));
            input[i] = new double[]{move.x, move.y};
        }
        straight = length(tap(0.0, 0.0, 2, 0.0));
        diagonal = length(tap(0.0, 0.0, 1, 0.0));
    }

    private List<Tap> search(double x, double z, double yaw) {
        double d = Math.hypot(cx - x, cz - z);
        bestError = d;
        bestPlan = List.of();
        int span = (int) (MAX_TURN / pixel);
        for (int key = 0; key < KEYS.length; key++) {
            for (int n = -span; n <= span; n++) {
                double turned = yaw + (n * pixel);
                consider(tap(x, z, key, turned), List.of(new Tap(key, turned)));
            }
        }
        pair(x, z, yaw, 3, 2, List.of());
        int stride = Math.max(1, span / TRIPLE_STEPS);
        for (int key = 0; key < KEYS.length; key++) {
            for (int n = -span; n <= span; n += stride) {
                double turned = yaw + (n * pixel);
                double[] end = tap(x, z, key, turned);
                if (Math.hypot(cx - end[0], cz - end[1]) <= diagonal * 2.0) {
                    pair(end[0], end[1], turned, 2, 2, List.of(new Tap(key, turned)));
                }
            }
        }
        return bestError < d * BETTER ? bestPlan : List.of();
    }

    private void pair(double x, double z, double yaw, int spread, int spread2, List<Tap> before) {
        double dx = cx - x;
        double dz = cz - z;
        double d = Math.hypot(dx, dz);
        if (d < 1.0E-12) {
            return;
        }
        double toward = Math.toDegrees(Math.atan2(dz, dx));
        for (int type = 0; type < 4; type++) {
            boolean diag1 = (type & 1) != 0;
            boolean diag2 = (type & 2) != 0;
            double l1 = diag1 ? diagonal : straight;
            double l2 = diag2 ? diagonal : straight;
            if (d > l1 + l2 || d < Math.abs(l1 - l2)) {
                continue;
            }
            double spreadAngle = Math.toDegrees(Math.acos(Mth.clamp(((l1 * l1) + (d * d) - (l2 * l2)) / (2.0 * l1 * d),
                    -1.0, 1.0)));
            for (int side = -1; side <= 1; side += 2) {
                for (Tap first : near(toward + (side * spreadAngle), yaw, spread, diag1)) {
                    double[] mid = tap(x, z, first.key, first.yaw);
                    double back = Math.toDegrees(Math.atan2(cz - mid[1], cx - mid[0]));
                    for (Tap second : near(back, first.yaw, spread2, diag2)) {
                        List<Tap> plan = new ArrayList<>(before);
                        plan.add(first);
                        plan.add(second);
                        consider(tap(mid[0], mid[1], second.key, second.yaw), plan);
                    }
                }
            }
        }
    }

    private List<Tap> near(double angle, double yaw, int spread, boolean diag) {
        List<Tap> out = new ArrayList<>();
        for (int key = 0; key < KEYS.length; key++) {
            if ((KEYS[key][0] != 0 && KEYS[key][1] != 0) != diag) {
                continue;
            }
            double keyAngle = Math.toDegrees(Math.atan2(KEYS[key][1], KEYS[key][0]));
            long n = Math.round(Mth.wrapDegrees(angle - keyAngle - yaw) / pixel);
            for (long i = n - spread; i <= n + spread; i++) {
                if (Math.abs(i * pixel) <= MAX_TURN) {
                    out.add(new Tap(key, yaw + (i * pixel)));
                }
            }
        }
        return out;
    }

    private void consider(double[] end, List<Tap> plan) {
        double error = Math.hypot(cx - end[0], cz - end[1]);
        if (error < bestError) {
            bestError = error;
            bestPlan = plan;
        }
    }

    private double[] tap(double x, double z, int key, double yaw) {
        float rad = (float) yaw * (float) (Math.PI / 180.0);
        float sin = Mth.sin(rad);
        float cos = Mth.cos(rad);
        double mx = input[key][0] * speed;
        double mz = input[key][1] * speed;
        return rest(x, z, (mx * cos) - (mz * sin), (mz * cos) + (mx * sin));
    }

    private double[] rest(double x, double z, double vx, double vz) {
        for (int i = 0; i < 200 && (vx * vx) + (vz * vz) >= STOP_SQ; i++) {
            x += vx;
            z += vz;
            vx *= drag;
            vz *= drag;
        }
        return new double[]{x, z};
    }

    private static double length(double[] end) {
        return Math.hypot(end[0], end[1]);
    }

    static Vec2 squared(Vec2 move) {
        float length = move.length();
        if (length <= 0.0f) {
            return move;
        }
        Vec2 direction = move.scale(1.0f / length);
        float x = Math.abs(direction.x);
        float y = Math.abs(direction.y);
        float tan = y > x ? x / y : y / x;
        return direction.scale(Math.min(length * Mth.sqrt(1.0f + Mth.square(tan)), 1.0f));
    }
}
