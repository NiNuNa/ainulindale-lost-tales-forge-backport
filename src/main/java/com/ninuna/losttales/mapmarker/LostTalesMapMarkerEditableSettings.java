package com.ninuna.losttales.mapmarker;

/**
 * Explicit editable marker fields shared by the settings GUI, packets, and
 * server validator. Stable identity, source, ownership, sharing membership,
 * linkage, generation state, and revision are deliberately not editable
 * JSON-style settings.
 */
public final class LostTalesMapMarkerEditableSettings {
    private final String name;
    private final String iconName;
    private final String colorName;
    private final String categoryName;
    private final String description;
    private final boolean hasFastTravel;
    private final String fastTravelWaypointCode;
    private final int dimensionId;
    private final double x;
    private final double y;
    private final double z;
    private final double compassFadeInRadius;
    private final double discoveryRadius;
    private final boolean hiddenUntilDiscovered;
    private final boolean discoverable;
    private final boolean requiresRegionUnlock;
    private final boolean hasWaystone;
    private final String waystoneStructureType;
    private final LostTalesMapMarkerVisibility visibility;

    public LostTalesMapMarkerEditableSettings(
            String name, String iconName, String colorName,
            String categoryName, String description,
            boolean hasFastTravel, String fastTravelWaypointCode,
            int dimensionId, double x, double y, double z,
            double compassFadeInRadius, double discoveryRadius,
            boolean hiddenUntilDiscovered, boolean discoverable,
            boolean requiresRegionUnlock, boolean hasWaystone,
            String waystoneStructureType,
            LostTalesMapMarkerVisibility visibility) {
        this.name = value(name);
        this.iconName = value(iconName);
        this.colorName = value(colorName);
        this.categoryName = value(categoryName);
        this.description = value(description);
        this.hasFastTravel = hasFastTravel;
        this.fastTravelWaypointCode = value(fastTravelWaypointCode);
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.compassFadeInRadius = compassFadeInRadius;
        this.discoveryRadius = discoveryRadius;
        this.hiddenUntilDiscovered = hiddenUntilDiscovered;
        this.discoverable = discoverable;
        this.requiresRegionUnlock = requiresRegionUnlock;
        this.hasWaystone = hasWaystone;
        this.waystoneStructureType = value(waystoneStructureType);
        this.visibility = visibility;
    }

    public static LostTalesMapMarkerEditableSettings fromRecord(
            LostTalesMapMarkerRecord record) {
        if (record == null) {
            throw new IllegalArgumentException(
                    "editable settings require a marker record");
        }
        return new LostTalesMapMarkerEditableSettings(
                record.getName(), record.getIconName(),
                record.getColorName(), record.getCategoryName(),
                record.getDescription(), record.hasFastTravel(),
                record.getFastTravelWaypointCode(),
                record.getDimensionId(), record.getX(), record.getY(),
                record.getZ(), record.getCompassFadeInRadius(),
                record.getDiscoveryRadius(),
                record.isHiddenUntilDiscovered(),
                record.isDiscoverable(),
                record.requiresRegionUnlock(), record.hasWaystone(),
                record.getWaystoneStructureType(),
                record.getVisibility());
    }

    private static String value(String input) {
        return input == null ? "" : input;
    }

    public String getName() { return this.name; }
    public String getIconName() { return this.iconName; }
    public String getColorName() { return this.colorName; }
    public String getCategoryName() { return this.categoryName; }
    public String getDescription() { return this.description; }
    public boolean hasFastTravel() { return this.hasFastTravel; }
    public String getFastTravelWaypointCode() {
        return this.fastTravelWaypointCode;
    }
    public int getDimensionId() { return this.dimensionId; }
    public double getX() { return this.x; }
    public double getY() { return this.y; }
    public double getZ() { return this.z; }
    public double getCompassFadeInRadius() {
        return this.compassFadeInRadius;
    }
    public double getDiscoveryRadius() {
        return this.discoveryRadius;
    }
    public boolean isHiddenUntilDiscovered() {
        return this.hiddenUntilDiscovered;
    }
    public boolean isDiscoverable() { return this.discoverable; }
    public boolean requiresRegionUnlock() {
        return this.requiresRegionUnlock;
    }
    public boolean hasWaystone() { return this.hasWaystone; }
    public String getWaystoneStructureType() {
        return this.waystoneStructureType;
    }
    public LostTalesMapMarkerVisibility getVisibility() {
        return this.visibility;
    }
}
