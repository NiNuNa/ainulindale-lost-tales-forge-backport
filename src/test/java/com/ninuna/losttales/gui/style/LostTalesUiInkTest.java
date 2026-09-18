package com.ninuna.losttales.gui.style;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The one centring rule: something that can stand in the middle of its
 * box does; something an odd number of pixels short of it counts its
 * shadow, a pixel down and right, as part of its shape, which stands its
 * ink half a pixel up or left.
 */
public final class LostTalesUiInkTest {

    @Test
    public void anEvenRemainderStandsInTheMiddle() {
        assertEquals(2, LostTalesUiInk.centredStart(11, 7));
        assertEquals(0, LostTalesUiInk.centredStart(8, 8));
    }

    @Test
    public void anOddRemainderStandsThePixelUpOrLeft() {
        // Seven-row capitals in a twelve-row box: the caps and their
        // shadow are eight rows, which centre exactly two rows down.
        assertEquals(2, LostTalesUiInk.centredStart(12, 7));
        assertEquals(0, LostTalesUiInk.centredStart(8, 7));
    }

    @Test
    public void inkWiderThanItsBoxKeepsTheSameSide() {
        assertEquals(-1, LostTalesUiInk.centredStart(8, 10));
        // An odd overhang: two pixels out on the left, one on the right.
        assertEquals(-2, LostTalesUiInk.centredStart(8, 11));
    }

    @Test
    public void aScaledSpaceKeepsTheSameSideOnTheDisplaysGrid() {
        // Two display pixels a unit: a remainder of three display pixels
        // leaves the ink one display pixel from the start, not one and a
        // half.
        assertEquals(0.5F, LostTalesUiInk.centredStart(5.0F, 3.5F, 2.0F),
                0.0001F);
        // An even remainder stands in the middle.
        assertEquals(1.0F, LostTalesUiInk.centredStart(5.0F, 3.0F, 2.0F),
                0.0001F);
    }
}
