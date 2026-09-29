package com.ninuna.losttales.mapmarker;

import java.util.Locale;

/**
 * Runtime identity for one logical map marker.
 *
 * Persisted IDs are not rewritten. Only native LOTR waypoint IDs receive a
 * canonical comparison key because their code names are case-insensitive in
 * LOTR's registry.
 */
public final class LostTalesMapMarkerIdentity {
    private final String canonicalKey;

    private LostTalesMapMarkerIdentity(String canonicalKey) {
        this.canonicalKey = canonicalKey;
    }

    public static LostTalesMapMarkerIdentity create(String markerId) {
        String normalized = markerId == null ? "" : markerId.trim();
        if (normalized.length() == 0) {
            throw new IllegalArgumentException(
                    "marker identity requires a non-empty ID");
        }
        return new LostTalesMapMarkerIdentity(canonicalize(normalized));
    }

    public String getCanonicalKey() {
        return this.canonicalKey;
    }

    public boolean isSameLogicalMarker(
            LostTalesMapMarkerIdentity other) {
        return other != null
                && this.canonicalKey.equals(other.canonicalKey);
    }

    @Override
    public boolean equals(Object value) {
        return value instanceof LostTalesMapMarkerIdentity
                && isSameLogicalMarker(
                        (LostTalesMapMarkerIdentity)value);
    }

    @Override
    public int hashCode() {
        return this.canonicalKey.hashCode();
    }

    @Override
    public String toString() {
        return this.canonicalKey;
    }

    private static String canonicalize(String markerId) {
        String waypointId =
                LostTalesMapMarkerIdResolver.resolveLotrWaypointId(
                        markerId);
        if (waypointId.length() > 0) {
            return LostTalesMapMarkerIdResolver.LOTR_WAYPOINT_PREFIX
                    + waypointId.toLowerCase(Locale.ROOT);
        }
        return markerId;
    }
}
