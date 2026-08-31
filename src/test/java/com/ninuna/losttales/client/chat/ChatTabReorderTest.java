package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The rule a dragged run changes places by: a third of the way onto a
 * neighbour takes that neighbour's place, from either side, with the
 * boundary just crossed held to its crossing so a swap cannot hand
 * itself straight back. Three tabs of thirty pixels with one-pixel
 * seams stand in for the row: rest positions 100, 131 and 162, so each
 * boundary's lines fall a third — ten pixels — in from either side.
 */
public final class ChatTabReorderTest {

    private static final int[] REST = {100, 131, 162};
    private static final int[] WIDTHS = {30, 30, 30};
    private static final int GAP = 1;

    private static int slot(int left, int current,
                            ChatChannelTabBar.ReorderLatch latch) {
        return ChatChannelTabBar.reorderSlot(left, current, REST, WIDTHS,
                GAP, latch);
    }

    @Test
    public void aThirdOfTheWayOntoTheNeighbourTakesItsPlace() {
        // Rightward from rest at 100: the line lies a third into the
        // neighbour, plus the guard's lean — twelve pixels of travel.
        assertEquals(0, slot(111, 0, new ChatChannelTabBar.ReorderLatch()));
        assertEquals(1, slot(112, 0, new ChatChannelTabBar.ReorderLatch()));
        // Leftward from rest at 131: the same third back over the tab
        // now standing on 100.
        assertEquals(1, slot(119, 1, new ChatChannelTabBar.ReorderLatch()));
        assertEquals(0, slot(118, 1, new ChatChannelTabBar.ReorderLatch()));
    }

    @Test
    public void aFreshSwapHoldsUntilARealRetreat() {
        ChatChannelTabBar.ReorderLatch latch =
                new ChatChannelTabBar.ReorderLatch();
        assertEquals(1, slot(112, 0, latch));
        // The run now stands well past the leftward line of the place
        // it just won; read cold that would swap it straight back. The
        // latch holds the swap where the hand is.
        assertEquals(1, slot(112, 1, latch));
        assertEquals(1, slot(109, 1, latch));
        // A real retreat behind the crossing undoes it.
        assertEquals(0, slot(107, 1, latch));
    }

    @Test
    public void slidingOnPastTheFarLineDoesNotFlapAroundIt() {
        ChatChannelTabBar.ReorderLatch latch =
                new ChatChannelTabBar.ReorderLatch();
        assertEquals(1, slot(112, 0, latch));
        // Sliding on across the far line at 119 does not yet arm the
        // way back, so the tremors of a slow hand around that line
        // move nothing — this is exactly where a swap once rocked back
        // and forth as the hand breathed.
        assertEquals(1, slot(121, 1, latch));
        assertEquals(1, slot(118, 1, latch));
        assertEquals(1, slot(122, 1, latch));
        // A clear step past the line arms it; from there giving the
        // place up is the line's own third-in reach again.
        assertEquals(1, slot(123, 1, latch));
        assertEquals(1, slot(119, 1, latch));
        assertEquals(0, slot(118, 1, latch));
    }

    @Test
    public void aFastDragCrossesSeveralBoundariesAtOnce() {
        assertEquals(2, slot(170, 0, new ChatChannelTabBar.ReorderLatch()));
        assertEquals(0, slot(101, 2, new ChatChannelTabBar.ReorderLatch()));
    }

    @Test
    public void aLeftwardSwapDoesNotBounceBackEither() {
        ChatChannelTabBar.ReorderLatch latch =
                new ChatChannelTabBar.ReorderLatch();
        // Between the middle boundary's two lines — past the leftward
        // one at 150, not yet back over the rightward one at 143 — the
        // swap left lands and must stand, though the run still leans
        // past the line that would re-enter.
        assertEquals(1, slot(147, 2, latch));
        assertEquals(1, slot(147, 1, latch));
        // Even resting on the re-entry line moves nothing until the
        // retreat has carried a clear step past it and armed it.
        assertEquals(1, slot(143, 1, latch));
        assertEquals(1, slot(139, 1, latch));
        // Armed, the line answers as any other: pushing back over it
        // re-enters at the usual third.
        assertEquals(2, slot(143, 1, latch));
    }

    /**
     * The regression the latch exists for: a slow slide with a small
     * tremor in the hand, across the whole row and back. The place may
     * only ever move the way the slide is going — one rock backwards
     * anywhere is the flapping this guards against.
     */
    @Test
    public void aSlowJitteringSweepNeverRocksBack() {
        ChatChannelTabBar.ReorderLatch latch =
                new ChatChannelTabBar.ReorderLatch();
        int current = 0;
        for (int left = 100; left <= 190; left++) {
            int forward = slot(left, current, latch);
            assertTrue("rocked back at " + left, forward >= current);
            current = forward;
            assertEquals("trembled at " + left, current,
                    slot(left - 2, current, latch));
        }
        assertEquals(3, current);
        for (int left = 190; left >= 100; left--) {
            int back = slot(left, current, latch);
            assertTrue("rocked forward at " + left, back <= current);
            current = back;
            assertEquals("trembled at " + left, current,
                    slot(left + 2, current, latch));
        }
        assertEquals(0, current);
    }

    @Test
    public void aRowWithNothingElseHasOnePlace() {
        assertEquals(0, ChatChannelTabBar.reorderSlot(500, 0, new int[0],
                new int[0], GAP, new ChatChannelTabBar.ReorderLatch()));
    }
}
