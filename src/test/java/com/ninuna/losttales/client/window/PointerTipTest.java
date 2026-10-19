package com.ninuna.losttales.client.window;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Where the one tip stands: beside the pointer, above it, inside the screen (LOOK-14). */
public class PointerTipTest {

    @Test
    public void aTipStandsRightOfThePointerAndAboveIt() {
        assertEquals(108, PointerTip.left(40, 100, 400));
        assertEquals(100 - PointerTip.ABOVE - 12, PointerTip.top(12, 100));
    }

    @Test
    public void aTipNearTheRightEdgeIsHeldInsideTheScreen() {
        assertEquals(400 - 60 - PointerTip.EDGE, PointerTip.left(60, 380, 400));
    }

    @Test
    public void aTipNearTheLeftEdgeIsHeldInsideTheScreen() {
        assertEquals(PointerTip.EDGE, PointerTip.left(500, 10, 400));
    }

    @Test
    public void aTipWithNoRoomAboveGoesBelowThePointer() {
        assertEquals(5 + PointerTip.BELOW, PointerTip.top(12, 5));
    }
}
