package com.ninuna.losttales.util;

import lotr.common.LOTRDimension;

import java.util.Locale;

/** Small dimension-name adapter for modern JSON ids in a Forge 1.7.10 world. */
public final class LostTalesDimensionHelper {
    private LostTalesDimensionHelper() {}

    public static int parseDimensionId(String dimensionName, int fallback) {
        if (dimensionName == null || dimensionName.length() == 0) {
            return fallback;
        }

        String normalized = dimensionName.toLowerCase(Locale.ROOT).trim();
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {}

        if ("minecraft:overworld".equals(normalized) || "overworld".equals(normalized)) {
            return 0;
        }
        if ("minecraft:the_nether".equals(normalized) || "minecraft:nether".equals(normalized) || "the_nether".equals(normalized) || "nether".equals(normalized)) {
            return -1;
        }
        if ("minecraft:the_end".equals(normalized) || "minecraft:end".equals(normalized) || "the_end".equals(normalized) || "end".equals(normalized)) {
            return 1;
        }
        if ("lotr:middle_earth".equals(normalized) || "lotr:middle-earth".equals(normalized) || "middle_earth".equals(normalized) || "middle-earth".equals(normalized) || "middleearth".equals(normalized)) {
            return LOTRDimension.MIDDLE_EARTH.dimensionID;
        }
        if ("lotr:utumno".equals(normalized) || "utumno".equals(normalized)) {
            return LOTRDimension.UTUMNO.dimensionID;
        }

        return fallback;
    }
}
