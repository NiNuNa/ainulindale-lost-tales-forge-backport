package com.ninuna.losttales.gui.hud;

import org.junit.Test;

import static com.ninuna.losttales.gui.hud.HudPlacementLayout.CoordinateMode.AVAILABLE_SPACE_PERCENT;
import static com.ninuna.losttales.gui.hud.HudPlacementLayout.CoordinateMode.SCREEN_PERCENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HudPlacementLayoutTest {

    @Test
    public void availableSpacePercentCentersTheWholePanel() {
        HudPlacementLayout.Bounds bounds = HudPlacementLayout.calculate(
                1000, 600, 200, 80, 50, 50,
                AVAILABLE_SPACE_PERCENT, AVAILABLE_SPACE_PERCENT);

        assertEquals(400, bounds.x);
        assertEquals(260, bounds.y);
    }

    @Test
    public void screenPercentCoordinatesAreClampedInsideTheDisplay() {
        HudPlacementLayout.Bounds bounds = HudPlacementLayout.calculate(
                800, 450, 280, 150, 100, 100,
                SCREEN_PERCENT, SCREEN_PERCENT);

        assertEquals(516, bounds.x);
        assertEquals(296, bounds.y);
        assertEquals(800 - HudPlacementLayout.SCREEN_MARGIN,
                bounds.x + bounds.width);
        assertEquals(450 - HudPlacementLayout.SCREEN_MARGIN,
                bounds.y + bounds.height);
    }

    @Test
    public void positionConversionRoundTripsBothCoordinateModes() {
        assertEquals(50.0D, HudPlacementLayout.percentForPosition(
                400, 1000, 200, AVAILABLE_SPACE_PERCENT, 0), 0.0001D);
        assertEquals(62.0D, HudPlacementLayout.percentForPosition(
                620, 1000, 280, SCREEN_PERCENT, 0), 0.0001D);
    }

    @Test
    public void subPercentOffsetsPreserveSinglePixelDragging() {
        double percent = HudPlacementLayout.percentForPosition(
                317, 1000, 200, AVAILABLE_SPACE_PERCENT, 0);
        HudPlacementLayout.Bounds bounds = HudPlacementLayout.calculate(
                1000, 600, 200, 80, percent, 50,
                AVAILABLE_SPACE_PERCENT, AVAILABLE_SPACE_PERCENT);

        assertEquals(317, bounds.x);
    }

    @Test
    public void draggingSnapsPanelCentersToBothScreenAxes() {
        HudPlacementLayout.DragResult result =
                HudPlacementLayout.constrainDrag(
                        397, 258, 200, 80, 1000, 600, 6);

        assertEquals(400, result.x);
        assertEquals(260, result.y);
        assertTrue(result.snappedX);
        assertTrue(result.snappedY);
    }

    @Test
    public void draggingCannotCrossScreenMargins() {
        HudPlacementLayout.DragResult result =
                HudPlacementLayout.constrainDrag(
                        -200, 900, 200, 80, 1000, 600, 6);

        assertEquals(4, result.x);
        assertEquals(516, result.y);
        assertFalse(result.snappedX);
        assertFalse(result.snappedY);
    }
}
