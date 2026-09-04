package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The row's way of fitting too many tabs: the widest labels are capped
 * to one common width, narrower ones keep theirs, and the cap is the
 * largest that fits — so tabs shrink evenly, like a browser's.
 */
public final class ChatChannelTabBarTest {

    private static final double EPSILON = 1.0E-6D;

    /** The marquee: still, out, rest, back, rest — on the clock alone. */
    @Test
    public void marqueeWaitsThenSlidesOutRestsAndSlidesBack() {
        int overflow = 40;
        double delay = ChatChannelTabBar.MARQUEE_START_DELAY_SECONDS;
        double speed = ChatChannelTabBar.MARQUEE_SPEED_PX_PER_SECOND;
        double pause = ChatChannelTabBar.MARQUEE_END_PAUSE_SECONDS;
        double slide = overflow / speed;
        assertEquals(0.0D, ChatChannelTabBar.marqueeOffset(0.0D, overflow), EPSILON);
        assertEquals(0.0D, ChatChannelTabBar.marqueeOffset(delay * 0.8D, overflow), EPSILON);
        // Half a second in: half a second's worth of sliding.
        assertEquals(0.5D * speed,
                ChatChannelTabBar.marqueeOffset(delay + 0.5D, overflow), EPSILON);
        // At the end of the slide and through the rest: the whole overflow.
        assertEquals(overflow,
                ChatChannelTabBar.marqueeOffset(delay + slide, overflow), EPSILON);
        assertEquals(overflow,
                ChatChannelTabBar.marqueeOffset(delay + slide + pause * 0.5D, overflow),
                EPSILON);
        // Halfway back.
        assertEquals(overflow / 2.0D,
                ChatChannelTabBar.marqueeOffset(delay + slide + pause + slide / 2.0D,
                        overflow), EPSILON);
        // Home, resting, and round again.
        double cycle = 2.0D * (slide + pause);
        assertEquals(0.0D,
                ChatChannelTabBar.marqueeOffset(delay + cycle - pause * 0.5D, overflow),
                EPSILON);
        assertEquals(0.5D * speed,
                ChatChannelTabBar.marqueeOffset(delay + cycle + 0.5D, overflow), EPSILON);
    }

    @Test
    public void marqueeIsStillWhenNothingOverflows() {
        assertEquals(0.0D, ChatChannelTabBar.marqueeOffset(5.0D, 0), EPSILON);
        assertEquals(0.0D, ChatChannelTabBar.marqueeOffset(5.0D, -3), EPSILON);
    }

    @Test
    public void marqueeNeverLeavesTheOverflow() {
        int overflow = 17;
        for (double time = 0.0D; time < 20.0D; time += 0.037D) {
            double offset = ChatChannelTabBar.marqueeOffset(time, overflow);
            assertTrue(offset >= -EPSILON);
            assertTrue(offset <= overflow + EPSILON);
        }
    }

    @Test
    public void widestLabelsAreCappedToOneWidthThatFits() {
        int[] widths = {40, 10, 60, 25};
        ChatChannelTabBar.capLabels(widths, 135);
        // Everything fits whole: nothing changes.
        assertArrayEquals(new int[] {40, 10, 60, 25}, widths);
        widths = new int[] {40, 10, 60, 25};
        ChatChannelTabBar.capLabels(widths, 100);
        // 60 and 40 come down to the same cap; 25 + 10 + 2 * cap <= 100.
        assertArrayEquals(new int[] {32, 10, 32, 25}, widths);
        widths = new int[] {40, 10, 60, 25};
        ChatChannelTabBar.capLabels(widths, 40);
        assertArrayEquals(new int[] {10, 10, 10, 10}, widths);
    }

    @Test
    public void noRoomLeavesNoLabelRatherThanAnOverflow() {
        int[] widths = {40, 10};
        ChatChannelTabBar.capLabels(widths, 0);
        assertArrayEquals(new int[] {0, 0}, widths);
        widths = new int[] {40, 10};
        ChatChannelTabBar.capLabels(widths, -5);
        assertArrayEquals(new int[] {0, 0}, widths);
    }

    /** The tab in front keeps its whole name; the rest give way to it. */
    @Test
    public void theKeptLabelIsNeverShortened() {
        int[] widths = { 60, 40, 30 };
        ChatChannelTabBar.capLabelsAround(widths, 100, 0);
        assertEquals(60, widths[0]);
        assertEquals(40, widths[1] + widths[2]);

        // Too little room even for the kept label: it takes what there is
        // and the others go to nothing.
        widths = new int[] { 60, 40, 30 };
        ChatChannelTabBar.capLabelsAround(widths, 25, 0);
        assertEquals(25, widths[0]);
        assertEquals(0, widths[1]);
        assertEquals(0, widths[2]);

        // The kept label may sit anywhere in the row.
        widths = new int[] { 60, 40, 30 };
        ChatChannelTabBar.capLabelsAround(widths, 80, 2);
        assertEquals(30, widths[2]);
        assertTrue(widths[0] + widths[1] <= 50);

        // No kept label caps every one alike, as before.
        widths = new int[] { 60, 40, 30 };
        int[] expected = { 60, 40, 30 };
        ChatChannelTabBar.capLabelsAround(widths, 100, -1);
        ChatChannelTabBar.capLabels(expected, 100);
        assertArrayEquals(expected, widths);
    }

    /** The run the row shows always holds the tab in front. */
    @Test
    public void theShownRunKeepsTheTabInFront() {
        int[] widths = { 30, 30, 30, 30, 30 };
        // Room for three of the five: the run closes in on the selection
        // from the far end first, so the tab in front never falls out.
        assertArrayEquals(new int[] { 0, 2 },
                shownRun(widths, 100, 1));
        assertArrayEquals(new int[] { 2, 4 },
                shownRun(widths, 100, 4));
        assertArrayEquals(new int[] { 0, 4 },
                shownRun(widths, 1000, 2));
        // Not even one tab fits: the row keeps the selected one anyway.
        assertArrayEquals(new int[] { 3, 3 }, shownRun(widths, 5, 3));
    }

    /**
     * The same shrink the row performs, over bare tab widths: the run
     * closes in on the selection from whichever end is further away.
     */
    private static int[] shownRun(int[] fixed, int room, int selected) {
        int first = 0;
        int last = fixed.length - 1;
        while (first <= last && sum(fixed, first, last) > room) {
            if (last > selected) {
                last--;
            } else if (first < selected) {
                first++;
            } else {
                break;
            }
        }
        return new int[] { first, last };
    }

    private static int sum(int[] widths, int first, int last) {
        int total = 0;
        for (int index = first; index <= last; index++) {
            total += widths[index];
        }
        return total;
    }

    /** Capping over a run leaves the labels outside it alone. */
    @Test
    public void cappingOverARunTouchesOnlyTheRun() {
        int[] widths = { 60, 40, 30, 50 };
        ChatChannelTabBar.capLabelsAround(widths, 40, 0, 1, 2);
        assertEquals(60, widths[0]);
        assertEquals(50, widths[3]);
        assertEquals(40, widths[1]);
        assertEquals(0, widths[2]);
    }
}
