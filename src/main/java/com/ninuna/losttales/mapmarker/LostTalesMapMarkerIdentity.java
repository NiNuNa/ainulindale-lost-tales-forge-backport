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
    public enum Authority {
        WORLD_RECORD,
        QUEST_PLAYER,
        PARTY_CHARACTER
    }

    private final String markerId;
    private final String canonicalKey;
    private final Authority authority;

    private LostTalesMapMarkerIdentity(
            String markerId, String canonicalKey,
            Authority authority) {
        this.markerId = markerId;
        this.canonicalKey = canonicalKey;
        this.authority = authority;
    }

    public static LostTalesMapMarkerIdentity create(
            String markerId, Authority authority) {
        String normalized = markerId == null ? "" : markerId.trim();
        if (normalized.length() == 0) {
            throw new IllegalArgumentException(
                    "marker identity requires a non-empty ID");
        }
        if (authority == null) {
            throw new IllegalArgumentException(
                    "marker identity requires an authority");
        }
        return new LostTalesMapMarkerIdentity(
                normalized, canonicalize(normalized), authority);
    }

    public String getMarkerId() {
        return this.markerId;
    }

    public String getCanonicalKey() {
        return this.canonicalKey;
    }

    public Authority getAuthority() {
        return this.authority;
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
