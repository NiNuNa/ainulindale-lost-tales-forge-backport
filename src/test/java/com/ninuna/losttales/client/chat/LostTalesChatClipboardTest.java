package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LostTalesChatClipboardTest {
    @Test
    public void findsNewestLineAtVanillaChatOrigin() {
        assertEquals(0, LostTalesChatClipboard.lineIndexAt(
                8, 54, 2, 1.0F, 320, 10,
                0, 0.0F, 10));
    }

    @Test
    public void lineStrideSeparatesNeighbouringLines() {
        // Last row of line 0's band.
        assertEquals(0, LostTalesChatClipboard.lineIndexAt(
                8, 74, 2, 1.0F, 320, 10,
                0, 0.0F, 10));
        // First row of line 1's band; bands connect without a gap.
        assertEquals(1, LostTalesChatClipboard.lineIndexAt(
                8, 76, 2, 1.0F, 320, 10,
                0, 0.0F, 10));
    }

    @Test
    public void removesAnimatedVisualOffsetBeforeHitTesting() {
        assertEquals(0, LostTalesChatClipboard.lineIndexAt(
                8, 40, 2, 1.0F, 320, 10,
                0, 7.0F, 10));
    }

    @Test
    public void includesChatScrollPosition() {
        assertEquals(4, LostTalesChatClipboard.lineIndexAt(
                8, 54, 2, 1.0F, 320, 10,
                4, 0.0F, 20));
    }

    @Test
    public void rejectsCoordinatesOutsideChat() {
        assertEquals(-1, LostTalesChatClipboard.lineIndexAt(
                2, 54, 2, 1.0F, 320, 10,
                0, 0.0F, 10));
        // One row above the topmost visible band.
        assertEquals(-1, LostTalesChatClipboard.lineIndexAt(
                8, 274, 2, 1.0F, 320, 10,
                0, 0.0F, 10));
        assertEquals(9, LostTalesChatClipboard.lineIndexAt(
                8, 272, 2, 1.0F, 320, 10,
                0, 0.0F, 10));
    }
}
