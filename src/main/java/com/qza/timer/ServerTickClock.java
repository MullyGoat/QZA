package com.qza.timer;

public final class ServerTickClock {
    private static volatile long ticks;
    private static volatile boolean seen;

    private ServerTickClock() {
    }

    public static void onServerTick() {
        ticks++;
        seen = true;
    }

    public static long ticks() {
        return ticks;
    }

    public static boolean isAvailable() {
        return seen;
    }

    public static void reset() {
        seen = false;
    }
}
