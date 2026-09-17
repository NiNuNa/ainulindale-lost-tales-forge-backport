package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The column's gaps are what the eye sees: three pixels of clear space
 * between the frame edge and the timestamp, between the timestamp and
 * the separator, and between the separator and the message.
 */
public final class ChatTimestampColumnTest {
    /** Ink width of {@code [00:00]} in the default font: 34 less its
     *  trailing spacing column. */
    private static final int TIMESTAMP_INK = 33;

    @Test
    public void everyGapIsThreePixelsOfClearSpace() {
        ChatTimestampColumn column =
                ChatTimestampColumn.forWidth(TIMESTAMP_INK);
        // The frame edge stands outside the window, so the gap starts at
        // the window's own edge.
        assertEquals(ChatTimestampColumn.EDGE_GAP, column.timestampX());
        // From the last pixel of ink to the separator.
        assertEquals(ChatTimestampColumn.EDGE_GAP,
                column.separatorX()
                        - (column.timestampX() + TIMESTAMP_INK));
        // And from the separator to the first pixel of the message.
        assertEquals(ChatTimestampColumn.EDGE_GAP,
                column.messageX() - (column.separatorX()
                        + ChatTimestampColumn.SEPARATOR_WIDTH));
    }

    /** Without a column the message keeps its own edge gap and nothing else. */
    @Test
    public void aDisabledColumnIsOnlyItsEdgeGap() {
        ChatTimestampColumn column = ChatTimestampColumn.disabled();
        assertEquals(ChatTimestampColumn.EDGE_GAP, column.messageX());
    }
}
