package com.ninuna.losttales.mapmarker;

/** Server-owned visibility policy for player-created markers. */
public enum LostTalesMapMarkerVisibility {
    PRIVATE("private"),
    SHARED("shared"),
    PUBLIC("public");

    private final String serializedName;

    LostTalesMapMarkerVisibility(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public static LostTalesMapMarkerVisibility forSerializedName(
            String value, LostTalesMapMarkerVisibility fallback) {
        if (value != null) {
            for (LostTalesMapMarkerVisibility visibility : values()) {
                if (visibility.serializedName.equalsIgnoreCase(value.trim())
                        || visibility.name().equalsIgnoreCase(value.trim())) {
                    return visibility;
                }
            }
        }
        return fallback;
    }
}
