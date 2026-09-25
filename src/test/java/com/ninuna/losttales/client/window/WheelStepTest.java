package com.ninuna.losttales.client.window;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * One turn of the wheel is two lines, one with Shift, and each thing it
 * scrolls counts them in its own unit: the history in message lines, a
 * menu in whole rows, a picker in a menu row's height per line.
 */
public final class WheelStepTest {

    @Test
    public void aTurnIsTwoLinesAndOneWithShift() {
        assertEquals(2, WheelStep.lines(120, false));
        assertEquals(-2, WheelStep.lines(-120, false));
        assertEquals(1, WheelStep.lines(120, true));
        assertEquals(-1, WheelStep.lines(-3, true));
        assertEquals(0, WheelStep.lines(0, false));
    }

    @Test
    public void eachThingMovesInItsOwnUnit() {
        // The history: whole message lines.
        assertEquals(2 * WindowStyle.LINE_HEIGHT, WheelStep.pixels(
                WheelStep.lines(120, false), WindowStyle.LINE_HEIGHT));
        assertEquals(-WindowStyle.LINE_HEIGHT, WheelStep.pixels(
                WheelStep.lines(-120, true), WindowStyle.LINE_HEIGHT));
        // A menu: whole rows, never part of one.
        assertEquals(2, WheelStep.menuRows(WheelStep.lines(120, false)));
        assertEquals(1, WheelStep.menuRows(WheelStep.lines(120, true)));
        // A picker: its own row's height per line, whatever a message row
        // measures.
        assertEquals(22, WheelStep.pixels(WheelStep.lines(120, false), 11));
        assertEquals(11, WheelStep.pixels(WheelStep.lines(120, true), 11));
    }
}
