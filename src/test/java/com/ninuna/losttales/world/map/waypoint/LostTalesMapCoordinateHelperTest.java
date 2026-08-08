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

    /**
     * Where the map draws a position is half a cell from where a waypoint
     * defined at that position is stored, so the two conversions must stay
     * apart: aiming the camera with the storage one framed the marker
     * sixty-four blocks off.
     */
    @Test
    public void theRenderedConversionRoundTripsThroughTheMapScreen() {
        int worldX = 12345;
        int worldZ = -6789;

        assertEquals(worldX,
                LostTalesMapCoordinateHelper.renderedMapImageXToWorld(
                        LostTalesMapCoordinateHelper
                                .worldToRenderedMapImageX(worldX)));
        assertEquals(worldZ,
                LostTalesMapCoordinateHelper.renderedMapImageZToWorld(
                        LostTalesMapCoordinateHelper
                                .worldToRenderedMapImageZ(worldZ)));
    }

    @Test
    public void theStoredAndDrawnConversionsAreHalfACellApart() {
        assertNotEquals(
                LostTalesMapCoordinateHelper.worldToMapImageX(1000.0D),
                LostTalesMapCoordinateHelper
                        .worldToRenderedMapImageX(1000.0D),
                0.0001D);
        assertEquals(0.5D,
                LostTalesMapCoordinateHelper
                        .worldToRenderedMapImageX(1000.0D)
                        - LostTalesMapCoordinateHelper
                                .worldToMapImageX(1000.0D),
                0.0001D);
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
