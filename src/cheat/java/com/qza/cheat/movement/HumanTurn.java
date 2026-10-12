package com.qza.cheat.movement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class HumanTurn {
    private static final double MIN_TURN = 0.5;
    private static final double OVERSHOOT_FROM = 6.0;

    private final List<Leg> legs = new ArrayList<>();
    private final long startNanos;
    private double totalMs;

    private HumanTurn(long startNanos) {
        this.startNanos = startNanos;
    }

    static HumanTurn plan(float fromYaw, float fromPitch, float toYaw, float toPitch) {
        double dYaw = toYaw - fromYaw;
        double dPitch = toPitch - fromPitch;
        double distance = Math.hypot(dYaw, dPitch);
        if (distance < MIN_TURN) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        HumanTurn turn = new HumanTurn(System.nanoTime());
        double react = 60.0 + (random.nextDouble() * 90.0);
        double time = (95.0 + (120.0 * Math.log(1.0 + (distance / 2.5)) / Math.log(2.0)))
                * (0.85 + (random.nextDouble() * 0.3));
        double side = random.nextBoolean() ? 1.0 : -1.0;

        if (distance > OVERSHOOT_FROM) {
            double ux = dYaw / distance;
            double uy = dPitch / distance;
            double along = distance * (random.nextDouble() < 0.7
                    ? 0.02 + (random.nextDouble() * 0.05)
                    : -(0.02 + (random.nextDouble() * 0.04)));
            double across = distance * random.nextGaussian() * 0.012;
            double missYaw = toYaw + (ux * along) - (uy * across);
            double missPitch = toPitch + (uy * along) + (ux * across);
            double main = time * 0.78;
            turn.leg(fromYaw, fromPitch, missYaw, missPitch, react, main,
                    side * distance * (0.03 + (random.nextDouble() * 0.06)));
            double settle = 15.0 + (random.nextDouble() * 45.0);
            turn.leg(missYaw, missPitch, toYaw, toPitch, react + main + settle, Math.max(70.0, time * 0.3), 0.0);
        } else {
            turn.leg(fromYaw, fromPitch, toYaw, toPitch, react, time,
                    side * distance * (0.02 + (random.nextDouble() * 0.05)));
        }
        return turn;
    }

    private void leg(double fromYaw, double fromPitch, double toYaw, double toPitch, double startMs,
                     double durationMs, double curve) {
        legs.add(new Leg(fromYaw, fromPitch, toYaw, toPitch, startMs, durationMs, curve));
        totalMs = Math.max(totalMs, startMs + durationMs);
    }

    double totalMs() {
        return totalMs;
    }

    boolean done(long nanos) {
        return (nanos - startNanos) / 1.0E6 >= totalMs;
    }

    double[] at(long nanos) {
        double ms = (nanos - startNanos) / 1.0E6;
        Leg first = legs.get(0);
        double[] current = {first.fromYaw, first.fromPitch};
        for (Leg leg : legs) {
            if (ms <= leg.startMs) {
                return current;
            }
            double tau = Math.min(1.0, (ms - leg.startMs) / leg.durationMs);
            current = leg.at(tau);
            if (tau < 1.0) {
                return current;
            }
        }
        return current;
    }

    private record Leg(double fromYaw, double fromPitch, double toYaw, double toPitch, double startMs,
                       double durationMs, double curve) {

        double[] at(double tau) {
            double s = (tau * tau * tau) * (10.0 + (tau * ((tau * 6.0) - 15.0)));
            double dx = toYaw - fromYaw;
            double dy = toPitch - fromPitch;
            double yaw = fromYaw + (dx * s);
            double pitch = fromPitch + (dy * s);
            double length = Math.hypot(dx, dy);
            if (length > 1.0E-6 && curve != 0.0) {
                double bump = curve * Math.sin(Math.PI * s);
                yaw += (-dy / length) * bump;
                pitch += (dx / length) * bump;
            }
            return new double[]{yaw, pitch};
        }
    }
}
