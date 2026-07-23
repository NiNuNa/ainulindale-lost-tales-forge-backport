package com.ninuna.losttales.mapmarker;

/**
 * Identifies who owns the authoritative definition and supplies source-aware
 * defaults when older marker data has no waystone fields.
 */
public enum LostTalesMapMarkerSource {
    CUSTOM_PRESET("custom_preset", true),
    LOTR_ADAPTER("lotr_adapter", false),
    PLAYER_CREATED("player_created", true),
    QUEST_DYNAMIC("quest_dynamic", false);

    private final String serializedName;
    private final boolean defaultHasWaystone;

    LostTalesMapMarkerSource(String serializedName,
                             boolean defaultHasWaystone) {
        this.serializedName = serializedName;
        this.defaultHasWaystone = defaultHasWaystone;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public boolean defaultHasWaystone() {
        return this.defaultHasWaystone;
    }

    public static LostTalesMapMarkerSource forSerializedName(
            String value, LostTalesMapMarkerSource fallback) {
        if (value != null) {
            for (LostTalesMapMarkerSource source : values()) {
                if (source.serializedName.equalsIgnoreCase(value.trim())
                        || source.name().equalsIgnoreCase(value.trim())) {
                    return source;
                }
            }
        }
        return fallback;
    }
}
