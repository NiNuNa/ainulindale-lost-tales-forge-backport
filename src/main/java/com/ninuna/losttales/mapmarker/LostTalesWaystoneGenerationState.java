package com.ninuna.losttales.mapmarker;

/** Durable state; {@code hasWaystone} by itself never means placement succeeded. */
public enum LostTalesWaystoneGenerationState {
    NOT_ATTEMPTED("not_attempted"),
    PLACED("placed"),
    DISABLED("disabled"),
    FAILED_OR_BLOCKED("failed_or_blocked");

    private final String serializedName;

    LostTalesWaystoneGenerationState(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public static LostTalesWaystoneGenerationState forSerializedName(
            String value, LostTalesWaystoneGenerationState fallback) {
        if (value != null) {
            for (LostTalesWaystoneGenerationState state : values()) {
                if (state.serializedName.equalsIgnoreCase(value.trim())
                        || state.name().equalsIgnoreCase(value.trim())) {
                    return state;
                }
            }
        }
        return fallback;
    }
}
