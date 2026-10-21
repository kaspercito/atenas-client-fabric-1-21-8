package com.atenas.fabric.shared;

/** Mismo modelo que el cliente 1.8.9 (nav/Waypoint.java): el JSON es compartido. */
public final class Waypoint {
    public final String id;
    public String name;
    public double x, y, z;
    public int dim;       // 0 = overworld, -1 = nether, 1 = end (igual que 1.8.9); otras dimensiones: un id estable > 1
    public int color;
    public boolean visible = true;
    public boolean death;

    public Waypoint(String id, String name, double x, double y, double z, int dim, int color) {
        this.id = id; this.name = name; this.x = x; this.y = y; this.z = z; this.dim = dim; this.color = color;
    }
}
