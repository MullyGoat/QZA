package com.qza.waypoint;

public class Waypoint {
    public int x;
    public int y;
    public int z;

    public int width = 1;
    public int height = 1;
    public int depth = 1;

    public String colour = "blue";
    public String name = "";
    public boolean enabled = true;

    public Waypoint() {
    }

    public Waypoint(int x, int y, int z, String colour, int width, int height, int depth,
                    String name) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.colour = colour;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.name = name == null ? "" : name;
    }

    public String label() {
        return name == null || name.isBlank() ? x + " " + y + " " + z : name;
    }

    public String sizeText() {
        return height == 1 ? width + "x" + depth : width + "x" + height + "x" + depth;
    }

    public boolean sameBlock(int ox, int oy, int oz) {
        return x == ox && y == oy && z == oz;
    }

    public int argb() {
        return WaypointColour.argb(colour);
    }

    public double minX() {
        return x - (width - 1) / 2.0;
    }

    public double minY() {
        return y - (height - 1) / 2.0;
    }

    public double minZ() {
        return z - (depth - 1) / 2.0;
    }

    public double maxX() {
        return minX() + width;
    }

    public double maxY() {
        return minY() + height;
    }

    public double maxZ() {
        return minZ() + depth;
    }
}
