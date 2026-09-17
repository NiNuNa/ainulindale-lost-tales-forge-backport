package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The row's way of fitting too many tabs: every tab is one width, the
 * default while the row holds them all at it and a narrower one they
 * all share once it does not; each tab decides for itself which
 * controls its drawn width holds, the tab in front keeping its cross
 * at the cost of its icon; and the seams are laid down from the exact
 * running total so a row settling into new widths never steps back
 * and forth.
 */
public final class ChatChannelTabBarTest {

    private static final double EPSILON = 1.0E-6D;
    private static final int CONTROL =
            ChatChannelTabBar.CONTROL_GAP + ChatChannelTabBar.CONTROL_SIZE;

    /**
     * Every control of the strip answers on the box it is drawn in and
     * nowhere else: the search button on its frame, an end control on
     * the nine-pixel square round its glyph, a tab's cog or cross on its
     * square in the tab. The rows of the band above and below a box
     * answer nothing.
     */
    @Test
    public void everyControlAnswersOnItsOwnBox() {
        int rowBottom = 100;
        int rowTop = ChatChannelTabBar.rowTop(rowBottom);
        ChatHitBox search = ChatChannelTabBar.searchBox(40, rowBottom);
        assertEquals(search.width, search.height, EPSILON);
        // The strip begins two pixels left of the row, on the window's
        // own edge, its one-pixel frame standing just outside it; the
        // button stands three clear pixels inside the frame, and the
        // first tab three past the button.
        int stripInset = 2;
        assertEquals(40 - stripInset + 3, search.left, EPSILON);
        assertEquals(search.right() + 3,
                40 + ChatChannelTabBar.tabRunLeftInset() - stripInset, EPSILON);
        assertEquals(ChatChannelTabBar.centredInStrip(rowBottom,
                (int)search.height), search.top, EPSILON);
        assertTrue(search.top > rowTop);
        assertTrue(search.bottom() < rowBottom);
        double middleX = search.left + search.width / 2.0D;
        assertTrue(search.contains(middleX, search.top));
        assertTrue(search.contains(middleX, search.bottom() - 1));
        assertFalse(search.contains(middleX, search.top - 1));
        assertFalse(search.contains(middleX, search.bottom()));
        assertFalse(search.contains(middleX, rowBottom - 1));
        assertFalse(search.contains(search.left - 1, search.top));

        ChatHitBox glyph = ChatChannelTabBar.endControlBox(70, 5, 5, rowBottom);
        assertEquals(ChatChannelTabBar.END_CONTROL_SIZE, glyph.width, EPSILON);
        assertEquals(ChatChannelTabBar.END_CONTROL_SIZE, glyph.height, EPSILON);
        assertEquals(68, glyph.left, EPSILON);
        assertEquals(ChatChannelTabBar.centredInStrip(rowBottom,
                ChatChannelTabBar.END_CONTROL_SIZE), glyph.top, EPSILON);
        assertTrue(glyph.contains(72, glyph.top + 4));
        assertFalse(glyph.contains(72, rowBottom - 1));
        assertFalse(glyph.contains(72, rowTop));
        // The ink itself is what the control is drawn with.
        ChatHitBox ink = ChatChannelTabBar.endControlInk(70, 5, 5, rowBottom);
        assertEquals(70, ink.left, EPSILON);
        assertEquals(glyph.top + 2, ink.top, EPSILON);

        int liftedTop = ChatChannelTabBar.tabTop(rowBottom, true);
        assertEquals(ChatChannelTabBar.tabTop(rowBottom, false)
                - ChatChannelTabBar.LIFT, liftedTop);
        ChatHitBox control = ChatChannelTabBar.tabControlBox(50, liftedTop);
        assertEquals(ChatChannelTabBar.CONTROL_SIZE, control.width, EPSILON);
        assertEquals(ChatChannelTabBar.CONTROL_SIZE, control.height, EPSILON);
        assertTrue(control.top >= liftedTop);
        assertTrue(control.bottom() <= liftedTop + ChatChannelTabBar.HEIGHT);
        // A resting tab's control stands the lift lower than the selected tab's.
        assertEquals(control.top + ChatChannelTabBar.LIFT,
                ChatChannelTabBar.tabControlBox(50,
                        ChatChannelTabBar.tabTop(rowBottom, false)).top,
                EPSILON);
    }

    /**
     * The strip's band, which moves the window, is the row and the tool
     * strip under its rule together, down to the window's top rule; the
     * tabs and controls answer in the row alone.
     */
    @Test
    public void theToolStripIsPartOfTheStripsBand() {
        ChatChannelTabBar.Row row = new ChatChannelTabBar.Row();
        row.rowBottom = 100;
        int rowTop = ChatChannelTabBar.rowTop(100);
        int toolBottom = 100 + ChatWindowPlacement.TOOL_STRIP_HEIGHT;
        assertEquals(17, ChatWindowPlacement.TOOL_STRIP_HEIGHT);
        assertTrue(ChatChannelTabBar.inStripBand(row, rowTop));
        assertFalse(ChatChannelTabBar.inStripBand(row, rowTop - 1));
        assertTrue(ChatChannelTabBar.inRowBand(row, 99));
        assertFalse(ChatChannelTabBar.inRowBand(row, 100));
        assertTrue(ChatChannelTabBar.inStripBand(row, 100));
        assertTrue(ChatChannelTabBar.inStripBand(row, toolBottom - 1));
        assertFalse(ChatChannelTabBar.inStripBand(row, toolBottom));
    }

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

    /**
     * Every tab of a run is one width: the default while the row holds
     * them all at it, else the widest they can share once the seams
     * between them are paid; never less than nothing.
     */
    @Test
    public void everyTabIsOneWidth() {
        int gap = ChatChannelTabBar.TAB_GAP;
        int fallback = ChatChannelTabBar.DEFAULT_TAB_WIDTH;
        assertEquals(fallback, ChatChannelTabBar.uniformTabWidth(1000, 3),
                EPSILON);
        assertEquals(fallback, ChatChannelTabBar.uniformTabWidth(
                fallback * 3 + gap * 2, 3), EPSILON);
        assertEquals(fallback - 1, ChatChannelTabBar.uniformTabWidth(
                fallback * 3 + gap * 2 - 3, 3), EPSILON);
        // Fractions of a pixel are kept: a window's edge dragged a third
        // of a pixel narrows every tab by a ninth of one.
        assertEquals((200.0D - 2 * gap) / 3,
                ChatChannelTabBar.uniformTabWidth(200, 3), EPSILON);
        assertEquals((200.0D - 2 * gap) / 3 - 1.0D / 9.0D,
                ChatChannelTabBar.uniformTabWidth(200 - 1.0D / 3.0D, 3),
                EPSILON);
        assertEquals(0, ChatChannelTabBar.uniformTabWidth(0, 3), EPSILON);
        assertEquals(0, ChatChannelTabBar.uniformTabWidth(50, 0), EPSILON);
        assertEquals(0, ChatChannelTabBar.uniformTabWidth(-5, 2), EPSILON);
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
                ChatChannelTabBar.controlsFor(name, name + 2 * CONTROL, true, false, 0);
        assertTrue(full.cog);
        assertTrue(full.close);
        assertEquals(name, full.labelRoom);
        // One pixel less: the cog goes at once, and the name keeps the
        // whole of what is left beside the cross.
        ChatChannelTabBar.TabControls cut =
                ChatChannelTabBar.controlsFor(name, name + 2 * CONTROL - 1, true, false, 0);
        assertFalse(cut.cog);
        assertTrue(cut.close);
        assertEquals(name + CONTROL - 1, Math.min(name, cut.labelRoom) + CONTROL - 1
                + (cut.labelRoom - name));
        assertEquals(name, cut.labelRoom);
        // The cross stays while at least half the name shows beside it.
        ChatChannelTabBar.TabControls half =
                ChatChannelTabBar.controlsFor(name, name / 2 + CONTROL, true, false, 0);
        assertFalse(half.cog);
        assertTrue(half.close);
        assertEquals(name / 2, half.labelRoom);
        // Less than half: the cross goes too and the name has the room.
        ChatChannelTabBar.TabControls bare =
                ChatChannelTabBar.controlsFor(name, name / 2 + CONTROL - 1, true, false, 0);
        assertFalse(bare.cog);
        assertFalse(bare.close);
        assertEquals(name / 2 + CONTROL - 1, bare.labelRoom);
        // No room at all: an icon alone.
        ChatChannelTabBar.TabControls none =
                ChatChannelTabBar.controlsFor(name, 0, true, false, 0);
        assertFalse(none.cog);
        assertFalse(none.close);
        assertEquals(0, none.labelRoom);
        assertEquals(0, ChatChannelTabBar.controlsFor(name, -7, true, false, 0).labelRoom);
        // The name is never given more than it is wide.
        assertEquals(name, ChatChannelTabBar.controlsFor(name, 500, true, false, 0).labelRoom);
    }

    /** Where no cross is offered, the cog is the only control to give up. */
    @Test
    public void withoutACrossOnlyTheCogGivesWay() {
        int name = 30;
        ChatChannelTabBar.TabControls full =
                ChatChannelTabBar.controlsFor(name, name + CONTROL, false, false, 0);
        assertTrue(full.cog);
        assertFalse(full.close);
        assertEquals(name, full.labelRoom);
        ChatChannelTabBar.TabControls cut =
                ChatChannelTabBar.controlsFor(name, name + CONTROL - 1, false, false, 0);
        assertFalse(cut.cog);
        assertFalse(cut.close);
        assertEquals(name, cut.labelRoom);
        assertEquals(12, ChatChannelTabBar.controlsFor(name, 12, false, false, 0).labelRoom);
    }

    /**
     * The tab in front never loses its cross: it gives up its cog like
     * any tab, and where another tab would give up the cross it keeps
     * it beside its icon and lets the name shrink between them to
     * nothing; the icon goes last, only when the room past it can no
     * longer hold the cross, which then stands alone.
     */
    @Test
    public void theTabInFrontKeepsItsCrossAndGivesUpItsIconLast() {
        int name = 40;
        int icon = 13;
        ChatChannelTabBar.TabControls full = ChatChannelTabBar.controlsFor(
                name, name + 2 * CONTROL, true, true, icon);
        assertTrue(full.cog);
        assertTrue(full.close);
        assertTrue(full.icon);
        assertEquals(name, full.labelRoom);
        // One pixel less: the cog goes, the cross and the icon stay, as
        // on any tab.
        ChatChannelTabBar.TabControls cut = ChatChannelTabBar.controlsFor(
                name, name + 2 * CONTROL - 1, true, true, icon);
        assertFalse(cut.cog);
        assertTrue(cut.close);
        assertTrue(cut.icon);
        assertEquals(name, cut.labelRoom);
        // Less than half the name beside the cross: another tab would
        // drop the cross; the tab in front keeps both the cross and the
        // icon, and the name has what stands between them.
        ChatChannelTabBar.TabControls narrow = ChatChannelTabBar.controlsFor(
                name, name / 2 + CONTROL - 1, true, true, icon);
        assertFalse(narrow.cog);
        assertTrue(narrow.close);
        assertTrue(narrow.icon);
        assertEquals(name / 2 - 1, narrow.labelRoom);
        // Exactly the cross's room past the icon: the two stand together
        // and the name is gone; the cross keeps its place at the right.
        ChatChannelTabBar.TabControls pair = ChatChannelTabBar.controlsFor(
                name, CONTROL, true, true, icon);
        assertTrue(pair.close);
        assertTrue(pair.icon);
        assertEquals(0, pair.labelRoom);
        int tabWidth = ChatChannelTabBar.PADDING_X * 2 + 10;
        assertEquals(100 + tabWidth - ChatChannelTabBar.PADDING_X
                - ChatChannelTabBar.CONTROL_SIZE,
                ChatChannelTabBar.closeLeft(pair, 100, tabWidth));
        // One pixel less: the icon gives its room to the cross, which
        // stands alone, centred where a tab behind stands its icon.
        ChatChannelTabBar.TabControls bare = ChatChannelTabBar.controlsFor(
                name, CONTROL - 1, true, true, icon);
        assertTrue(bare.close);
        assertFalse(bare.icon);
        assertEquals(0, bare.labelRoom);
        assertEquals(100 + (tabWidth - ChatChannelTabBar.CONTROL_SIZE) / 2,
                ChatChannelTabBar.closeLeft(bare, 100, tabWidth));
        assertEquals(100 + (tabWidth - ChatChannelTabBar.CONTROL_SIZE) / 2.0D,
                ChatChannelTabBar.closeLeftExact(bare, 100, tabWidth), 0.0D);
        assertEquals(100 + tabWidth - ChatChannelTabBar.PADDING_X
                - ChatChannelTabBar.CONTROL_SIZE,
                ChatChannelTabBar.closeLeftExact(narrow, 100, tabWidth),
                0.0D);
        // A tab with no icon has nothing to give up: once its name is
        // gone its cross stands alone at once.
        ChatChannelTabBar.TabControls plain = ChatChannelTabBar.controlsFor(
                name, CONTROL, true, true, 0);
        assertTrue(plain.close);
        assertFalse(plain.icon);
        assertEquals(0, plain.labelRoom);
        assertEquals(100 + (tabWidth - ChatChannelTabBar.CONTROL_SIZE) / 2,
                ChatChannelTabBar.closeLeft(plain, 100, tabWidth));
        // Without a cross to keep, the tab in front gives way like the rest.
        ChatChannelTabBar.TabControls unclosable = ChatChannelTabBar.controlsFor(
                name, name + CONTROL - 1, false, true, icon);
        assertFalse(unclosable.cog);
        assertFalse(unclosable.close);
        assertTrue(unclosable.icon);
        assertEquals(name, unclosable.labelRoom);
    }

    /**
     * The name's exact room follows the stage the whole pixels chose:
     * what lies past the controls that stand, fractions included, never
     * more than the name and never below nothing.
     */
    @Test
    public void theNamesExactRoomFollowsTheTabsEdge() {
        int name = 40;
        ChatChannelTabBar.TabControls full = ChatChannelTabBar.controlsFor(
                name, name + 2 * CONTROL, true, false, 0);
        assertEquals(name, ChatChannelTabBar.labelRoomExact(full, name,
                name + 2 * CONTROL + 0.4D), 0.0D);
        // Half a pixel short of the whole name beside both controls: the
        // stage still shows both, and the name is cut by the half.
        assertEquals(name - 0.5D, ChatChannelTabBar.labelRoomExact(full, name,
                name + 2 * CONTROL - 0.5D), 1.0E-9D);
        ChatChannelTabBar.TabControls cut = ChatChannelTabBar.controlsFor(
                name, name / 2 + CONTROL, true, false, 0);
        assertEquals(name / 2 + 0.25D, ChatChannelTabBar.labelRoomExact(cut,
                name, name / 2 + CONTROL + 0.25D), 1.0E-9D);
        // A name the stage has already dropped has no room at all, and
        // a name with no controls beside it has the whole room.
        ChatChannelTabBar.TabControls bare = ChatChannelTabBar.controlsFor(
                name, 0, true, true, 13);
        assertEquals(0.0D, ChatChannelTabBar.labelRoomExact(bare, name, 3.7D),
                0.0D);
        ChatChannelTabBar.TabControls behind = ChatChannelTabBar.controlsFor(
                name, 12, true, false, 0);
        assertEquals(12.6D, ChatChannelTabBar.labelRoomExact(behind, name,
                12.6D), 1.0E-9D);
        assertEquals(name, ChatChannelTabBar.labelRoomExact(behind, name,
                80.0D), 0.0D);
    }

    /**
     * A row is asked for every tab at its narrowest, no tab reserved
     * more: the tab in front is the width of the rest, whichever it is.
     */
    @Test
    public void aRowIsReservedEveryTabAtItsNarrowest() {
        assertEquals(25 + 25 + 22,
                ChatChannelTabBar.reservedRowWidth(new int[] { 25, 25, 22 }));
        assertEquals(25, ChatChannelTabBar.reservedRowWidth(new int[] { 25 }));
        assertEquals(0, ChatChannelTabBar.reservedRowWidth(new int[0]));
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
