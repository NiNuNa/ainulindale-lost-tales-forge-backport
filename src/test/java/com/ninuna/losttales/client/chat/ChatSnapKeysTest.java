package com.ninuna.losttales.client.chat;

import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Snapping from the keyboard: Alt with an arrow walks a window through
 * the halves, the thirds and the quarters of the screen as the desktop's
 * Windows key does.
 */
public final class ChatSnapKeysTest {

    @Test
    public void onlyTheArrowsAreDirections() {
        assertEquals(ChatSnapKeys.Direction.LEFT,
                ChatSnapKeys.direction(Keyboard.KEY_LEFT));
        assertEquals(ChatSnapKeys.Direction.DOWN,
                ChatSnapKeys.direction(Keyboard.KEY_DOWN));
        assertNull(ChatSnapKeys.direction(Keyboard.KEY_Z));
    }

    /**
     * A side cycles on through a half, two thirds and a third; the other
     * arrow gives the screen back before the window crosses.
     */
    @Test
    public void aSideCyclesThroughItsHalfAndThirds() {
        assertEquals(ChatWindow.ScreenFill.LEFT,
                next(ChatWindow.ScreenFill.NONE, ChatSnapKeys.Direction.LEFT));
        assertEquals(ChatWindow.ScreenFill.LEFT_TWO_THIRDS,
                next(ChatWindow.ScreenFill.LEFT, ChatSnapKeys.Direction.LEFT));
        assertEquals(ChatWindow.ScreenFill.LEFT_THIRD,
                next(ChatWindow.ScreenFill.LEFT_TWO_THIRDS,
                        ChatSnapKeys.Direction.LEFT));
        assertEquals(ChatWindow.ScreenFill.LEFT,
                next(ChatWindow.ScreenFill.LEFT_THIRD,
                        ChatSnapKeys.Direction.LEFT));
        assertEquals(ChatWindow.ScreenFill.NONE,
                next(ChatWindow.ScreenFill.LEFT, ChatSnapKeys.Direction.RIGHT));
        assertEquals(ChatWindow.ScreenFill.RIGHT_TWO_THIRDS,
                next(ChatWindow.ScreenFill.RIGHT,
                        ChatSnapKeys.Direction.RIGHT));
        assertEquals(ChatWindow.ScreenFill.NONE,
                next(ChatWindow.ScreenFill.RIGHT_THIRD,
                        ChatSnapKeys.Direction.LEFT));
        // The whole screen, and a part the player shaped, go to a half.
        assertEquals(ChatWindow.ScreenFill.RIGHT,
                next(ChatWindow.ScreenFill.FULL,
                        ChatSnapKeys.Direction.RIGHT));
        assertEquals(ChatWindow.ScreenFill.LEFT,
                next(ChatWindow.ScreenFill.free(0.1D, 0.0D, 0.4D, 1.0D),
                        ChatSnapKeys.Direction.LEFT));
    }

    /** A half splits into its quarters and they join back into it. */
    @Test
    public void aHalfAndItsQuartersWalkUpAndDown() {
        assertEquals(ChatWindow.ScreenFill.TOP_LEFT,
                next(ChatWindow.ScreenFill.LEFT, ChatSnapKeys.Direction.UP));
        assertEquals(ChatWindow.ScreenFill.LEFT,
                next(ChatWindow.ScreenFill.TOP_LEFT,
                        ChatSnapKeys.Direction.DOWN));
        assertEquals(ChatWindow.ScreenFill.BOTTOM_RIGHT,
                next(ChatWindow.ScreenFill.RIGHT,
                        ChatSnapKeys.Direction.DOWN));
        assertEquals(ChatWindow.ScreenFill.RIGHT,
                next(ChatWindow.ScreenFill.BOTTOM_RIGHT,
                        ChatSnapKeys.Direction.UP));
        assertEquals(ChatWindow.ScreenFill.BOTTOM_LEFT,
                next(ChatWindow.ScreenFill.BOTTOM_LEFT,
                        ChatSnapKeys.Direction.DOWN));
        // A quarter crosses straight to the quarter beside it.
        assertEquals(ChatWindow.ScreenFill.TOP_RIGHT,
                next(ChatWindow.ScreenFill.TOP_LEFT,
                        ChatSnapKeys.Direction.RIGHT));
        // A top quarter, like anything else, fills the screen from up.
        assertEquals(ChatWindow.ScreenFill.FULL,
                next(ChatWindow.ScreenFill.TOP_RIGHT,
                        ChatSnapKeys.Direction.UP));
    }

    @Test
    public void upFillsTheScreenAndDownGivesItBack() {
        assertEquals(ChatWindow.ScreenFill.FULL,
                next(ChatWindow.ScreenFill.NONE, ChatSnapKeys.Direction.UP));
        assertEquals(ChatWindow.ScreenFill.FULL,
                next(ChatWindow.ScreenFill.FULL, ChatSnapKeys.Direction.UP));
        assertEquals(ChatWindow.ScreenFill.NONE,
                next(ChatWindow.ScreenFill.FULL, ChatSnapKeys.Direction.DOWN));
        assertEquals(ChatWindow.ScreenFill.NONE,
                next(ChatWindow.ScreenFill.CENTRE_THIRD,
                        ChatSnapKeys.Direction.DOWN));
        assertEquals(ChatWindow.ScreenFill.NONE,
                next(ChatWindow.ScreenFill.NONE,
                        ChatSnapKeys.Direction.DOWN));
    }

    private static ChatWindow.ScreenFill next(ChatWindow.ScreenFill from,
                                              ChatSnapKeys.Direction to) {
        return ChatSnapKeys.next(from, to);
    }
}
