package com.qza.cheat.movement;

import net.minecraft.core.BlockPos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Recording {
    public static final double MIN_SPEED = 1.0;
    public static final double MAX_SPEED = 5.0;
    public static final double SECONDS_PER_FRAME = 0.05;

    public static final String START_AUTO = "auto";
    public static final String START_TELEPORT = "teleport";
    public static final String START_WALK = "walk";

    private static final double STILL_SQ = 0.003 * 0.003;
    private static final int SETTLE_AFTER_TELEPORT = 6;
    private static final int SETTLE_BEFORE_CLICK = 3;

    public String name = "";
    public long created;
    public int trimStart;
    public int trimEnd;
    public double speed = MIN_SPEED;
    public int[] block;
    public String area = "";
    public String trigger = START_AUTO;
    public List<MoveFrame> frames = new ArrayList<>();

    transient Path file;

    private transient int[] cached;
    private transient int cachedFrom = -1;
    private transient int cachedTo = -1;
    private transient int cachedSize = -1;
    private transient double cachedSpeed;

    public boolean walkIn() {
        if (START_WALK.equals(trigger)) {
            return true;
        }
        if (START_TELEPORT.equals(trigger)) {
            return false;
        }
        return DungeonBoss.inBoss();
    }

    public void nextTrigger() {
        trigger = START_AUTO.equals(trigger) ? START_TELEPORT : START_TELEPORT.equals(trigger) ? START_WALK : START_AUTO;
    }

    public String triggerLabel() {
        if (START_WALK.equals(trigger)) {
            return "Walk into the ring on the block";
        }
        if (START_TELEPORT.equals(trigger)) {
            return "Etherwarp onto the block";
        }
        return "Auto: etherwarp in rooms, walk into the ring in boss";
    }

    public String startHint() {
        if (START_WALK.equals(trigger)) {
            return "Walk into the ring on it to play it.";
        }
        if (START_TELEPORT.equals(trigger)) {
            return "Etherwarp onto it to play it.";
        }
        return "Before boss, etherwarp onto it. In boss, walk into the ring on it.";
    }

    public int size() {
        return frames == null ? 0 : frames.size();
    }

    public void clampTrim() {
        int size = size();
        trimStart = Math.max(0, Math.min(trimStart, Math.max(0, size - 1)));
        trimEnd = Math.max(0, Math.min(trimEnd, Math.max(0, size - 1 - trimStart)));
        speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }

    public int start() {
        clampTrim();
        return trimStart;
    }

    public int end() {
        clampTrim();
        return size() - trimEnd;
    }

    public double seconds() {
        return size() * SECONDS_PER_FRAME;
    }

    public double keptSeconds() {
        return Math.max(0, end() - start()) * SECONDS_PER_FRAME;
    }

    public double playSeconds() {
        return schedule().length * SECONDS_PER_FRAME;
    }

    public boolean placed() {
        return block != null && block.length == 3;
    }

    public BlockPos blockPos() {
        return placed() ? new BlockPos(block[0], block[1], block[2]) : null;
    }

    public String blockText() {
        return placed() ? block[0] + ", " + block[1] + ", " + block[2] : "";
    }

    public boolean moving(int index) {
        MoveFrame frame = frames.get(index);
        if ((frame.keys & MoveFrame.MOVE_KEYS) != 0 || !frame.ground) {
            return true;
        }
        if (index > 0 && frames.get(index - 1).distanceSq(frame) > STILL_SQ) {
            return true;
        }
        return index + 1 < frames.size() && frame.distanceSq(frames.get(index + 1)) > STILL_SQ;
    }

    public boolean compressible(int index) {
        if (moving(index)) {
            return false;
        }
        MoveFrame frame = frames.get(index);
        if ((frame.keys & (MoveFrame.ATTACK | MoveFrame.USE)) != 0 || frame.clicked()) {
            return false;
        }
        if (index > 0) {
            MoveFrame previous = frames.get(index - 1);
            if (previous.slot != frame.slot || previous.held(MoveFrame.SNEAK) != frame.held(MoveFrame.SNEAK)) {
                return false;
            }
        }
        if (frames.get(Math.max(0, index - SETTLE_AFTER_TELEPORT)).teleports != frame.teleports) {
            return false;
        }
        int last = Math.min(frames.size() - 1, index + SETTLE_BEFORE_CLICK);
        for (int i = index + 1; i <= last; i++) {
            MoveFrame ahead = frames.get(i);
            if (ahead.clicked() || ahead.slot != frame.slot
                    || (ahead.held(MoveFrame.USE) && !frames.get(i - 1).held(MoveFrame.USE))) {
                return false;
            }
        }
        return true;
    }

    public int[] schedule() {
        int from = start();
        int to = end();
        if (cached != null && cachedFrom == from && cachedTo == to && cachedSize == size()
                && cachedSpeed == speed) {
            return cached;
        }
        cached = build(from, to);
        cachedFrom = from;
        cachedTo = to;
        cachedSize = size();
        cachedSpeed = speed;
        return cached;
    }

    private int[] build(int from, int to) {
        if (to <= from) {
            return new int[0];
        }
        double rate = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
        int[] out = new int[to - from];
        int count = 0;
        double credit = 0.0;
        for (int i = from; i < to; i++) {
            if (rate <= MIN_SPEED || !compressible(i)) {
                out[count++] = i;
                credit = 0.0;
                continue;
            }
            credit += 1.0 / rate;
            boolean lastStill = i + 1 >= to || !compressible(i + 1);
            if (credit >= 1.0 - 1.0E-9 || lastStill) {
                out[count++] = i;
                credit = Math.max(0.0, credit - 1.0);
            }
        }
        return Arrays.copyOf(out, count);
    }
}
