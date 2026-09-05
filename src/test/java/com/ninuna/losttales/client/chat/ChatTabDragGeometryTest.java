package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The geometry a tab drag decides detachment by: the pointer's
 * straight-line distance to the strip it is leaving, the same measure
 * on every side, so no direction of pull is cheaper than another.
 */
public final class ChatTabDragGeometryTest {

    @Test
    public void comingBackIsAShorterReachThanLeaving() {
        // The band between the two is what stops a shaking hand tearing
        // a tab out and having it handed straight back.
        assertTrue(ChatWindowGestures.RETURN_DISTANCE
                < ChatWindowGestures.DETACH_DISTANCE);
        // And a row is only offered a run near its band at all within
        // the reach that would take it back, so targeting and taking
        // back cannot argue either.
        assertTrue(ChatWindowGestures.DOCK_BAND_SLACK
                < ChatWindowGestures.RETURN_DISTANCE);
    }

    @Test
    public void theBandBetweenLeavingAndReturningOutlastsAHandTremor() {
        // A tremor too small to start a drag cannot cross the band.
        assertTrue(ChatWindowGestures.DETACH_DISTANCE
                - ChatWindowGestures.RETURN_DISTANCE
                > ChatWindowGestures.DRAG_THRESHOLD);
    }

    @Test
    public void leavingTakesLessThanARow() {
        // A run comes free before the pointer has crossed a whole tab
        // row of the window it is leaving.
        assertTrue(ChatWindowGestures.DETACH_DISTANCE
                < ChatChannelTabBar.ROW_HEIGHT);
    }

    /**
     * The pull that tears a run out is one straight-line distance to
     * the strip it is leaving, so a diagonal carry needs the same
     * travel as a straight one — no direction is the cheap way out.
     */
    @Test
    public void escapeDistanceIsTheSameInEveryDirection() {
        int reach = ChatWindowGestures.DETACH_DISTANCE;
        // Straight off the band, straight past the end: the full pull.
        assertTrue(ChatWindowGestures.pulledBeyond(reach, 0, reach));
        assertTrue(ChatWindowGestures.pulledBeyond(0, reach, reach));
        assertFalse(ChatWindowGestures.pulledBeyond(reach - 1, 0, reach));
        assertFalse(ChatWindowGestures.pulledBeyond(0, reach - 1, reach));
        // Diagonally the two overhangs add as one pull: each may be
        // well short of the reach while together they are past it.
        int corner = (int)Math.ceil(reach / Math.sqrt(2.0D));
        assertTrue(ChatWindowGestures.pulledBeyond(corner, corner, reach));
        assertFalse(ChatWindowGestures.pulledBeyond(corner - 2, corner - 2,
                reach));
        // Resting anywhere on the strip is no pull at all.
        assertFalse(ChatWindowGestures.pulledBeyond(0, 0, reach));
    }

    @Test
    public void aTabRunBeginsPastTheSearchControl() {
        // What a drag has to allow for when a tab leaves a row for a
        // window of its own: the row's inset and the search control's
        // run stand between the window's edge and its first tab.
        assertTrue(ChatChannelTabBar.tabRunLeftInset() > 0);
    }
}
