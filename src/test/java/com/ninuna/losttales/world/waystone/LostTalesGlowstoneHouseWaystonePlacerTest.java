package com.ninuna.losttales.world.waystone;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LostTalesGlowstoneHouseWaystonePlacerTest {

    @Test
    public void safeSiteSearchStartsAtMarkerAndCoversNearestRings() {
        int[][] offsets =
                LostTalesGlowstoneHouseWaystonePlacer
                        .getSiteSearchOffsetsForTest();

        assertEquals(33 * 33, offsets.length);
        assertEquals(0, offsets[0][0]);
        assertEquals(0, offsets[0][1]);
        int previousRadius = 0;
        Set<String> unique = new HashSet<String>();
        for (int[] offset : offsets) {
            int radius = Math.max(
                    Math.abs(offset[0]), Math.abs(offset[1]));
            assertTrue(radius >= previousRadius);
            assertTrue(radius <= 16);
            assertTrue(unique.add(
                    offset[0] + ":" + offset[1]));
            previousRadius = radius;
        }
    }
}
