package com.ninuna.losttales.mapmarker;

/**
 * Who may see a marker. A record is saved public or not; one that is not
 * public reads SHARED while it has a share and PRIVATE without one
 * ({@link LostTalesMapMarkerRecord#access}).
 */
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
