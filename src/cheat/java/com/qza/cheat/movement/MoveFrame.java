package com.qza.cheat.movement;

public final class MoveFrame {
    public static final int FORWARD = 1;
    public static final int BACK = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int JUMP = 1 << 4;
    public static final int SNEAK = 1 << 5;
    public static final int SPRINT = 1 << 6;
    public static final int ATTACK = 1 << 7;
    public static final int USE = 1 << 8;

    public static final int MOVE_KEYS = FORWARD | BACK | LEFT | RIGHT | JUMP;

    public static final int CLICK_ATTACK = 0;
    public static final int CLICK_USE = 1;
    public static final int CLICK_PICK = 2;
    public static final int CLICK_DROP = 3;
    public static final int CLICK_OFFHAND = 4;
    public static final int CLICK_HOTBAR = 5;
    public static final int CLICK_KEYS = CLICK_HOTBAR + 9;

    public float yaw;
    public float pitch;
    public int keys;
    public int slot;
    public int[] clicks;
    public float x;
    public float y;
    public float z;
    public boolean ground;
    public int teleports;
    public boolean screen;
    public float[] aim;

    public boolean held(int bit) {
        return (keys & bit) != 0;
    }

    public int clicks(int index) {
        return clicks == null || index < 0 || index >= clicks.length ? 0 : clicks[index];
    }

    public boolean clicked() {
        if (clicks == null) {
            return false;
        }
        for (int count : clicks) {
            if (count > 0) {
                return true;
            }
        }
        return false;
    }

    public double distanceSq(MoveFrame other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return (dx * dx) + (dy * dy) + (dz * dz);
    }
}
