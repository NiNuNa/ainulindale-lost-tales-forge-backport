package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The geometry a tab drag decides on: how far past the ends of a row a
 * carried tab has been pushed, and which edge that is measured from.
 * A row takes a tab anywhere along it, so this only decides when a tab
 * that has not yet been moved is pulled out of the row it started in.
 */
public final class ChatTabDragGeometryTest {

    @Test
    public void aTabInsideTheSpanOverrunsNothing() {
        assertEquals(0, ChatChannelTabBar.overrunOf(20, 30, 10, 100));
        assertEquals(0, ChatChannelTabBar.overrunOf(10, 30, 10, 100));
        // Flush against the right end: the last place the row can hold it.
        assertEquals(0, ChatChannelTabBar.overrunOf(70, 30, 10, 100));
    }

    @Test
    public void overrunIsMeasuredFromWhicheverEndIsPassed() {
        assertEquals(4, ChatChannelTabBar.overrunOf(6, 30, 10, 100));
        assertEquals(5, ChatChannelTabBar.overrunOf(75, 30, 10, 100));
    }

    @Test
    public void aSpanTooNarrowReadsAsOverrunAtItsRightEnd() {
        // Nowhere in the span can hold the tab; it is being pushed
        // against the end the controls stand at, and says so.
        assertTrue(ChatChannelTabBar.overrunOf(10, 200, 10, 100) > 0);
    }

    @Test
    public void pullingOutAndBackIsTheSameOnBothEnds() {
        int tabWidth = 30;
        int lowest = 10;
        // The row's own limit, where its end controls begin: the one
        // edge a carried tab is stopped at, whichever way it came.
        int border = 200;
        // The travel that tears the tab out is the same either way, and
        // so is the travel that brings it back.
        int outRight = border - tabWidth + LostTalesChatGui.SIDE_DETACH_DISTANCE;
        int outLeft = lowest - LostTalesChatGui.SIDE_DETACH_DISTANCE;
        assertEquals(LostTalesChatGui.SIDE_DETACH_DISTANCE,
                ChatChannelTabBar.overrunOf(outRight, tabWidth, lowest,
                        border));
        assertEquals(LostTalesChatGui.SIDE_DETACH_DISTANCE,
                ChatChannelTabBar.overrunOf(outLeft, tabWidth, lowest,
                        border));
    }

    @Test
    public void comingBackIsAShorterReachThanLeavingOnBothAxes() {
        // The band between the two is what stops a shaking hand tearing
        // a tab out and having it handed straight back, so neither pair
        // may be levelled up to meet the other.
        assertTrue(LostTalesChatGui.RETURN_DISTANCE
                < LostTalesChatGui.DETACH_DISTANCE);
        assertTrue(LostTalesChatGui.SIDE_RETURN_DISTANCE
                < LostTalesChatGui.SIDE_DETACH_DISTANCE);
        // Sideways the reach is measured against the room tabs sit in
        // rather than against a strip a few pixels tall, so it is the
        // longer of the two.
        assertTrue(LostTalesChatGui.SIDE_RETURN_DISTANCE
                > LostTalesChatGui.RETURN_DISTANCE);
    }

    @Test
    public void aTabRunBeginsPastTheSearchControl() {
        // What a drag has to allow for when a tab leaves a row for a
        // window of its own: the row's inset and the search control's
        // run stand between the window's edge and its first tab.
        assertTrue(ChatChannelTabBar.tabRunLeftInset() > 0);
        assertEquals(ChatChannelTabBar.tabRunLeftInset(),
                ChatChannelTabBar.overrunOf(0, 0,
                        ChatChannelTabBar.tabRunLeftInset(),
                        ChatChannelTabBar.tabRunLeftInset() + 100));
    }
}
