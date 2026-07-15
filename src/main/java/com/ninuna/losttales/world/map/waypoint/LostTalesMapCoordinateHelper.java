package com.ninuna.losttales.world.map.waypoint;

import lotr.common.world.genlayer.LOTRGenLayerWorld;

/** Exact coordinate conversion for positions that are not aligned to LOTR's map grid. */
public final class LostTalesMapCoordinateHelper {
    private static final double MAP_X_ORIGIN = 810.0D;
    private static final double MAP_Z_ORIGIN = 730.0D;
    private static final double BLOCK_CENTER_OFFSET = 0.5D;

    private LostTalesMapCoordinateHelper() {}

    /**
     * Converts a world X coordinate without the integer rounding performed by
     * {@code LOTRWaypoint.worldToMapX}. LOTR waypoint constructors accept a
     * double, so preserving the fraction prevents 128-block grid snapping.
     */
    public static double worldToMapImageX(double worldX) {
        return worldX / (double) LOTRGenLayerWorld.scale
                - BLOCK_CENTER_OFFSET + MAP_X_ORIGIN;
    }

    /** Exact Z counterpart to {@link #worldToMapImageX(double)}. */
    public static double worldToMapImageZ(double worldZ) {
        return worldZ / (double) LOTRGenLayerWorld.scale
                - BLOCK_CENTER_OFFSET + MAP_Z_ORIGIN;
    }
}
