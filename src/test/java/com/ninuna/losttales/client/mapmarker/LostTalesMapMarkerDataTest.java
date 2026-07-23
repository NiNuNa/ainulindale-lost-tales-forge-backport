package com.ninuna.losttales.client.mapmarker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerDataTest {
    @Test
    public void waystoneCompassTargetUsesBlockCenter() {
        LostTalesMapMarkerData marker = marker(true);

        assertEquals(12.5D, marker.getCompassTargetX(), 0.0D);
        assertEquals(-7.5D, marker.getCompassTargetZ(), 0.0D);
    }

    @Test
    public void ordinaryMarkerCompassTargetPreservesExactPosition() {
        LostTalesMapMarkerData marker = marker(false);

        assertEquals(12.0D, marker.getCompassTargetX(), 0.0D);
        assertEquals(-8.0D, marker.getCompassTargetZ(), 0.0D);
    }

    @Test
    public void blankDescriptionRemainsBlankForNativeLoreFallback() {
        assertEquals("", marker(false).getDescription());
    }

    @Test
    public void decorativeAnimationOmitsHiddenDefinitions() {
        LostTalesMapMarkerData visible = marker(false);
        LostTalesMapMarkerData hidden =
                new LostTalesMapMarkerData(
                        "losttales:hidden", "Hidden", "fort", "white",
                        "Map Marker", "", false,
                        lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID,
                        12.0D, 64.0D, -8.0D,
                        128.0D, 8.0D,
                        true, true, false, false);

        assertTrue(
                LostTalesLotrMapMarkerIconOverlay
                        .shouldRenderDecorativeMarker(visible));
        assertFalse(
                LostTalesLotrMapMarkerIconOverlay
                        .shouldRenderDecorativeMarker(hidden));
    }

    private static LostTalesMapMarkerData marker(boolean hasWaystone) {
        return new LostTalesMapMarkerData(
                "losttales:test", "Test", "fort", "white",
                "Map Marker", "", false,
                lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID,
                12.0D, 64.0D, -8.0D,
                128.0D, 8.0D,
                false, false, false, hasWaystone);
    }
}
