package com.ninuna.losttales.gui.style;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A point moves to the nearest whole display pixel, a sixteenth of a
 * pixel on, in its own units, so pixel art whose texels do not fill
 * whole pixels takes the same pixels wherever it is carried.
 */
public final class LostTalesDisplayPixelsTest {

    private static final float EPSILON = 0.0001F;

    private static final float TIE = (float)LostTalesDisplayPixels.TIE_BREAK;

    @Test
    public void aPointMovesToTheNearestDisplayPixel() {
        // GUI scale 3, no matrix scale: 10.2 GUI pixels is 30.6 display
        // pixels, which lands on 31.
        assertEquals((0.4F + TIE) / 3.0F,
                LostTalesDisplayPixels.snapDelta(10.2F, 1.0F, 0.0F, 3),
                EPSILON);
        // 10.1 is 30.3, which lands on 30.
        assertEquals((-0.3F + TIE) / 3.0F,
                LostTalesDisplayPixels.snapDelta(10.1F, 1.0F, 0.0F, 3),
                EPSILON);
        // A point already on the grid only takes the tie break.
        assertEquals(TIE / 3.0F,
                LostTalesDisplayPixels.snapDelta(10.0F, 1.0F, 0.0F, 3),
                EPSILON);
    }

    /**
     * The headwear's texel edges at GUI scale 3 are seven and a half
     * display pixels apart, so every other one falls on a pixel's middle;
     * past the tie break none does, wherever the head was put.
     */
    @Test
    public void noTexelEdgeFallsOnAPixelsMiddle() {
        for (float at = 0.0F; at < 2.0F; at += 0.05F) {
            float head = at + LostTalesDisplayPixels.snapDelta(at, 1.0F,
                    0.0F, 3);
            for (int edge = 0; edge <= 8; edge++) {
                double display = (head - 2.0F) * 3.0D + edge * 7.5D;
                double fraction = display - Math.floor(display);
                assertTrue("at " + at + ", edge " + edge,
                        Math.abs(fraction - 0.5D) > 0.01D);
            }
        }
    }

    /** The matrix's scale and shift are part of where the point lands. */
    @Test
    public void theMatrixIsReadIntoIt() {
        // A stack drawn at half scale from 0.25: 3 units are 1.75 GUI
        // pixels, 3.5 display pixels at scale 2, landing on 4 — half a
        // display pixel on and the tie break, a unit to the display pixel.
        assertEquals(0.5F + TIE,
                LostTalesDisplayPixels.snapDelta(3.0F, 0.5F, 0.25F, 2),
                EPSILON);
        assertEquals(0.0F,
                LostTalesDisplayPixels.snapDelta(3.0F, 0.0F, 0.25F, 2),
                EPSILON);
    }
}
