package com.ninuna.losttales.world.map.waypoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import lotr.common.world.map.LOTRWaypoint;
import org.junit.Test;

/** Regression coverage for the map/compass coordinate mismatch. */
public final class LostTalesMapCoordinateHelperTest {
    @Test
    public void exactConversionRoundTripsNonGridAlignedMarker() {
        int worldX = 15;
        int worldZ = 15;

        double mapX = LostTalesMapCoordinateHelper
                .worldToMapImageX(worldX);
        double mapZ = LostTalesMapCoordinateHelper
                .worldToMapImageZ(worldZ);

        assertEquals(worldX, LOTRWaypoint.mapToWorldX(mapX));
        assertEquals(worldZ, LOTRWaypoint.mapToWorldZ(mapZ));
    }

    @Test
    public void lotrIntegerConversionWouldSnapTheTestMarker() {
        int worldX = 15;
        int worldZ = 15;

        assertNotEquals(worldX,
                LOTRWaypoint.mapToWorldX(
                        LOTRWaypoint.worldToMapX(worldX)));
        assertNotEquals(worldZ,
                LOTRWaypoint.mapToWorldZ(
                        LOTRWaypoint.worldToMapZ(worldZ)));
    }
}
