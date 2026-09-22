package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The typing line's gaps are what the eye sees: three pixels of clear
 * space from the indicator's ink to the first divider, from each divider
 * to the field, from the second divider to the counter, and from the
 * counter's slot to the controls.
 */
public final class ChatInputLineTest {
    private static final int INDICATOR_INK_RIGHT = 60;
    private static final int CONTROLS_LEFT = 240;
    private static final int COUNTER_WIDTH = 31;

    private static ChatInputLine line() {
        return ChatInputLine.between(INDICATOR_INK_RIGHT, CONTROLS_LEFT,
                COUNTER_WIDTH);
    }

    @Test
    public void everyGapOnTheLineIsThreePixelsOfClearSpace() {
        ChatInputLine line = line();
        int gap = ChatInputBar.BAR_GAP;
        int divider = LostTalesChatVisualStyle.DIVIDER_WIDTH;
        // From the indicator's last pixel of ink to the first divider.
        assertEquals(gap, line.leftDividerX - INDICATOR_INK_RIGHT);
        // From that divider to the field's first pixel.
        assertEquals(gap, line.fieldLeft - (line.leftDividerX + divider));
        // From a caret standing at the field's end to the second divider.
        assertEquals(gap, line.rightDividerX
                - (line.fieldRight + LostTalesUiCaret.WIDTH));
        // From the second divider to the counter.
        assertEquals(gap, line.counterLeft - (line.rightDividerX + divider));
        // And from the end of the counter's slot to the controls.
        assertEquals(gap, CONTROLS_LEFT - (line.counterLeft + COUNTER_WIDTH));
    }

    @Test
    public void theWellRunsFromDividerToDividerAndHoldsTheField() {
        ChatInputLine line = line();
        assertEquals(line.leftDividerX + LostTalesChatVisualStyle.DIVIDER_WIDTH,
                line.wellLeft);
        assertEquals(line.rightDividerX, line.wellRight);
        assertTrue(line.fieldLeft > line.wellLeft);
        assertTrue(line.fieldRight + LostTalesUiCaret.WIDTH
                < line.wellRight);
    }

    /** The indicator's box ends where the first divider stands. */
    @Test
    public void theIndicatorEndsAtTheFirstDivider() {
        assertEquals(line().leftDividerX,
                ChatInputLine.dividerAfter(INDICATOR_INK_RIGHT));
    }

    /** A wider counter slot moves the second divider, never the first. */
    @Test
    public void onlyTheCounterSlotMovesTheSecondDivider() {
        ChatInputLine narrow = ChatInputLine.between(INDICATOR_INK_RIGHT,
                CONTROLS_LEFT, 20);
        ChatInputLine wide = ChatInputLine.between(INDICATOR_INK_RIGHT,
                CONTROLS_LEFT, 30);
        assertEquals(narrow.leftDividerX, wide.leftDividerX);
        assertEquals(narrow.fieldLeft, wide.fieldLeft);
        assertEquals(10, narrow.rightDividerX - wide.rightDividerX);
    }
}
