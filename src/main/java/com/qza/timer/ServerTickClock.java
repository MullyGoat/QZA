package com.qza.timer;

public final class ServerTickClock {
    private static final double TICK_MS = 50.0;
    private static final long FRESH_NANOS = 1_000_000_000L;

    private static volatile long ticks;
    private static volatile boolean seen;
    private static volatile long lastTickNanos;

    private ServerTickClock() {
    }

    public static void onServerTick() {
        ticks++;
        lastTickNanos = System.nanoTime();
        seen = true;
    }

    public static long ticks() {
        return ticks;
    }

    public static boolean isAvailable() {
        return seen;
    }

    public static boolean ticking() {
        return seen && System.nanoTime() - lastTickNanos < FRESH_NANOS;
    }

    public static double partialTick() {
        double since = (System.nanoTime() - lastTickNanos) / 1_000_000.0;
        return Math.max(0.0, Math.min(1.0, since / TICK_MS));
    }

    public static void reset() {
        seen = false;
    }
}
