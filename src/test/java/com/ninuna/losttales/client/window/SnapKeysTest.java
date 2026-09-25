package com.ninuna.losttales.client.window;

import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Snapping from the keyboard: Alt with an arrow walks a window through
 * the halves, the thirds and the quarters of the screen as the desktop's
 * Windows key does.
 */
public final class SnapKeysTest {

    @Test
    public void onlyTheArrowsAreDirections() {
        assertEquals(SnapKeys.Direction.LEFT,
                SnapKeys.direction(Keyboard.KEY_LEFT));
        assertEquals(SnapKeys.Direction.DOWN,
                SnapKeys.direction(Keyboard.KEY_DOWN));
        assertNull(SnapKeys.direction(Keyboard.KEY_Z));
    }

    /**
     * A side cycles on through a half, two thirds and a third; the other
     * arrow gives the screen back before the window crosses.
     */
    @Test
    public void aSideCyclesThroughItsHalfAndThirds() {
        assertEquals(Window.ScreenFill.LEFT,
                next(Window.ScreenFill.NONE, SnapKeys.Direction.LEFT));
        assertEquals(Window.ScreenFill.LEFT_TWO_THIRDS,
                next(Window.ScreenFill.LEFT, SnapKeys.Direction.LEFT));
        assertEquals(Window.ScreenFill.LEFT_THIRD,
                next(Window.ScreenFill.LEFT_TWO_THIRDS,
                        SnapKeys.Direction.LEFT));
        assertEquals(Window.ScreenFill.LEFT,
                next(Window.ScreenFill.LEFT_THIRD,
                        SnapKeys.Direction.LEFT));
        assertEquals(Window.ScreenFill.NONE,
                next(Window.ScreenFill.LEFT, SnapKeys.Direction.RIGHT));
        assertEquals(Window.ScreenFill.RIGHT_TWO_THIRDS,
                next(Window.ScreenFill.RIGHT,
                        SnapKeys.Direction.RIGHT));
        assertEquals(Window.ScreenFill.NONE,
                next(Window.ScreenFill.RIGHT_THIRD,
                        SnapKeys.Direction.LEFT));
        // The whole screen, and a part the player shaped, go to a half.
        assertEquals(Window.ScreenFill.RIGHT,
                next(Window.ScreenFill.FULL,
                        SnapKeys.Direction.RIGHT));
        assertEquals(Window.ScreenFill.LEFT,
                next(Window.ScreenFill.free(0.1D, 0.0D, 0.4D, 1.0D),
                        SnapKeys.Direction.LEFT));
    }

    /** A half splits into its quarters and they join back into it. */
    @Test
    public void aHalfAndItsQuartersWalkUpAndDown() {
        assertEquals(Window.ScreenFill.TOP_LEFT,
                next(Window.ScreenFill.LEFT, SnapKeys.Direction.UP));
        assertEquals(Window.ScreenFill.LEFT,
                next(Window.ScreenFill.TOP_LEFT,
                        SnapKeys.Direction.DOWN));
        assertEquals(Window.ScreenFill.BOTTOM_RIGHT,
                next(Window.ScreenFill.RIGHT,
                        SnapKeys.Direction.DOWN));
        assertEquals(Window.ScreenFill.RIGHT,
                next(Window.ScreenFill.BOTTOM_RIGHT,
                        SnapKeys.Direction.UP));
        assertEquals(Window.ScreenFill.BOTTOM_LEFT,
                next(Window.ScreenFill.BOTTOM_LEFT,
                        SnapKeys.Direction.DOWN));
        // A quarter crosses straight to the quarter beside it.
        assertEquals(Window.ScreenFill.TOP_RIGHT,
                next(Window.ScreenFill.TOP_LEFT,
                        SnapKeys.Direction.RIGHT));
        // A top quarter, like anything else, fills the screen from up.
        assertEquals(Window.ScreenFill.FULL,
                next(Window.ScreenFill.TOP_RIGHT,
                        SnapKeys.Direction.UP));
    }

    @Test
    public void upFillsTheScreenAndDownGivesItBack() {
        assertEquals(Window.ScreenFill.FULL,
                next(Window.ScreenFill.NONE, SnapKeys.Direction.UP));
        assertEquals(Window.ScreenFill.FULL,
                next(Window.ScreenFill.FULL, SnapKeys.Direction.UP));
        assertEquals(Window.ScreenFill.NONE,
                next(Window.ScreenFill.FULL, SnapKeys.Direction.DOWN));
        assertEquals(Window.ScreenFill.NONE,
                next(Window.ScreenFill.CENTRE_THIRD,
                        SnapKeys.Direction.DOWN));
        assertEquals(Window.ScreenFill.NONE,
                next(Window.ScreenFill.NONE,
                        SnapKeys.Direction.DOWN));
    }

    private static Window.ScreenFill next(Window.ScreenFill from,
                                              SnapKeys.Direction to) {
        return SnapKeys.next(from, to);
    }
}
