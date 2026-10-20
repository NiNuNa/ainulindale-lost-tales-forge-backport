package com.ninuna.losttales.gui.hud.fellowship;

import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/** The fellowship HUD stacks a row for each of the seven members nearest the player. */
public final class FellowshipHudLayoutTest {

    @Test
    public void sevenOtherMembersStackSevenRows() {
        FellowshipHudLayout.Bounds bounds = FellowshipHudLayout.calculate(
                1920, 1080, 3.0D, 28.0D, 7, FellowshipHudLayout.BOX_ROWS);
        assertEquals(7, bounds.rowCount);
        assertEquals(FellowshipHudLayout.PANEL_PADDING * 2
                + 7 * FellowshipHudLayout.ROW_HEIGHT, bounds.height);
    }

    @Test
    public void theRowsStopAtSeven() {
        assertEquals(7, FellowshipHudLayout.MAX_ROWS);
        assertEquals(FellowshipHudLayout.MAX_ROWS, FellowshipHudLayout.calculate(
                1920, 1080, 3.0D, 28.0D, 12, 3).rowCount);
        assertEquals(1, FellowshipHudLayout.calculate(
                1920, 1080, 3.0D, 28.0D, 0, 3).rowCount);
    }

    @Test
    public void thePanelStandsAtTheTopOfItsBoxOfThreeRows() {
        HudPlacementLayout.Bounds box = HudPlacementLayout.calculate(480, 270,
                FellowshipHudLayout.PANEL_WIDTH, FellowshipHudLayout.height(3),
                3.0D, 28.0D);
        for (int rows = 1; rows <= 3; rows++) {
            FellowshipHudLayout.Bounds bounds = FellowshipHudLayout.calculate(
                    480, 270, 3.0D, 28.0D, rows, FellowshipHudLayout.BOX_ROWS);
            assertEquals(box.x, bounds.x);
            assertEquals(box.y, bounds.y);
        }
    }

    @Test
    public void moreRowsThanTheBoxHoldsGrowDownHeldOnTheScreen() {
        FellowshipHudLayout.Bounds three = FellowshipHudLayout.calculate(
                480, 270, 3.0D, 28.0D, 3, FellowshipHudLayout.BOX_ROWS);
        FellowshipHudLayout.Bounds five = FellowshipHudLayout.calculate(
                480, 270, 3.0D, 28.0D, 5, FellowshipHudLayout.BOX_ROWS);
        assertEquals("a member joining moves no row above", three.y, five.y);
        FellowshipHudLayout.Bounds seven = FellowshipHudLayout.calculate(
                480, 270, 3.0D, 100.0D, 7, FellowshipHudLayout.BOX_ROWS);
        assertEquals(270, seven.y + seven.height);
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
