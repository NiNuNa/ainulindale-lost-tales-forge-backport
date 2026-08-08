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

    /**
     * Where the map screen actually draws a world position.
     *
     * <p>Half a map pixel from {@link #worldToMapImageX(double)}, and
     * deliberately so. A waypoint stores the cell it sits in, and LOTR draws
     * it at the middle of that cell, so the position a waypoint is
     * <em>defined</em> at and the position it is <em>drawn</em> at differ by
     * half a cell — sixty-four blocks. Anything aimed at what is on screen,
     * the map camera above all, has to use this one.</p>
     */
    public static double worldToRenderedMapImageX(double worldX) {
        return worldX / (double) LOTRGenLayerWorld.scale + MAP_X_ORIGIN;
    }

    /** Z counterpart to {@link #worldToRenderedMapImageX(double)}. */
    public static double worldToRenderedMapImageZ(double worldZ) {
        return worldZ / (double) LOTRGenLayerWorld.scale + MAP_Z_ORIGIN;
    }

    /**
     * Inverse of {@link #worldToRenderedMapImageX(double)}, rounded to a block
     * exactly as LOTR rounds the coordinate under the pointer, so a position
     * derived here and one derived by the base mod name the same block.
     */
    public static int renderedMapImageXToWorld(double mapImageX) {
        return Math.round((float)((mapImageX - MAP_X_ORIGIN)
                * (double) LOTRGenLayerWorld.scale));
    }

    /** Z counterpart to {@link #renderedMapImageXToWorld(double)}. */
    public static int renderedMapImageZToWorld(double mapImageZ) {
        return Math.round((float)((mapImageZ - MAP_Z_ORIGIN)
                * (double) LOTRGenLayerWorld.scale));
    }
}
