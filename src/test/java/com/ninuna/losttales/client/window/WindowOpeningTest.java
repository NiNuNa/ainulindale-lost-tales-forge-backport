package com.ninuna.losttales.client.window;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Every window's input bar comes up from below and settles exactly in place. */
public final class WindowOpeningTest {
    private static final float EPSILON = 1.0E-4F;

    @Test
    public void theBarsEntranceStartsBelowAndSettlesInPlace() {
        assertTrue(WindowOpening.barOffsetAt(0.0F) > 0.0F);
        assertEquals(0.0F, WindowOpening.barOffsetAt(1.0F), EPSILON);
    }

    @Test
    public void theBarsEntranceIsClamped() {
        assertEquals(WindowOpening.barOffsetAt(0.0F),
                WindowOpening.barOffsetAt(-2.0F), EPSILON);
        assertEquals(0.0F, WindowOpening.barOffsetAt(2.0F), EPSILON);
    }
}
