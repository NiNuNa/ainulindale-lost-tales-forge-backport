package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesLotrMapControlBarTest {
    @Test
    public void wideLayoutShowsAllControlLabels() {
        LostTalesLotrMapControlBar.Layout layout =
                LostTalesLotrMapControlBar.calculateLayout(
                        600, 20, 30, 16, 24, 20, 36);

        assertTrue(layout.showClose);
        assertTrue(layout.showCloseLabel);
        assertTrue(layout.showZoom);
        assertTrue(layout.showZoomLabel);
        assertTrue(layout.showLegend);
        assertTrue(layout.showLegendLabel);
        assertTrue(layout.leftEnd <= 600 / 3);
    }

    @Test
    public void mediumLayoutKeepsCloseAndLegendWithoutOverlappingCenter() {
        LostTalesLotrMapControlBar.Layout layout =
                LostTalesLotrMapControlBar.calculateLayout(
                        240, 20, 30, 16, 24, 20, 36);

        assertTrue(layout.showClose);
        assertFalse(layout.showCloseLabel);
        assertFalse(layout.showZoom);
        assertFalse(layout.showZoomLabel);
        assertTrue(layout.showLegend);
        assertFalse(layout.showLegendLabel);
        assertTrue(layout.leftEnd <= 240 / 3);
    }

    @Test
    public void extremelyNarrowLayoutOmitsControlsThatCannotFit() {
        LostTalesLotrMapControlBar.Layout layout =
                LostTalesLotrMapControlBar.calculateLayout(
                        60, 20, 30, 16, 24, 20, 36);

        assertFalse(layout.showClose);
        assertFalse(layout.showZoom);
        assertFalse(layout.showLegend);
        assertTrue(layout.leftEnd <= 60 / 3);
    }
}
