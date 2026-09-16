package com.ninuna.losttales.client.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The player list keeps the game's own grid: the server's player cap in
 * columns of at most twenty rows, the columns sharing three hundred
 * pixels and none wider than a hundred and fifty, and the ping bars
 * stepping down at the game's own thresholds.
 */
public final class LostTalesPlayerListOverlayTest {

    @Test
    public void theCapSpreadsOverColumnsOfAtMostTwentyRows() {
        assertEquals(1, LostTalesPlayerListOverlay.columns(20));
        assertEquals(20, LostTalesPlayerListOverlay.rows(20, 1));
        assertEquals(2, LostTalesPlayerListOverlay.columns(21));
        assertEquals(11, LostTalesPlayerListOverlay.rows(21, 2));
        assertEquals(3, LostTalesPlayerListOverlay.columns(41));
        assertEquals(14, LostTalesPlayerListOverlay.rows(41, 3));
        assertEquals(1, LostTalesPlayerListOverlay.columns(0));
    }

    @Test
    public void columnsShareTheWidthUpToTheCap() {
        assertEquals(150, LostTalesPlayerListOverlay.columnWidth(1));
        assertEquals(150, LostTalesPlayerListOverlay.columnWidth(2));
        assertEquals(100, LostTalesPlayerListOverlay.columnWidth(3));
        assertEquals(75, LostTalesPlayerListOverlay.columnWidth(4));
    }

    @Test
    public void thePingBarsStepDownAtTheGamesThresholds() {
        assertEquals(5, LostTalesPlayerListOverlay.pingIndex(-1));
        assertEquals(0, LostTalesPlayerListOverlay.pingIndex(0));
        assertEquals(0, LostTalesPlayerListOverlay.pingIndex(149));
        assertEquals(1, LostTalesPlayerListOverlay.pingIndex(150));
        assertEquals(2, LostTalesPlayerListOverlay.pingIndex(300));
        assertEquals(3, LostTalesPlayerListOverlay.pingIndex(600));
        assertEquals(4, LostTalesPlayerListOverlay.pingIndex(1000));
    }
}
