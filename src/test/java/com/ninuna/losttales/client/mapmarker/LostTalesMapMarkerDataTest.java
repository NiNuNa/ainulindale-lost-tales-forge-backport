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

    @Test
    public void foregroundSelectionUsesDistanceBeforeTiePriority() {
        assertTrue(LostTalesLotrMapMarkerIconOverlay
                .isBetterForegroundHit(4.0D, 1, 9.0D, 3));
        assertFalse(LostTalesLotrMapMarkerIconOverlay
                .isBetterForegroundHit(9.0D, 3, 4.0D, 1));
        assertTrue(LostTalesLotrMapMarkerIconOverlay
                .isBetterForegroundHit(4.0D, 3, 4.0D, 2));
        assertFalse(LostTalesLotrMapMarkerIconOverlay
                .isBetterForegroundHit(4.0D, 1, 4.0D, 2));
    }

    @Test
    public void edgeCullingWaitsUntilTheWholeStackLeavesTheMap() {
        assertTrue(LostTalesLotrMapMarkerIconOverlay
                .markerStackOverlapsMapBounds(
                        50.0F, -7.9F, 0.0F, 100.0F,
                        0.0F, 100.0F));
        assertFalse(LostTalesLotrMapMarkerIconOverlay
                .markerStackOverlapsMapBounds(
                        50.0F, -8.1F, 0.0F, 100.0F,
                        0.0F, 100.0F));
        // Sideways the fan reaches an extra 115% of the icon's half-width.
        assertTrue(LostTalesLotrMapMarkerIconOverlay
                .markerStackOverlapsMapBounds(
                        -17.1F, 50.0F, 0.0F, 100.0F,
                        0.0F, 100.0F));
        assertFalse(LostTalesLotrMapMarkerIconOverlay
                .markerStackOverlapsMapBounds(
                        -17.3F, 50.0F, 0.0F, 100.0F,
                        0.0F, 100.0F));
    }

    @Test
    public void tooltipTranslationRestoresFractionalMarkerMotion() {
        assertEquals(0.4F,
                LostTalesLotrMapMarkerIconOverlay.tooltipTranslation(
                        10.4F, 10.4F), 0.0001F);
        assertEquals(-0.4F,
                LostTalesLotrMapMarkerIconOverlay.tooltipTranslation(
                        10.6F, 10.6F), 0.0001F);
        assertEquals(0.9F,
                LostTalesLotrMapMarkerIconOverlay.tooltipTranslation(
                        10.4F, 10.9F), 0.0001F);
    }

    @Test
    public void foregroundIconsShareTheSameHighlightScale() {
        assertEquals(16.0F,
                LostTalesLotrMapMarkerIconOverlay.highlightedSize(13.0F),
                0.0001F);
        assertEquals(144.0F / 13.0F,
                LostTalesLotrMapMarkerIconOverlay.highlightedSize(9.0F),
                0.0001F);
        assertEquals(64.0F / 13.0F,
                LostTalesLotrMapMarkerIconOverlay.highlightedSize(4.0F),
                0.0001F);
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
