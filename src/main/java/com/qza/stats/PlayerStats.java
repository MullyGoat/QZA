package com.qza.stats;

import java.util.Map;

/**
 * What the stats proxy knows about one player's dungeon record.
 *
 * Personal bests are milliseconds keyed by floor number, split into normal and
 * master, matching Hypixel's own split.
 */
public record PlayerStats(
        String name,
        String uuid,
        String dungeonClass,
        long cataExp,
        long secrets,
        /** Null when it could not be read, which usually means their API is off. */
        Integer magicalPower,
        Map<Integer, Long> pbCata,
        Map<Integer, Long> pbMaster,
        Map<Integer, Integer> runsCata,
        Map<Integer, Integer> runsMaster) {

    public int cataLevel() {
        return CataLevel.of(cataExp);
    }

    /** Their selected class as a canonical name, or null when they have none. */
    public String role() {
        return DungeonClass.of(dungeonClass);
    }

    /** "1420", or "API Off" when the accessory bag could not be read. */
    public String magicalPowerLabel() {
        return magicalPower == null ? "API Off" : String.valueOf(magicalPower);
    }

    /** Every completion on every floor, entrance included, as the community counts it. */
    public int totalRuns() {
        int total = 0;
        for (int value : runsCata.values()) {
            total += value;
        }
        for (int value : runsMaster.values()) {
            total += value;
        }
        return total;
    }

    /** Secrets per run, or 0 when they have no runs to average over. */
    public double secretAverage() {
        int runs = totalRuns();
        return runs <= 0 ? 0.0 : secrets / (double) runs;
    }

    /** Best time on one floor in milliseconds, or 0 when they have never S+'d it. */
    public long pbMillis(String floorKey) {
        Map<Integer, Long> source = DungeonFloor.master(floorKey) ? pbMaster : pbCata;
        Long value = source.get(DungeonFloor.number(floorKey));
        return value == null || value <= 0 ? 0L : value;
    }

    public int runsOn(String floorKey) {
        Map<Integer, Integer> source = DungeonFloor.master(floorKey) ? runsMaster : runsCata;
        Integer value = source.get(DungeonFloor.number(floorKey));
        return value == null ? 0 : value;
    }
}
