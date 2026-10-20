package com.ninuna.losttales.compat.lotr;

import java.util.Locale;
import lotr.common.LOTRDimension;
import lotr.common.world.map.LOTRWaypoint;

/**
 * Where a deed's place lies when it names a LOTR waypoint
 * ({@code lotr:BREE}, PROG-11): in Middle-earth, at the waypoint's own
 * coordinates.
 */
public final class LotrDeedPlaces {
    public static final String PREFIX = "lotr:";

    private LotrDeedPlaces() {}

    public static boolean isWaypoint(String place) {
        return place != null && place.startsWith(PREFIX);
    }

    /** The dimension, x and z of a LOTR waypoint, or null for a name LOTR has no waypoint by. */
    public static double[] position(String place) {
        if (!isWaypoint(place)) {
            return null;
        }
        String name = place.substring(PREFIX.length()).toUpperCase(Locale.ROOT);
        for (LOTRWaypoint waypoint : LOTRWaypoint.values()) {
            if (waypoint.name().equals(name)) {
                return new double[] { LOTRDimension.MIDDLE_EARTH.dimensionID,
                        waypoint.getXCoord() + 0.5D, waypoint.getZCoord() + 0.5D };
            }
        }
        return null;
    }
}
