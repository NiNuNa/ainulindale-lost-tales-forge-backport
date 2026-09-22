package com.ninuna.losttales.gui.style;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The one caret blinks on one rhythm: lit for as long as it is out,
 * counted from the last key or caret move, so it stands lit while
 * somebody types.
 */
public final class LostTalesUiCaretTest {
    private static final long MILLIS = 1000000L;

    @Test
    public void itIsLitThenOutForAsLong() {
        long start = 5000L * MILLIS;
        assertTrue(LostTalesUiCaret.isLit(start, start));
        assertTrue(LostTalesUiCaret.isLit(start,
                start + (LostTalesUiCaret.LIT_MILLIS - 1) * MILLIS));
        assertFalse(LostTalesUiCaret.isLit(start,
                start + LostTalesUiCaret.LIT_MILLIS * MILLIS));
        assertFalse(LostTalesUiCaret.isLit(start,
                start + (2 * LostTalesUiCaret.LIT_MILLIS - 1) * MILLIS));
        assertTrue(LostTalesUiCaret.isLit(start,
                start + 2 * LostTalesUiCaret.LIT_MILLIS * MILLIS));
    }

    @Test
    public void aKeyLightsItAtOnce() {
        long now = 9000L * MILLIS;
        // Out half a turn after the last move, lit again from a new one.
        assertFalse(LostTalesUiCaret.isLit(now - 700L * MILLIS, now));
        assertTrue(LostTalesUiCaret.isLit(now, now));
        // A clock read a hair before the move still reads lit.
        assertTrue(LostTalesUiCaret.isLit(now, now - MILLIS));
    }

    @Test
    public void itIsTheChatBarsCaret() {
        assertEquals(1, LostTalesUiCaret.WIDTH);
        assertEquals(10, LostTalesUiCaret.HEIGHT);
        assertEquals(530L, LostTalesUiCaret.LIT_MILLIS);
        assertEquals(4, LostTalesUiCaret.topFor(5));
    }
}
