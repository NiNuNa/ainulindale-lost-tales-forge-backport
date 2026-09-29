package com.ninuna.losttales.compat.lotr;

/** Immutable public-API result used by the guarded location transition. */
public final class LotrStartingWaypointLocation {
    private final double x;
    private final double y;
    private final double z;

    public LotrStartingWaypointLocation(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() { return this.x; }
    public double getY() { return this.y; }
    public double getZ() { return this.z; }
}
