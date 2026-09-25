package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The typing line's gaps are what the eye sees: three pixels of clear
 * space from the controls' ink to the first divider, from each edge of
 * the well to the field, from the second divider to the counter, and from
 * the counter's slot to the controls; two between each divider and the
 * well.
 */
public final class ChatInputLineTest {
    private static final int BUTTONS_RIGHT = 60;
    private static final int CONTROLS_LEFT = 240;
    private static final int COUNTER_WIDTH = 31;

    private static ChatInputLine line() {
        return ChatInputLine.between(BUTTONS_RIGHT, CONTROLS_LEFT,
                COUNTER_WIDTH);
    }

    @Test
    public void theLinesGapsAreThreePixelsSaveTwoBetweenEachDividerAndTheWell() {
        ChatInputLine line = line();
        int gap = ChatInputBar.BAR_GAP;
        int divider = WindowStyle.DIVIDER_WIDTH;
        assertEquals(2, ChatInputLine.WELL_GAP);
        // From the controls' last pixel of ink to the first divider.
        assertEquals(gap, line.leftDividerX - BUTTONS_RIGHT);
        // From that divider to the well, and from the well's edge to the
        // field's first pixel.
        assertEquals(ChatInputLine.WELL_GAP,
                line.wellLeft - (line.leftDividerX + divider));
        assertEquals(gap, line.fieldLeft - line.wellLeft);
        // From a caret standing at the field's end to the well's edge, and
        // from there to the second divider.
        assertEquals(gap, line.wellRight
                - (line.fieldRight + LostTalesUiCaret.WIDTH));
        assertEquals(ChatInputLine.WELL_GAP, line.rightDividerX - line.wellRight);
        // From the second divider to the counter.
        assertEquals(gap, line.counterLeft - (line.rightDividerX + divider));
        // And from the end of the counter's slot to the controls.
        assertEquals(gap, CONTROLS_LEFT - (line.counterLeft + COUNTER_WIDTH));
    }

    @Test
    public void theWellStandsBetweenTheDividersAndHoldsTheField() {
        ChatInputLine line = line();
        assertTrue(line.wellLeft > line.leftDividerX
                + WindowStyle.DIVIDER_WIDTH);
        assertTrue(line.wellRight < line.rightDividerX);
        assertTrue(line.fieldLeft > line.wellLeft);
        assertTrue(line.fieldRight + LostTalesUiCaret.WIDTH
                < line.wellRight);
    }

    /** The leading buttons' box ends where the first divider stands. */
    @Test
    public void theLeadingButtonsEndAtTheFirstDivider() {
        assertEquals(line().leftDividerX,
                ChatInputLine.dividerAfter(BUTTONS_RIGHT));
    }

    /** A wider counter slot moves the second divider, never the first. */
    @Test
    public void onlyTheCounterSlotMovesTheSecondDivider() {
        ChatInputLine narrow = ChatInputLine.between(BUTTONS_RIGHT,
                CONTROLS_LEFT, 20);
        ChatInputLine wide = ChatInputLine.between(BUTTONS_RIGHT,
                CONTROLS_LEFT, 30);
        assertEquals(narrow.leftDividerX, wide.leftDividerX);
        assertEquals(narrow.fieldLeft, wide.fieldLeft);
        assertEquals(10, narrow.rightDividerX - wide.rightDividerX);
    }
}
