package com.ninuna.losttales.quest;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import java.util.Map;

/**
 * How quest code reads the parameters a quest file gives it: a quest's
 * interaction, prerequisites and rewards, and each objective's params.
 * Every value is read trimmed, and a blank one counts as absent.
 */
public final class LostTalesQuestParams {

    private LostTalesQuestParams() {}

    /** The value under {@code key}, trimmed; empty for none. */
    public static String value(Map<String, String> params, String key) {
        String value = params == null ? null : params.get(key);
        return value == null ? "" : value.trim();
    }

    /** The whole number the value names, spaces ignored; the fallback for none or any other text. */
    public static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    /** The number the value names, spaces ignored; the fallback for none or any other text. */
    public static double parseDouble(String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    /** The number the value names, spaces ignored; null for none or any other text. */
    public static Double parseNumber(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /**
     * Where a go-to objective's coordinates point, read the same by the
     * server that checks it and the tracker and map that mark it:
     * {@code x}, {@code y} and {@code z}, all three, in the dimension
     * {@code dimension} names (a number or a name such as
     * {@code lotr:middle_earth}), else the one {@code here} is. Null when a
     * coordinate is blank or not a number.
     */
    public static Location location(Map<String, String> params, int here) {
        Double x = parseNumber(value(params, "x"));
        Double y = parseNumber(value(params, "y"));
        Double z = parseNumber(value(params, "z"));
        if (x == null || y == null || z == null) {
            return null;
        }
        return new Location(LostTalesDimensionHelper.parseDimensionId(
                        value(params, "dimension"), here),
                x.doubleValue(), y.doubleValue(), z.doubleValue());
    }

    /** A point a quest file names, in the dimension it names. */
    public static final class Location {
        private final int dimensionId;
        private final double x;
        private final double y;
        private final double z;

        Location(int dimensionId, double x, double y, double z) {
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getDimensionId() {
            return this.dimensionId;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }
    }
}
