package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The row's way of fitting too many tabs: the tab in front is reserved
 * whole, the others' names and controls are capped to one common room,
 * each tab decides for itself which controls its drawn width holds,
 * and the seams are laid down from the exact running total so a row
 * settling into new widths never steps back and forth.
 */
public final class ChatChannelTabBarTest {

    private static final double EPSILON = 1.0E-6D;
    private static final int CONTROL =
            ChatChannelTabBar.CONTROL_GAP + ChatChannelTabBar.CONTROL_SIZE;

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

    /**
     * A tab behind the one in front gives its controls up in stages as
     * its room shrinks, and its name takes the room they leave: the cog
     * goes the moment the whole name no longer fits beside both, the
     * cross once less than half the name would show beside it.
     */
    @Test
    public void controlsGiveWayInStagesAsTheRoomShrinks() {
        int name = 40;
        // Room for the whole name and both controls: everything shows.
        ChatChannelTabBar.TabControls full =
                ChatChannelTabBar.controlsFor(name, name + 2 * CONTROL, true, false);
        assertTrue(full.cog);
        assertTrue(full.close);
        assertEquals(name, full.labelRoom);
        // One pixel less: the cog goes at once, and the name keeps the
        // whole of what is left beside the cross.
        ChatChannelTabBar.TabControls cut =
                ChatChannelTabBar.controlsFor(name, name + 2 * CONTROL - 1, true, false);
        assertFalse(cut.cog);
        assertTrue(cut.close);
        assertEquals(name + CONTROL - 1, Math.min(name, cut.labelRoom) + CONTROL - 1
                + (cut.labelRoom - name));
        assertEquals(name, cut.labelRoom);
        // The cross stays while at least half the name shows beside it.
        ChatChannelTabBar.TabControls half =
                ChatChannelTabBar.controlsFor(name, name / 2 + CONTROL, true, false);
        assertFalse(half.cog);
        assertTrue(half.close);
        assertEquals(name / 2, half.labelRoom);
        // Less than half: the cross goes too and the name has the room.
        ChatChannelTabBar.TabControls bare =
                ChatChannelTabBar.controlsFor(name, name / 2 + CONTROL - 1, true, false);
        assertFalse(bare.cog);
        assertFalse(bare.close);
        assertEquals(name / 2 + CONTROL - 1, bare.labelRoom);
        // No room at all: an icon alone.
        ChatChannelTabBar.TabControls none =
                ChatChannelTabBar.controlsFor(name, 0, true, false);
        assertFalse(none.cog);
        assertFalse(none.close);
        assertEquals(0, none.labelRoom);
        assertEquals(0, ChatChannelTabBar.controlsFor(name, -7, true, false).labelRoom);
        // The name is never given more than it is wide.
        assertEquals(name, ChatChannelTabBar.controlsFor(name, 500, true, false).labelRoom);
    }

    /** Where no cross is offered, the cog is the only control to give up. */
    @Test
    public void withoutACrossOnlyTheCogGivesWay() {
        int name = 30;
        ChatChannelTabBar.TabControls full =
                ChatChannelTabBar.controlsFor(name, name + CONTROL, false, false);
        assertTrue(full.cog);
        assertFalse(full.close);
        assertEquals(name, full.labelRoom);
        ChatChannelTabBar.TabControls cut =
                ChatChannelTabBar.controlsFor(name, name + CONTROL - 1, false, false);
        assertFalse(cut.cog);
        assertFalse(cut.close);
        assertEquals(name, cut.labelRoom);
        assertEquals(12, ChatChannelTabBar.controlsFor(name, 12, false, false).labelRoom);
    }

    /** The tab in front keeps both controls; its name gives way before they do. */
    @Test
    public void theTabInFrontKeepsItsControls() {
        int name = 40;
        ChatChannelTabBar.TabControls settled =
                ChatChannelTabBar.controlsFor(name, name + 2 * CONTROL, true, true);
        assertTrue(settled.cog);
        assertTrue(settled.close);
        assertEquals(name, settled.labelRoom);
        // Drawn narrower than it will settle: the controls stand, the
        // name is cut.
        ChatChannelTabBar.TabControls narrow =
                ChatChannelTabBar.controlsFor(name, 2 * CONTROL + 15, true, true);
        assertTrue(narrow.cog);
        assertTrue(narrow.close);
        assertEquals(15, narrow.labelRoom);
        assertEquals(0, ChatChannelTabBar.controlsFor(name, 5, true, true).labelRoom);
        ChatChannelTabBar.TabControls unclosable =
                ChatChannelTabBar.controlsFor(name, name + CONTROL, false, true);
        assertTrue(unclosable.cog);
        assertFalse(unclosable.close);
        assertEquals(name, unclosable.labelRoom);
    }

    /**
     * A row is asked for the widest tab whole and every other at its
     * icon: whichever tab comes to the front must fit with its whole
     * name and both controls.
     */
    @Test
    public void aRowIsReservedTheWidestTabWholeAndTheOthersIcons() {
        int[] natural = { 60, 90, 45 };
        int[] minimum = { 25, 25, 22 };
        assertEquals(90 + 25 + 22, ChatChannelTabBar.reservedRowWidth(natural, minimum));
        assertEquals(60, ChatChannelTabBar.reservedRowWidth(
                new int[] { 60 }, new int[] { 25 }));
        assertEquals(0, ChatChannelTabBar.reservedRowWidth(new int[0], new int[0]));
        // The first of two equally wide tabs is the one reserved; the
        // answer is the same either way.
        assertEquals(50 + 20, ChatChannelTabBar.reservedRowWidth(
                new int[] { 50, 50 }, new int[] { 20, 20 }));
    }

    /**
     * Seams laid on display pixels from the exact running total move
     * one way while the widths exchange room on one curve: what keeps
     * a row from stepping back and forth as it settles after the
     * selection moves. Rounding each width apart made every seam past
     * the changed tabs wobble by a pixel or two.
     */
    @Test
    public void seamsMoveOneWayWhileWidthsExchangeRoom() {
        double step = 1.0D / 3.0D;
        // The selection moves from the first tab to the second: the old
        // front tab gives up its room, the new one takes it, and the
        // others are capped afresh — 213 pixels before and after.
        double[] widths = { 58.0D, 31.0D, 31.0D, 31.0D, 31.0D, 31.0D };
        double[] targets = { 31.0D, 58.0D, 30.0D, 30.0D, 31.0D, 33.0D };
        double[] previous = ChatChannelTabBar.placeSeams(widths, 1.0D, step, 9.0D);
        int[] direction = new int[previous.length];
        for (int frame = 0; frame < 120; frame++) {
            for (int index = 0; index < widths.length; index++) {
                widths[index] = LostTalesChatMotion.approach(widths[index],
                        targets[index], 1.0D / 144.0D, 0.10D);
            }
            double[] seams = ChatChannelTabBar.placeSeams(widths, 1.0D, step, 9.0D);
            for (int index = 0; index < seams.length; index++) {
                double delta = seams[index] - previous[index];
                if (Math.abs(delta) > EPSILON) {
                    int sign = delta > 0.0D ? 1 : -1;
                    assertTrue("seam " + index + " turned back at frame " + frame,
                            direction[index] == 0 || direction[index] == sign);
                    direction[index] = sign;
                }
                // Every seam is on a display pixel.
                assertEquals(seams[index], Math.round(seams[index] / step) * step,
                        EPSILON);
            }
            // The row's end stays put: the widths only exchange room.
            assertEquals(previous[previous.length - 1], seams[seams.length - 1],
                    EPSILON);
            previous = seams;
        }
    }

    /** Two tabs of one exact width are drawn at most a display pixel apart. */
    @Test
    public void equalTabsDifferByAtMostADisplayPixel() {
        double step = 1.0D / 3.0D;
        for (double width = 20.0D; width < 24.0D; width += 0.01D) {
            double[] seams = ChatChannelTabBar.placeSeams(
                    new double[] { width, width, width }, 1.0D, step, 9.0D);
            double first = seams[1] - seams[0];
            double second = seams[2] - seams[1];
            double third = seams[3] - seams[2];
            assertTrue(Math.abs(first - second) <= step + EPSILON);
            assertTrue(Math.abs(second - third) <= step + EPSILON);
            assertTrue(Math.abs(first - third) <= step + EPSILON);
        }
        // Whole widths at a whole step land exactly.
        assertArrayEquals(new double[] { 9.0D, 40.0D, 71.0D },
                ChatChannelTabBar.placeSeams(new double[] { 30.0D, 30.0D }, 1.0D, 1.0D, 9.0D),
                EPSILON);
    }
}
