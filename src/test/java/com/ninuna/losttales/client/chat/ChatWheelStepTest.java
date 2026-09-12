package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * One turn of the wheel is two lines, one with Shift, and each thing it
 * scrolls counts them in its own unit: the history in message lines, a
 * menu in whole rows, a picker in a menu row's height per line.
 */
public final class ChatWheelStepTest {

    @Test
    public void aTurnIsTwoLinesAndOneWithShift() {
        assertEquals(2, ChatWheelStep.lines(120, false));
        assertEquals(-2, ChatWheelStep.lines(-120, false));
        assertEquals(1, ChatWheelStep.lines(120, true));
        assertEquals(-1, ChatWheelStep.lines(-3, true));
        assertEquals(0, ChatWheelStep.lines(0, false));
    }

    @Test
    public void eachThingMovesInItsOwnUnit() {
        // The history: whole message lines.
        assertEquals(2 * LostTalesChatOverlayRenderer.LINE_HEIGHT,
                ChatWheelStep.historyPixels(ChatWheelStep.lines(120, false)));
        assertEquals(-LostTalesChatOverlayRenderer.LINE_HEIGHT,
                ChatWheelStep.historyPixels(ChatWheelStep.lines(-120, true)));
        // A menu: whole rows, never part of one.
        assertEquals(2, ChatWheelStep.menuRows(ChatWheelStep.lines(120, false)));
        assertEquals(1, ChatWheelStep.menuRows(ChatWheelStep.lines(120, true)));
        // A picker: a menu row's height per line, whatever a message row
        // measures.
        assertEquals(ChatPopupMenu.ROW_HEIGHT, ChatWheelStep.PICKER_LINE_PIXELS);
        assertEquals(22, ChatWheelStep.pickerPixels(ChatWheelStep.lines(120, false)));
        assertEquals(11, ChatWheelStep.pickerPixels(ChatWheelStep.lines(120, true)));
    }
}
