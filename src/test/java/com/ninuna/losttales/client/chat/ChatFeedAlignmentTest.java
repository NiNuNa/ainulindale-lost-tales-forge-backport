package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The closed feed stands its lines against the left edge, the right one
 * or the middle: a row keeps its shape and only moves, by its ink, and
 * its band thins out away from where the lines stand.
 */
public final class ChatFeedAlignmentTest {

    @Test
    public void aConfigValueNamesAnAlignment() {
        assertEquals(ChatFeedAlignment.LEFT, ChatFeedAlignment.of("LEFT"));
        assertEquals(ChatFeedAlignment.RIGHT, ChatFeedAlignment.of(" right "));
        assertEquals(ChatFeedAlignment.CENTRE, ChatFeedAlignment.of("Centre"));
        assertEquals(ChatFeedAlignment.CENTRE, ChatFeedAlignment.of("CENTER"));
        assertEquals(ChatFeedAlignment.LEFT, ChatFeedAlignment.of("middle"));
        assertEquals(ChatFeedAlignment.LEFT, ChatFeedAlignment.of(null));
    }

    @Test
    public void aRowMovesByItsInk() {
        // A row whose ink runs from 0 to 80 in an area 200 wide.
        assertEquals(0.0F, ChatFeedAlignment.LEFT.rowShift(200.0F, 0.0F, 80.0F),
                0.0F);
        assertEquals(120.0F, ChatFeedAlignment.RIGHT.rowShift(200.0F, 0.0F,
                80.0F), 0.0F);
        assertEquals(60.0F, ChatFeedAlignment.CENTRE.rowShift(200.0F, 0.0F,
                80.0F), 0.0F);
        // A wrapped row's indent is not ink: centred, its words are, the
        // 80 pixels from 40 to 120 moved to stand from 60 to 140.
        assertEquals(20.0F, ChatFeedAlignment.CENTRE.rowShift(200.0F, 40.0F,
                120.0F), 0.0F);
        assertEquals(80.0F, ChatFeedAlignment.RIGHT.rowShift(200.0F, 40.0F,
                120.0F), 0.0F);
    }

    @Test
    public void aNewLineEntersFromItsOwnEdge() {
        assertEquals(-14.0F, ChatFeedAlignment.LEFT.slide(-14.0F), 0.0F);
        assertEquals(14.0F, ChatFeedAlignment.RIGHT.slide(-14.0F), 0.0F);
        assertEquals(0.0F, ChatFeedAlignment.CENTRE.slide(-14.0F), 0.0F);
    }

    @Test
    public void theBandThinsOutAwayFromWhereTheLinesStand() {
        float[] left = ChatFeedAlignment.LEFT.bandWeights();
        float[] right = ChatFeedAlignment.RIGHT.bandWeights();
        float[] middle = ChatFeedAlignment.CENTRE.bandWeights();
        assertEquals(1.0F, left[0], 0.0F);
        assertEquals(0.0F, left[left.length - 1], 0.0001F);
        assertEquals(left.length, right.length);
        for (int index = 0; index < left.length; index++) {
            assertEquals(left[index], right[right.length - 1 - index], 0.0F);
        }
        assertEquals(left.length * 2 - 1, middle.length);
        int half = left.length - 1;
        assertEquals(1.0F, middle[half], 0.0F);
        assertEquals(0.0F, middle[0], 0.0001F);
        assertEquals(0.0F, middle[middle.length - 1], 0.0001F);
        for (int index = 0; index < middle.length; index++) {
            assertEquals(middle[index], middle[middle.length - 1 - index], 0.0F);
        }
    }

    @Test
    public void onlyAnEdgeWearsTheMentionBar() {
        assertTrue(ChatFeedAlignment.LEFT.hasMentionBar());
        assertTrue(ChatFeedAlignment.RIGHT.hasMentionBar());
        assertFalse(ChatFeedAlignment.CENTRE.hasMentionBar());
    }
}
