package com.ninuna.losttales.world.biome.gen;

/**
 * The heights at which the Naldossë Mountains turn from soil to bare rock,
 * and from rock to an ice cap: snow on top, glacier ice under it. Each line
 * drops by a column's rock depth, so the bands follow LOTR's stone noise
 * instead of lying level.
 */
public final class MoonElfMountainBands {

    /** What a column's top and filler blocks become at one height. */
    public enum Band {
        /** The soil stays, so trees can grow. */
        SOIL,
        /** Bare stone. */
        ROCK,
        /** Snow on top, packed ice under it. */
        ICE_CAP
    }

    /** Where bare rock starts, before a column's rock depth. */
    public static final int ROCK_LINE = 88;
    /** Where the ice cap starts, before a column's rock depth. */
    public static final int ICE_LINE = 118;

    private MoonElfMountainBands() {
    }

    /** The band at height {@code y} in a column of the given rock depth. */
    public static Band bandAt(int y, int rockDepth) {
        if (y >= ICE_LINE - rockDepth) {
            return Band.ICE_CAP;
        }
        if (y >= ROCK_LINE - rockDepth) {
            return Band.ROCK;
        }
        return Band.SOIL;
    }

    /** The lowest height in a column that is not soil, never below 0. */
    public static int lowestBare(int rockDepth) {
        return Math.max(0, ROCK_LINE - rockDepth);
    }
}
