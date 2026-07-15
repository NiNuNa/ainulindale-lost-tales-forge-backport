package com.ninuna.losttales.gui.hud.compass.marker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression coverage for non-fading Go Here compass markers. */
public final class LostTalesCompassMarkerTest {

    @Test
    public void alwaysVisibleMarkerDisablesDistanceFade() {
        LostTalesCompassMarker marker =
                LostTalesCompassMarker.alwaysVisiblePositionWithStateKey(
                        "party_go_here:test", "Go Here",
                        LostTalesCompassMarkerIcon.QUEST,
                        30000000.0D, 64.0D, -30000000.0D,
                        true, true, "green");

        assertTrue(marker.isForceFullOpacity());
        assertTrue(marker.isRetainedBeyondFadeRadius());
        assertEquals(0.0D, marker.getFadeInRadius(), 0.0D);
    }
}
