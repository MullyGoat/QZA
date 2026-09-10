package com.qza.timer;

public final class ServerTickClock {
    private static final double NANOS_PER_TICK = 50_000_000.0;
    private static final long MAX_PLAUSIBLE_INTERVAL = 200L;

    private static volatile long lastServerTick = -1L;
    private static volatile long lastSyncNanos;
    private static volatile long syncIntervalTicks = 20L;

    private ServerTickClock() {
    }

    public static void onServerGameTime(long gameTime) {
        long now = System.nanoTime();
        long previous = lastServerTick;
        if (previous >= 0L) {
            long delta = gameTime - previous;
            if (delta > 0L && delta <= MAX_PLAUSIBLE_INTERVAL) {
                syncIntervalTicks = delta;
            }
        }
        lastServerTick = gameTime;
        lastSyncNanos = now;
    }

    public static boolean isAvailable() {
        return lastServerTick >= 0L;
    }

    public static double ticksNow() {
        long base = lastServerTick;
        if (base < 0L) {
            return -1.0;
        }
        double sinceSync = (System.nanoTime() - lastSyncNanos) / NANOS_PER_TICK;
        return base + Math.min(sinceSync, syncIntervalTicks);
    }

    public static void reset() {
        lastServerTick = -1L;
        syncIntervalTicks = 20L;
    }
}
