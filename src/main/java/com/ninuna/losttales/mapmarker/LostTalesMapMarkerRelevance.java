package com.ninuna.losttales.mapmarker;

import java.util.Locale;

/** Human-readable importance levels used when overlapping map markers compete. */
public enum LostTalesMapMarkerRelevance {
    VERY_LOW("very-low", -40),
    LOW("low", -20),
    MEDIUM("medium", 0),
    HIGH("high", 20),
    VERY_HIGH("very-high", 40);

    private final String serializedName;
    private final int rank;

    LostTalesMapMarkerRelevance(String serializedName, int rank) {
        this.serializedName = serializedName;
        this.rank = rank;
    }

    public String getSerializedName() { return this.serializedName; }
    public int getRank() { return this.rank; }

    /** The level a marker file names, {@code very-high} or {@code very_high}; null for a name no level has. */
    public static LostTalesMapMarkerRelevance fromSerializedName(
            String value) {
        String normalized = value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (LostTalesMapMarkerRelevance relevance : values()) {
            if (relevance.serializedName.equals(normalized)) {
                return relevance;
            }
        }
        return null;
    }

    /** The level nearest a marker's rank, as its waystone page reads it. */
    public static LostTalesMapMarkerRelevance fromRank(int rank) {
        LostTalesMapMarkerRelevance closest = MEDIUM;
        int closestDistance = Integer.MAX_VALUE;
        for (LostTalesMapMarkerRelevance relevance : values()) {
            int distance = Math.abs(rank - relevance.rank);
            if (distance < closestDistance) {
                closest = relevance;
                closestDistance = distance;
            }
        }
        return closest;
    }
}
