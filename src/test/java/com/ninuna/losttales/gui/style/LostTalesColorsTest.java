package com.ninuna.losttales.gui.style;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The palette's ramps: a shade lighter is the next step up a colour's own
 * ramp, and every entry stands on one of the six.
 */
public final class LostTalesColorsTest {

    @Test
    public void aShadeLighterIsTheNextStepUpItsOwnRamp() {
        assertEquals("SALMON", LostTalesColors.lighterStep("ORCHID"));
        assertEquals("ORCHID", LostTalesColors.lighterStep("mulberry"));
        assertEquals("PLUM_DARK", LostTalesColors.lighterStep("PLUM_BLACK"));
        assertEquals("HONEY", LostTalesColors.lighterStep(" apricot "));
        assertEquals("CRIMSON", LostTalesColors.lighterStep("WINE"));
        assertEquals("INDIGO", LostTalesColors.lighterStep("DUSK_VIOLET"));
    }

    @Test
    public void aRampsLightestHasNoneAndNeitherHasANonColour() {
        String[] lightest = {"SEAFOAM", "IVORY", "MEADOW_GREEN", "PARCHMENT",
                "SALMON", "HONEY"};
        for (String name : lightest) {
            assertNull(name, LostTalesColors.lighterStep(name));
        }
        assertNull(LostTalesColors.lighterStep("NOT_A_COLOUR"));
        assertNull(LostTalesColors.lighterStep(null));
    }

    /**
     * Every step up a ramp is lighter, and only the six ramps' lightest
     * have no step: an entry left off every ramp would have none either.
     */
    @Test
    public void everyStepIsLighterAndEveryEntryStandsOnARamp() {
        int withoutStep = 0;
        for (String name : LostTalesColors.paletteNames()) {
            String lighter = LostTalesColors.lighterStep(name);
            if (lighter == null) {
                withoutStep++;
                continue;
            }
            assertTrue(name + " -> " + lighter,
                    luminance(LostTalesColors.paletteColor(lighter, 0))
                            > luminance(LostTalesColors.paletteColor(name, 0)));
        }
        assertEquals(6, withoutStep);
    }

    private static double luminance(int color) {
        return 0.2126D * ((color >> 16) & 0xFF)
                + 0.7152D * ((color >> 8) & 0xFF)
                + 0.0722D * (color & 0xFF);
    }
}
