package com.ninuna.losttales.mapmarker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

    public LostTalesMapMarkerRelevance next() {
        LostTalesMapMarkerRelevance[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static LostTalesMapMarkerRelevance fromSerializedName(
            String value) {
        String normalized = value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        // Read the two names used by the short-lived six-level format so
        // existing resource packs do not stop loading after this cleanup.
        if ("normal".equals(normalized)) {
            normalized = MEDIUM.serializedName;
        } else if ("landmark".equals(normalized)) {
            normalized = VERY_HIGH.serializedName;
        }
        for (LostTalesMapMarkerRelevance relevance : values()) {
            if (relevance.serializedName.equals(normalized)) {
                return relevance;
            }
        }
        return null;
    }

    /** Maps old numeric saves to the nearest named level. */
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

    /**
     * Reads the named JSON setting. The former numeric key remains accepted
     * so resource packs authored against the transitional build still load.
     */
    public static Integer parseJsonRank(JsonObject object) {
        if (object == null) {
            return null;
        }
        JsonElement relevanceElement = object.get("relevance");
        if (relevanceElement != null && !relevanceElement.isJsonNull()) {
            if (!relevanceElement.isJsonPrimitive()
                    || !relevanceElement.getAsJsonPrimitive().isString()) {
                return null;
            }
            LostTalesMapMarkerRelevance relevance = fromSerializedName(
                    relevanceElement.getAsString());
            return relevance == null ? null
                    : Integer.valueOf(relevance.rank);
        }

        JsonElement legacy = object.get("priority");
        if (legacy == null || legacy.isJsonNull()) {
            return Integer.valueOf(MEDIUM.rank);
        }
        if (!legacy.isJsonPrimitive()
                || !legacy.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            double value = legacy.getAsDouble();
            if (Double.isNaN(value) || Double.isInfinite(value)
                    || value != Math.rint(value)
                    || value < LostTalesMapMarkerDefinition.MIN_PRIORITY
                    || value > LostTalesMapMarkerDefinition.MAX_PRIORITY) {
                return null;
            }
            return Integer.valueOf((int)value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
