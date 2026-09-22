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

    /** A colour off the palette is taken for its nearest entry. */
    @Test
    public void aColourOffThePaletteIsItsNearestEntry() {
        assertEquals("HONEY", LostTalesColors.nearestName(
                LostTalesColors.rgb(LostTalesColors.HONEY)));
        // A gold off the palette is its apricot.
        assertEquals("APRICOT", LostTalesColors.nearestName(0xD8B36A));
        assertEquals("PLUM_BLACK", LostTalesColors.nearestName(0x000000));
    }

    /**
     * The darkest shade of a family, passing over the colour itself and
     * the surface it would vanish into.
     */
    @Test
    public void theDarkestShadeKeepsClearOfTheColourAndTheSurface() {
        int plumBlack = LostTalesColors.rgb(LostTalesColors.PLUM_BLACK);
        assertEquals(LostTalesColors.rgb(LostTalesColors.HARBOR_BLUE),
                LostTalesColors.darkestShade(LostTalesColors.rgb(LostTalesColors.FERN_GREEN),
                        plumBlack));
        assertEquals(LostTalesColors.rgb(LostTalesColors.WINE),
                LostTalesColors.darkestShade(LostTalesColors.rgb(LostTalesColors.CRIMSON),
                        plumBlack));
        assertEquals(LostTalesColors.rgb(LostTalesColors.PLUM_DARK),
                LostTalesColors.darkestShade(LostTalesColors.rgb(LostTalesColors.IVORY),
                        plumBlack));
        // A colour that is its family's darkest stands on the next one up.
        assertEquals(LostTalesColors.rgb(LostTalesColors.SEA_GREEN),
                LostTalesColors.darkestShade(LostTalesColors.rgb(LostTalesColors.HARBOR_BLUE),
                        plumBlack));
        assertEquals(LostTalesColors.rgb(LostTalesColors.SEA_GREEN),
                LostTalesColors.darkestShade(LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN),
                        LostTalesColors.rgb(LostTalesColors.HARBOR_BLUE)));
    }

    private static double luminance(int color) {
        return 0.2126D * ((color >> 16) & 0xFF)
                + 0.7152D * ((color >> 8) & 0xFF)
                + 0.0722D * (color & 0xFF);
    }
}
