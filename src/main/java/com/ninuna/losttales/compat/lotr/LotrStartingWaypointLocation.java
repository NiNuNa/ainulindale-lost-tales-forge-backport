package com.ninuna.losttales.compat.lotr;

/** Immutable public-API result used by the guarded location transition. */
public final class LotrStartingWaypointLocation {
    private final int dimensionId;
    private final double x;
    private final double y;
    private final double z;

    public LotrStartingWaypointLocation(int dimensionId,
                                        double x, double y, double z) {
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getDimensionId() { return this.dimensionId; }
    public double getX() { return this.x; }
    public double getY() { return this.y; }
    public double getZ() { return this.z; }
}
