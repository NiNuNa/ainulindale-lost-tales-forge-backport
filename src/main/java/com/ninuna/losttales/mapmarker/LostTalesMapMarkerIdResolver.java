package com.ninuna.losttales.mapmarker;

import java.util.Locale;

/** Derives native LOTR waypoint identities encoded in marker IDs. */
public final class LostTalesMapMarkerIdResolver {
    public static final String LOTR_WAYPOINT_PREFIX =
            "lotr:waypoint:";

    private LostTalesMapMarkerIdResolver() {}

    /** Resolves {@code lotr:waypoint:oatbarton} to {@code OATBARTON}. */
    public static String resolveLotrWaypointId(String markerId) {
        String id = trim(markerId);
        if (!id.regionMatches(
                true, 0, LOTR_WAYPOINT_PREFIX, 0,
                LOTR_WAYPOINT_PREFIX.length())
                || id.length() == LOTR_WAYPOINT_PREFIX.length()) {
            return "";
        }
        String derived = id.substring(
                LOTR_WAYPOINT_PREFIX.length());
        if (!derived.matches("[A-Za-z0-9_]+")) {
            return "";
        }
        return derived.toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
