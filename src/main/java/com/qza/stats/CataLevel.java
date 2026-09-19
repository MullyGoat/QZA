package com.qza.stats;

public final class CataLevel {
    public static final int MAX = 50;

    private static final long OVERFLOW_PER_LEVEL = 200_000_000L;

    private static final long[] STEPS = {
            50, 75, 110, 160, 230, 330, 470, 670, 950, 1_340,
            1_890, 2_665, 3_760, 5_260, 7_380, 10_300, 14_400, 20_000, 27_600, 38_000,
            52_500, 71_500, 97_000, 132_000, 180_000, 243_000, 328_000, 445_000, 600_000, 800_000,
            1_065_000, 1_410_000, 1_900_000, 2_500_000, 3_300_000, 4_300_000, 5_600_000,
            7_200_000, 9_200_000, 12_000_000,
            15_000_000, 19_000_000, 24_000_000, 30_000_000, 38_000_000, 48_000_000,
            60_000_000, 75_000_000, 93_000_000, 116_250_000,
    };

    private static final long[] TOTALS = new long[MAX + 1];

    static {
        long running = 0;
        for (int level = 1; level <= MAX; level++) {
            running += STEPS[level - 1];
            TOTALS[level] = running;
        }
    }

    private CataLevel() {
    }

    public static long maxExperience() {
        return TOTALS[MAX];
    }

    public static int of(long experience) {
        if (experience <= 0) {
            return 0;
        }
        if (experience >= TOTALS[MAX]) {
            long overflow = experience - TOTALS[MAX];
            return MAX + (int) (overflow / OVERFLOW_PER_LEVEL);
        }

        int level = 0;
        for (int candidate = 1; candidate <= MAX; candidate++) {
            if (experience >= TOTALS[candidate]) {
                level = candidate;
            } else {
                break;
            }
        }
        return level;
    }
}
