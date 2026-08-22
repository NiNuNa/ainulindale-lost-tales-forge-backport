package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatPointerRegionsTest {

    @Test
    public void containsIsHalfOpenAndIgnoresEmptyRectangles() {
        ChatPointerRegions regions = new ChatPointerRegions();
        regions.add(10, 20, 30, 40);
        regions.add(5, 5, 5, 50);
        regions.add(50, 60, 40, 70);
        assertEquals(1, regions.count());
        assertTrue(regions.contains(10, 20));
        assertTrue(regions.contains(29, 39));
        assertFalse(regions.contains(30, 39));
        assertFalse(regions.contains(29, 40));
        assertFalse(regions.contains(9, 25));
        regions.reset();
        assertEquals(0, regions.count());
        assertFalse(regions.contains(10, 20));
    }

    @Test
    public void translatedOverlaysAndScreenOverlaysShareOneSpace() {
        ChatPointerRegions regions = new ChatPointerRegions();
        // The input bar group is drawn 13px lower this frame.
        regions.reset(13);
        regions.add(0, 100, 10, 110);
        regions.addScreen(50, 100, 60, 110);
        assertFalse(regions.contains(5, 105));
        assertTrue(regions.contains(5, 118));
        assertTrue(regions.contains(55, 105));
        assertFalse(regions.contains(55, 118));
        regions.reset();
        regions.add(0, 100, 10, 110);
        assertTrue(regions.contains(5, 105));
    }

    @Test
    public void growsBeyondInitialCapacity() {
        ChatPointerRegions regions = new ChatPointerRegions();
        for (int index = 0; index < 20; index++) {
            regions.add(index * 10, 0, index * 10 + 5, 5);
        }
        assertEquals(20, regions.count());
        assertTrue(regions.contains(192, 2));
        assertFalse(regions.contains(196, 2));
    }
}
