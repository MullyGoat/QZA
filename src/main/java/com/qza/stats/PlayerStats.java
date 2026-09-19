package com.qza.stats;

import java.util.Map;

public record PlayerStats(
        String name,
        String uuid,
        String dungeonClass,
        long cataExp,
        long secrets,

        Integer magicalPower,
        Map<Integer, Long> pbCata,
        Map<Integer, Long> pbMaster,
        Map<Integer, Integer> runsCata,
        Map<Integer, Integer> runsMaster) {

    public int cataLevel() {
        return CataLevel.of(cataExp);
    }

    public String role() {
        return DungeonClass.of(dungeonClass);
    }

    public String magicalPowerLabel() {
        return magicalPower == null ? "API Off" : String.valueOf(magicalPower);
    }

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

    public double secretAverage() {
        int runs = totalRuns();
        return runs <= 0 ? 0.0 : secrets / (double) runs;
    }

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
