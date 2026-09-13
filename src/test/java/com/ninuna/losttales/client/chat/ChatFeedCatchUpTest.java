package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * The closed feed shows what is happening, not what was said before this
 * player arrived. A line printed as history is stamped a whole fade old,
 * and a feed run never carries such a line back onto the clock of a line
 * said since: a run is broken wherever a message arrived a whole fade
 * after the one before it.
 */
public final class ChatFeedCatchUpTest {

    private static final int FADE = LostTalesChatOverlayRenderer.FEED_FADE_TICKS;

    @Test
    public void aRunBreaksWhereAWholeFadeLiesBetweenTwoArrivals() {
        // Newest first: two messages said live, a moment apart, then one
        // printed as history just before them and stamped a whole fade
        // back.
        boolean[] grouped = {true, true, false};
        int[] arrivals = {1000, 990, 990 - FADE};
        assertArrayEquals(new boolean[] {true, false, false},
                ChatWindowLines.heldToArrivals(grouped, arrivals));
    }

    @Test
    public void runsWithinTheFadeAreKept() {
        boolean[] grouped = {true, false, true, false};
        int[] arrivals = {500, 480, 470, 300};
        assertArrayEquals(grouped,
                ChatWindowLines.heldToArrivals(grouped, arrivals));
        // Nothing runs off the ends of either list.
        assertEquals(0, ChatWindowLines.heldToArrivals(null, null).length);
        assertArrayEquals(new boolean[] {true},
                ChatWindowLines.heldToArrivals(new boolean[] {true},
                        new int[0]));
    }

    @Test
    public void theHatchKeepsTheGapTwoGroupsStandApartBy() {
        // The stack's top 40 above the baseline, not moving: the hatch
        // ends one gap above it.
        assertEquals(-40.0F - ChatStackRows.SPACER_HEIGHT,
                LostTalesChatOverlayRenderer.hatchBottom(0.0F, 40.0F), 0.0F);
        // It follows the stack as the stack moves.
        assertEquals(-40.0F - ChatStackRows.SPACER_HEIGHT + 6.0F,
                LostTalesChatOverlayRenderer.hatchBottom(6.0F, 40.0F), 0.0F);
        // Never below the baseline.
        assertEquals(0.0F,
                LostTalesChatOverlayRenderer.hatchBottom(50.0F, 0.0F), 0.0F);
    }
}
