package com.ninuna.losttales.mapmarker;

import java.util.EnumSet;

/**
 * A setting a marker's page saves. A record keeps which of them were saved
 * on its page with another value than it had
 * ({@link LostTalesMapMarkerRecord#getSavedSettings}), and
 * {@code /losttales mapmarker reseed} keeps those while every other comes
 * from the marker's file (WAY-16).
 */
public enum LostTalesMapMarkerSetting {
    NAME("name"),
    ICON("icon"),
    COLOR("color"),
    CATEGORY("category"),
    DESCRIPTION("description"),
    FAST_TRAVEL("fast_travel"),
    COMPASS_RADIUS("compass_radius"),
    DISCOVERY_RADIUS("discovery_radius"),
    DISCOVERABLE("discoverable"),
    HIDDEN("hidden"),
    REGION("region"),
    RELEVANCE("relevance"),
    ACCESS("access");

    private final String serializedName;

    LostTalesMapMarkerSetting(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    /** The setting saved under {@code value}; null for a name no setting has. */
    public static LostTalesMapMarkerSetting forSerializedName(String value) {
        for (LostTalesMapMarkerSetting setting : values()) {
            if (setting.serializedName.equals(value)) {
                return setting;
            }
        }
        return null;
    }

    /** The settings that read differently on {@code after} than on {@code before}. */
    static EnumSet<LostTalesMapMarkerSetting> changed(
            LostTalesMapMarkerRecord before, LostTalesMapMarkerRecord after) {
        EnumSet<LostTalesMapMarkerSetting> changed =
                EnumSet.noneOf(LostTalesMapMarkerSetting.class);
        if (!before.getName().equals(after.getName())) {
            changed.add(NAME);
        }
        if (!before.getIconName().equals(after.getIconName())) {
            changed.add(ICON);
        }
        if (!before.getColorName().equals(after.getColorName())) {
            changed.add(COLOR);
        }
        if (!before.getCategoryName().equals(after.getCategoryName())) {
            changed.add(CATEGORY);
        }
        if (!before.getDescription().equals(after.getDescription())) {
            changed.add(DESCRIPTION);
        }
        if (before.hasFastTravel() != after.hasFastTravel()) {
            changed.add(FAST_TRAVEL);
        }
        if (Double.compare(before.getCompassFadeInRadius(),
                after.getCompassFadeInRadius()) != 0) {
            changed.add(COMPASS_RADIUS);
        }
        if (Double.compare(before.getDiscoveryRadius(),
                after.getDiscoveryRadius()) != 0) {
            changed.add(DISCOVERY_RADIUS);
        }
        if (before.isDiscoverable() != after.isDiscoverable()) {
            changed.add(DISCOVERABLE);
        }
        if (before.isHiddenUntilDiscovered()
                != after.isHiddenUntilDiscovered()) {
            changed.add(HIDDEN);
        }
        if (before.requiresRegionUnlock() != after.requiresRegionUnlock()) {
            changed.add(REGION);
        }
        if (before.getPriority() != after.getPriority()) {
            changed.add(RELEVANCE);
        }
        if ((before.getVisibility() == LostTalesMapMarkerVisibility.PUBLIC)
                != (after.getVisibility()
                        == LostTalesMapMarkerVisibility.PUBLIC)) {
            changed.add(ACCESS);
        }
        return changed;
    }
}
