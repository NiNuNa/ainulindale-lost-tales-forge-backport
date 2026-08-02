package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesLotrMapLegendTest {
    @Test
    public void wideLayoutShowsEveryCategoryWithoutPaging() {
        LostTalesLotrMapLegend.Layout layout =
                LostTalesLotrMapLegend.calculateLayout(
                        700, 480, 6, 0);

        assertTrue(layout.visible);
        assertFalse(layout.showArrows);
        assertEquals(6, layout.visibleCount);
        assertEquals(0, layout.firstIndex);
        assertEquals(480 - LostTalesLotrMapControlBar.HEIGHT
                        - LostTalesLotrMapLegend.GAP_ABOVE_CONTROL_BAR
                        - LostTalesLotrMapLegend.HEIGHT,
                layout.panelY);
        assertTrue(layout.panelX >= 0);
        assertTrue(layout.panelX + layout.panelWidth <= 700);
    }

    @Test
    public void narrowLayoutPagesAndClampsTheRequestedIndex() {
        LostTalesLotrMapLegend.Layout layout =
                LostTalesLotrMapLegend.calculateLayout(
                        320, 240, 6, 99);

        assertTrue(layout.visible);
        assertTrue(layout.showArrows);
        assertEquals(4, layout.visibleCount);
        assertEquals(2, layout.firstIndex);
        assertTrue(layout.tileWidth > 0);
        assertTrue(layout.panelX >= 0);
        assertTrue(layout.panelX + layout.panelWidth <= 320);
    }

    @Test
    public void tileAndArrowHitboxesRemainInsideThePanel() {
        LostTalesLotrMapLegend.Layout layout =
                LostTalesLotrMapLegend.calculateLayout(
                        240, 180, 6, 0);

        assertTrue(layout.showArrows);
        assertTrue(layout.containsLeftArrow(
                layout.leftArrowX, layout.panelY + 1));
        assertTrue(layout.containsRightArrow(
                layout.rightArrowX, layout.panelY + 1));
        assertTrue(layout.containsTile(
                0, layout.tileX(0), layout.tileY));
        assertTrue(layout.containsPanel(
                layout.tileX(0), layout.tileY));
    }

    @Test
    public void layoutHidesWhenTheScaledScreenIsTooShort() {
        LostTalesLotrMapLegend.Layout layout =
                LostTalesLotrMapLegend.calculateLayout(
                        320, 70, 6, 0);

        assertFalse(layout.visible);
    }
}
