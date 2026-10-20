package com.ninuna.losttales.gui.hud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** One rule for every HUD panel's numbers: a share of the room its box can move in (MAP-10). */
public final class HudPlacementLayoutTest {

    @Test
    public void zeroPutsTheBoxFlushWithTheLeftAndTopEdges() {
        HudPlacementLayout.Bounds bounds = HudPlacementLayout.calculate(
                480, 270, 257, 55, 0, 0);
        assertEquals(0, bounds.x);
        assertEquals(0, bounds.y);
    }

    @Test
    public void aHundredPutsTheBoxFlushWithTheRightAndBottomEdges() {
        HudPlacementLayout.Bounds bounds = HudPlacementLayout.calculate(
                800, 450, 280, 150, 100, 100);
        assertEquals(800, bounds.x + bounds.width);
        assertEquals(450, bounds.y + bounds.height);
    }

    @Test
    public void fiftyCentresTheBox() {
        HudPlacementLayout.Bounds bounds = HudPlacementLayout.calculate(
                1000, 600, 200, 80, 50, 50);
        assertEquals(400, bounds.x);
        assertEquals(260, bounds.y);
    }

    @Test
    public void fiftyCentresAnOddBoxOnTheColumnTheCentreSnapUses() {
        // The compass is 257 wide: centred, its middle column stands on the
        // page's guide at screenWidth / 2, where the centre snap puts it.
        int place = HudPlacementLayout.place(50, 480, 257);
        HudPlacementLayout.PreciseDragResult snapped =
                HudPlacementLayout.constrainDrag(110.0D, 20.0D, 257, 55,
                        480, 270, 6);
        assertTrue(snapped.snappedX);
        assertEquals(snapped.x, place, 0.0D);
    }

    @Test
    public void theSameShareStandsAtTheSameShareOfEveryScreen() {
        for (int[] screen : new int[][] {{480, 270}, {342, 256}, {640, 360},
                {960, 540}, {427, 240}}) {
            int left = HudPlacementLayout.place(25, screen[0], 100);
            int room = screen[0] - 100;
            assertEquals(Math.round(room / 4.0D), left);
        }
    }

    @Test
    public void aShareBeyondEitherEndCountsAsThatEnd() {
        assertEquals(0, HudPlacementLayout.place(-30, 480, 166));
        assertEquals(480 - 166, HudPlacementLayout.place(150, 480, 166));
        assertEquals(0, HudPlacementLayout.place(Double.NaN, 480, 166));
        assertEquals(0, HudPlacementLayout.place(
                Double.POSITIVE_INFINITY, 480, 166));
    }

    @Test
    public void aBoxLargerThanTheScreenIsCutToItAndHasNoRoom() {
        HudPlacementLayout.Bounds bounds = HudPlacementLayout.calculate(
                200, 100, 257, 211, 70, 70);
        assertEquals(0, bounds.x);
        assertEquals(0, bounds.y);
        assertEquals(200, bounds.width);
        assertEquals(100, bounds.height);
        assertEquals(0.0D, HudPlacementLayout.share(0, 200, 257), 0.0D);
    }

    @Test
    public void aPanelNeverLeavesTheScreenAtAnySize() {
        for (int width = 1; width <= 1000; width += 37) {
            for (int height = 1; height <= 600; height += 29) {
                for (double share : new double[] {0, 12.5, 50, 87, 100}) {
                    HudPlacementLayout.Bounds bounds =
                            HudPlacementLayout.calculate(width, height, 238,
                                    73, share, share);
                    assertTrue(bounds.x >= 0 && bounds.y >= 0);
                    assertTrue(bounds.x + bounds.width <= width);
                    assertTrue(bounds.y + bounds.height <= height);
                }
            }
        }
    }

    @Test
    public void theShareOfEveryPlaceLeadsBackToIt() {
        for (int[] sizes : new int[][] {{480, 257}, {270, 55}, {342, 166},
                {256, 95}, {1000, 200}, {270, 162}}) {
            int room = sizes[0] - sizes[1];
            for (int place = 0; place <= room; place++) {
                double share = HudPlacementLayout.share(place, sizes[0],
                        sizes[1]);
                assertEquals(place, HudPlacementLayout.place(share, sizes[0],
                        sizes[1]));
            }
        }
    }

    @Test
    public void theShareOfAPlaceIsHeldBetweenTheEnds() {
        assertEquals(0.0D, HudPlacementLayout.share(0, 480, 280), 0.0D);
        assertEquals(50.0D, HudPlacementLayout.share(100, 480, 280), 0.0D);
        assertEquals(100.0D, HudPlacementLayout.share(200, 480, 280), 0.0D);
        assertEquals(0.0D, HudPlacementLayout.share(-12, 480, 280), 0.0D);
        assertEquals(100.0D, HudPlacementLayout.share(260, 480, 280), 0.0D);
        assertEquals(0.0D, HudPlacementLayout.share(Double.NaN, 480, 280),
                0.0D);
    }

    @Test
    public void aSmallerPanelStandsAtItsBoxsTopLeft() {
        HudPlacementLayout.Bounds box = HudPlacementLayout.calculate(
                480, 270, 166, 95, 3, 28);
        HudPlacementLayout.Bounds drawn = HudPlacementLayout.drawn(box,
                166, 37, 480, 270);
        assertEquals(box.x, drawn.x);
        assertEquals(box.y, drawn.y);
        assertEquals(37, drawn.height);
    }

    @Test
    public void aLargerPanelGrowsOutOfItsBoxHeldOnTheScreen() {
        HudPlacementLayout.Bounds box = HudPlacementLayout.calculate(
                480, 270, 238, 73, 4, 96);
        HudPlacementLayout.Bounds drawn = HudPlacementLayout.drawn(box,
                238, 211, 480, 270);
        assertEquals(box.x, drawn.x);
        assertEquals(270 - 211, drawn.y);
        HudPlacementLayout.Bounds tall = HudPlacementLayout.drawn(box,
                238, 400, 480, 270);
        assertEquals(0, tall.y);
        assertEquals(270, tall.height);
    }

    @Test
    public void draggingSnapsPanelCentersToBothScreenAxes() {
        HudPlacementLayout.PreciseDragResult result =
                HudPlacementLayout.constrainDrag(
                        397.0D, 258.0D, 200, 80, 1000, 600, 6);

        assertEquals(400.0D, result.x, 0.0D);
        assertEquals(260.0D, result.y, 0.0D);
        assertTrue(result.snappedX);
        assertTrue(result.snappedY);
    }

    @Test
    public void snappingAnOddWidthPanelLandsOnTheCentreGuideColumn() {
        // The compass panel is 257 wide, so its own centre is pixel column 128.
        // The page draws its vertical guide as a one-pixel column at
        // screenWidth / 2, and the snapped panel has to put that same column
        // there: halving the leftover space rounds the wrong way and leaves
        // the compass ornament one pixel left of the guide.
        int panelWidth = 257;
        int screenWidth = 1000;

        HudPlacementLayout.PreciseDragResult result =
                HudPlacementLayout.constrainDrag(
                        370.0D, 258.0D, panelWidth, 80, screenWidth, 600, 6);

        assertTrue(result.snappedX);
        assertEquals(screenWidth / 2, result.x + panelWidth / 2, 0.0D);
    }

    @Test
    public void aCarriedPanelMovesInFractionsOfAPixel() {
        HudPlacementLayout.PreciseDragResult result =
                HudPlacementLayout.constrainDrag(
                        120.25D, 40.5D, 200, 80, 1000, 600, 6);

        assertEquals(120.25D, result.x, 0.0D);
        assertEquals(40.5D, result.y, 0.0D);
        assertFalse(result.snappedX);
        assertFalse(result.snappedY);
    }

    @Test
    public void draggingCannotCrossScreenMargins() {
        HudPlacementLayout.PreciseDragResult result =
                HudPlacementLayout.constrainDrag(
                        -200.0D, 900.0D, 200, 80, 1000, 600, 6);

        assertEquals(0.0D, result.x, 0.0D);
        assertEquals(520.0D, result.y, 0.0D);
        assertFalse(result.snappedX);
        assertFalse(result.snappedY);
    }
}
