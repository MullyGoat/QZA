package com.qza.waypoint;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class WaypointGroup {
    private static final int[][] SIDES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final List<Waypoint> members;

    private WaypointGroup(List<Waypoint> members) {
        this.members = List.copyOf(members);
    }

    public List<Waypoint> members() {
        return members;
    }

    public Waypoint first() {
        return members.get(0);
    }

    public boolean single() {
        return members.size() == 1;
    }

    public String name() {
        return first().name;
    }

    public String colour() {
        return first().colour;
    }

    public int argb() {
        return first().argb();
    }

    public boolean visible() {
        for (Waypoint waypoint : members) {
            if (waypoint.enabled) {
                return true;
            }
        }
        return false;
    }

    public void setVisible(boolean visible) {
        for (Waypoint waypoint : members) {
            waypoint.enabled = visible;
        }
    }

    public void setName(String name) {
        for (Waypoint waypoint : members) {
            waypoint.name = name;
        }
    }

    public void setColour(String colour) {
        for (Waypoint waypoint : members) {
            waypoint.colour = colour;
        }
    }

    public int[] coords() {
        if (single()) {
            return new int[]{first().x, first().y, first().z};
        }
        return new int[]{(int) minX(), (int) minY(), (int) minZ()};
    }

    public void moveTo(int x, int y, int z) {
        int[] at = coords();
        int dx = x - at[0];
        int dy = y - at[1];
        int dz = z - at[2];
        for (Waypoint waypoint : members) {
            waypoint.x += dx;
            waypoint.y += dy;
            waypoint.z += dz;
        }
    }

    public String coordsText() {
        int[] at = coords();
        return at[0] + " " + at[1] + " " + at[2];
    }

    public String label() {
        String name = name();
        return name == null || name.isBlank() ? coordsText() : name;
    }

    public String sizeText() {
        return single() ? first().sizeText() : members.size() + " blocks";
    }

    public double minX() {
        double min = Double.MAX_VALUE;
        for (Waypoint waypoint : members) {
            min = Math.min(min, waypoint.minX());
        }
        return min;
    }

    public double minY() {
        double min = Double.MAX_VALUE;
        for (Waypoint waypoint : members) {
            min = Math.min(min, waypoint.minY());
        }
        return min;
    }

    public double minZ() {
        double min = Double.MAX_VALUE;
        for (Waypoint waypoint : members) {
            min = Math.min(min, waypoint.minZ());
        }
        return min;
    }

    public double maxX() {
        double max = -Double.MAX_VALUE;
        for (Waypoint waypoint : members) {
            max = Math.max(max, waypoint.maxX());
        }
        return max;
    }

    public double maxY() {
        double max = -Double.MAX_VALUE;
        for (Waypoint waypoint : members) {
            max = Math.max(max, waypoint.maxY());
        }
        return max;
    }

    public double maxZ() {
        double max = -Double.MAX_VALUE;
        for (Waypoint waypoint : members) {
            max = Math.max(max, waypoint.maxZ());
        }
        return max;
    }

    public String command() {
        int width = (int) (maxX() - minX());
        int height = (int) (maxY() - minY());
        int depth = (int) (maxZ() - minZ());
        if (!single() && Math.max(width, Math.max(height, depth)) > WaypointSize.MAX) {
            return null;
        }
        String size = height == 1 ? width + "x" + depth : width + "x" + height + "x" + depth;
        int x = (int) minX() + ((width - 1) / 2);
        int y = (int) minY() + ((height - 1) / 2);
        int z = (int) minZ() + ((depth - 1) / 2);

        if (single()) {
            Waypoint only = first();
            x = only.x;
            y = only.y;
            z = only.z;
            size = only.sizeText();
        } else if (members.size() != width * height * depth) {
            BigInteger mask = BigInteger.ZERO;
            for (Waypoint waypoint : members) {
                int index = ((waypoint.y - (int) minY()) * depth + (waypoint.z - (int) minZ())) * width
                        + (waypoint.x - (int) minX());
                mask = mask.setBit(index);
            }
            size = size + "." + mask.toString(16);
        }

        String colour = colour().startsWith("#") ? colour().substring(1) : colour();
        String command = "/qza waypoint add " + x + " " + y + " " + z + " " + colour + " " + size;
        String name = name();
        return name == null || name.isBlank() ? command : command + " " + name.trim();
    }

    public static List<int[]> shape(int x, int y, int z, WaypointSize.Size size, String mask) {
        BigInteger bits;
        try {
            bits = new BigInteger(mask, 16);
        } catch (NumberFormatException e) {
            return null;
        }
        int cells = size.width() * size.height() * size.depth();
        if (bits.signum() <= 0 || bits.bitLength() > cells) {
            return null;
        }
        int minX = x - ((size.width() - 1) / 2);
        int minY = y - ((size.height() - 1) / 2);
        int minZ = z - ((size.depth() - 1) / 2);
        List<int[]> blocks = new ArrayList<>();
        for (int index = 0; index < cells; index++) {
            if (!bits.testBit(index)) {
                continue;
            }
            int dx = index % size.width();
            int dz = (index / size.width()) % size.depth();
            int dy = index / (size.width() * size.depth());
            blocks.add(new int[]{minX + dx, minY + dy, minZ + dz});
        }
        return blocks;
    }

    static List<WaypointGroup> of(List<Waypoint> waypoints) {
        int count = waypoints.size();
        int[] parent = new int[count];
        Map<Long, Integer> blocks = new HashMap<>();
        for (int i = 0; i < count; i++) {
            parent[i] = i;
            Waypoint waypoint = waypoints.get(i);
            if (block(waypoint)) {
                blocks.putIfAbsent(pack(waypoint.x, waypoint.y, waypoint.z), i);
            }
        }

        for (int i = 0; i < count; i++) {
            Waypoint waypoint = waypoints.get(i);
            if (!block(waypoint)) {
                continue;
            }
            for (int[] side : SIDES) {
                Integer other = blocks.get(pack(waypoint.x + side[0], waypoint.y + side[1],
                        waypoint.z + side[2]));
                if (other != null && joins(waypoint, waypoints.get(other))) {
                    union(parent, i, other);
                }
            }
        }

        Map<Integer, List<Waypoint>> grouped = new HashMap<>();
        List<List<Waypoint>> ordered = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int root = find(parent, i);
            List<Waypoint> members = grouped.get(root);
            if (members == null) {
                members = new ArrayList<>();
                grouped.put(root, members);
                ordered.add(members);
            }
            members.add(waypoints.get(i));
        }

        List<WaypointGroup> out = new ArrayList<>(ordered.size());
        for (List<Waypoint> members : ordered) {
            out.add(new WaypointGroup(members));
        }
        return out;
    }

    static Waypoint neighbour(List<Waypoint> waypoints, int x, int y, int z) {
        for (int[] side : SIDES) {
            for (Waypoint waypoint : waypoints) {
                if (block(waypoint) && waypoint.sameBlock(x + side[0], y + side[1], z + side[2])) {
                    return waypoint;
                }
            }
        }
        return null;
    }

    static boolean joins(Waypoint a, Waypoint b) {
        return block(a) && block(b) && Objects.equals(a.colour, b.colour) && Objects.equals(a.name, b.name);
    }

    private static boolean block(Waypoint waypoint) {
        return waypoint.width == 1 && waypoint.height == 1 && waypoint.depth == 1;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) {
            parent[Math.max(rootA, rootB)] = Math.min(rootA, rootB);
        }
    }
}
