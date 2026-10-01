package com.ninuna.losttales.gui.hud.fellowship;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/** The fellowship HUD stacks a row for each of the seven members nearest the player. */
public final class FellowshipHudLayoutTest {

    @Test
    public void sevenOtherMembersStackSevenRows() {
        FellowshipHudLayout.Bounds bounds = FellowshipHudLayout.calculate(
                1920, 1080, 2.0D, 18.0D, 7);
        assertEquals(7, bounds.rowCount);
        assertEquals(FellowshipHudLayout.PANEL_PADDING * 2
                + 7 * FellowshipHudLayout.ROW_HEIGHT, bounds.height);
    }

    @Test
    public void theRowsStopAtSeven() {
        assertEquals(7, FellowshipHudLayout.MAX_ROWS);
        assertEquals(FellowshipHudLayout.MAX_ROWS, FellowshipHudLayout.calculate(
                1920, 1080, 2.0D, 18.0D, 12).rowCount);
        assertEquals(1, FellowshipHudLayout.calculate(
                1920, 1080, 2.0D, 18.0D, 0).rowCount);
    }

    @Test
    public void theNearestComeFirstAndTheUnseenKeepTheirOrder() {
        Integer[] order = FellowshipHudLayout.nearestFirst(new double[] {
                Double.NaN, 400.0D, Double.NaN, 25.0D, 400.0D});
        assertArrayEquals(new Integer[] {3, 1, 4, 0, 2}, order);
    }

    @Test
    public void theHeightFollowsTheRows() {
        assertEquals(FellowshipHudLayout.PANEL_PADDING * 2
                + 3 * FellowshipHudLayout.ROW_HEIGHT, FellowshipHudLayout.height(3));
    }
}
