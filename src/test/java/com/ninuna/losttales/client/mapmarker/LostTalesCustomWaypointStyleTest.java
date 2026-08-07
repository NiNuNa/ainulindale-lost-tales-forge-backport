package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.config.LostTalesConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class LostTalesCustomWaypointStyleTest {
    private String[] savedColors;

    @Before
    public void captureConfig() {
        this.savedColors = LostTalesConfig.customWaypointColors;
        LostTalesConfig.customWaypointColors = new String[0];
    }

    @After
    public void restoreConfig() {
        LostTalesConfig.customWaypointColors = this.savedColors;
    }

    @Test
    public void anUnknownWaypointFallsBackToItsKindDefault() {
        assertEquals(LostTalesCustomWaypointStyle.DEFAULT_PERSONAL_COLOR,
                LostTalesCustomWaypointStyle.getColor("Camp", false));
        assertEquals(LostTalesCustomWaypointStyle.DEFAULT_SHARED_COLOR,
                LostTalesCustomWaypointStyle.getColor("Camp", true));
    }

    @Test
    public void storedColoursAreReadBackWhateverTheCasing() {
        LostTalesConfig.customWaypointColors =
                new String[] {"camp=green"};

        assertEquals("green",
                LostTalesCustomWaypointStyle.getColor("Camp", false));
        assertEquals("green",
                LostTalesCustomWaypointStyle.getColor("  CAMP  ", false));
        // A colour is presentation only, so it applies to a shared waypoint
        // just as it does to a personal one.
        assertEquals("green",
                LostTalesCustomWaypointStyle.getColor("camp", true));
    }

    @Test
    public void malformedAndUnknownEntriesAreIgnored() {
        LostTalesConfig.customWaypointColors = new String[] {
                "no-separator", "=green", "camp=chartreuse", null
        };

        assertEquals(LostTalesCustomWaypointStyle.DEFAULT_PERSONAL_COLOR,
                LostTalesCustomWaypointStyle.getColor("camp", false));
    }

    @Test
    public void namesTheConfigCannotRoundTripHaveNoStoredColour() {
        assertEquals("", LostTalesCustomWaypointStyle.normalizeKey(""));
        assertEquals("", LostTalesCustomWaypointStyle.normalizeKey(null));
        assertEquals("",
                LostTalesCustomWaypointStyle.normalizeKey("a=b"));
        assertEquals("",
                LostTalesCustomWaypointStyle.normalizeKey("say \"hi\""));
        assertEquals("north camp",
                LostTalesCustomWaypointStyle.normalizeKey(" North Camp "));
    }

    @Test
    public void onlyPaletteColoursAreAccepted() {
        for (String color : LostTalesCustomWaypointStyle.PALETTE) {
            assertTrue(color,
                    LostTalesCustomWaypointStyle.isKnownColor(color));
        }
        assertFalse(LostTalesCustomWaypointStyle.isKnownColor("chartreuse"));
        assertFalse(LostTalesCustomWaypointStyle.isKnownColor(null));
    }

    @Test
    public void everyPaletteColourIsOneTheMarkerRendererKnows() {
        // A colour the icon renderer cannot parse would silently draw white,
        // so the palette may only offer names it recognises.
        for (String color : LostTalesCustomWaypointStyle.PALETTE) {
            float[] parsed = com.ninuna.losttales.gui.hud.compass.marker
                    .LostTalesCompassMarker.parseColor(color);
            float[] fallback = com.ninuna.losttales.gui.hud.compass.marker
                    .LostTalesCompassMarker.parseColor("definitely-not-a-colour");
            assertTrue(color,
                    "white".equals(color)
                            || parsed[0] != fallback[0]
                            || parsed[1] != fallback[1]
                            || parsed[2] != fallback[2]);
        }
    }
}
