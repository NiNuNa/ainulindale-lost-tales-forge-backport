package com.ninuna.losttales.world.biome.gen;

import com.ninuna.losttales.world.biome.gen.MoonElfMountainBands.Band;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MoonElfMountainBandsTest {

    @Test
    public void theIceCapStandsAboveTheBareRock() {
        assertTrue(MoonElfMountainBands.ICE_LINE > MoonElfMountainBands.ROCK_LINE);
    }

    @Test
    public void soilStaysBelowTheRockLine() {
        assertEquals(Band.SOIL, MoonElfMountainBands.bandAt(MoonElfMountainBands.ROCK_LINE - 1, 0));
        assertEquals(Band.SOIL, MoonElfMountainBands.bandAt(0, 0));
    }

    @Test
    public void rockStartsAtTheRockLine() {
        assertEquals(Band.ROCK, MoonElfMountainBands.bandAt(MoonElfMountainBands.ROCK_LINE, 0));
        assertEquals(Band.ROCK, MoonElfMountainBands.bandAt(MoonElfMountainBands.ICE_LINE - 1, 0));
    }

    @Test
    public void theIceCapStartsAtTheIceLine() {
        assertEquals(Band.ICE_CAP, MoonElfMountainBands.bandAt(MoonElfMountainBands.ICE_LINE, 0));
        assertEquals(Band.ICE_CAP, MoonElfMountainBands.bandAt(255, 0));
    }

    @Test
    public void aDeeperRockDepthLowersBothLines() {
        int depth = 6;
        assertEquals(Band.SOIL, MoonElfMountainBands.bandAt(MoonElfMountainBands.ROCK_LINE - depth - 1, depth));
        assertEquals(Band.ROCK, MoonElfMountainBands.bandAt(MoonElfMountainBands.ROCK_LINE - depth, depth));
        assertEquals(Band.ROCK, MoonElfMountainBands.bandAt(MoonElfMountainBands.ICE_LINE - depth - 1, depth));
        assertEquals(Band.ICE_CAP, MoonElfMountainBands.bandAt(MoonElfMountainBands.ICE_LINE - depth, depth));
    }

    @Test
    public void aNegativeRockDepthRaisesBothLines() {
        assertEquals(Band.SOIL, MoonElfMountainBands.bandAt(MoonElfMountainBands.ROCK_LINE, -3));
        assertEquals(Band.ROCK, MoonElfMountainBands.bandAt(MoonElfMountainBands.ROCK_LINE + 3, -3));
        assertEquals(Band.ROCK, MoonElfMountainBands.bandAt(MoonElfMountainBands.ICE_LINE, -3));
        assertEquals(Band.ICE_CAP, MoonElfMountainBands.bandAt(MoonElfMountainBands.ICE_LINE + 3, -3));
    }

    @Test
    public void theLowestBareHeightIsWhereRockStarts() {
        for (int depth = -4; depth <= 12; depth++) {
            int lowest = MoonElfMountainBands.lowestBare(depth);
            assertEquals(Band.ROCK, MoonElfMountainBands.bandAt(lowest, depth));
            assertEquals(Band.SOIL, MoonElfMountainBands.bandAt(lowest - 1, depth));
        }
    }

    @Test
    public void theLowestBareHeightNeverFallsBelowTheWorld() {
        assertEquals(0, MoonElfMountainBands.lowestBare(MoonElfMountainBands.ROCK_LINE + 40));
    }
}
