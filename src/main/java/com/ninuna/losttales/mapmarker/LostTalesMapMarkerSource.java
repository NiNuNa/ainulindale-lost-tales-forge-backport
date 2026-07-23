package com.ninuna.losttales.mapmarker;

/** Identifies who owns the authoritative marker definition. */
public enum LostTalesMapMarkerSource {
    CUSTOM_PRESET("custom_preset"),
    LOTR_ADAPTER("lotr_adapter"),
    PLAYER_CREATED("player_created"),
    QUEST_DYNAMIC("quest_dynamic");

    private final String serializedName;

    LostTalesMapMarkerSource(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return this.serializedName;
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
