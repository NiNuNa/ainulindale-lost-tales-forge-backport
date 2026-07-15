package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Regression coverage for the intentionally different map/compass policy. */
public final class LostTalesClientMapMarkerVisibilityTest {
    @Test
    public void hiddenUndiscoveredMarkerIsCompassVisibleButMapHidden() {
        LostTalesMapMarkerData marker = marker(true, true, false);

        assertFalse(LostTalesClientMapMarkerVisibility
                .isUndiscoveredRegionVisible(marker));
        assertTrue(LostTalesClientMapMarkerVisibility
                .isUndiscoveredCompassVisible(marker));
        assertFalse(LostTalesClientMapMarkerVisibility
                .isMapVisible(marker));
    }

    @Test
    public void nonDiscoverableMarkerIgnoresHiddenFlag() {
        LostTalesMapMarkerData marker = marker(false, true, false);

        assertTrue(LostTalesClientMapMarkerVisibility
                .isRegionRequirementMet(marker));
        assertTrue(LostTalesClientMapMarkerVisibility
                .isNonDiscoverableVisible(marker));
        assertTrue(LostTalesClientMapMarkerVisibility
                .isMapVisible(marker));
    }

    private static LostTalesMapMarkerData marker(
            boolean discoverable, boolean hiddenUntilDiscovered,
            boolean requiresRegionUnlock) {
        return new LostTalesMapMarkerData(
                "losttales:test_hidden_compass_marker",
                "Hidden marker",
                "fort",
                "white",
                LostTalesMapMarkerData.CATEGORY_POINT_OF_INTEREST,
                false,
                100,
                0.0D,
                64.0D,
                0.0D,
                128.0D,
                8.0D,
                hiddenUntilDiscovered,
                discoverable,
                requiresRegionUnlock);
    }
}
