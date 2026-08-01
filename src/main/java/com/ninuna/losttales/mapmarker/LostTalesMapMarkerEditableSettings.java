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
    private final int priority;
    private final LostTalesMapMarkerVisibility visibility;

    public LostTalesMapMarkerEditableSettings(
            String name, String iconName, String colorName,
            String categoryName, String description,
            boolean hasFastTravel,
            int dimensionId, double x, double y, double z,
            double compassFadeInRadius, double discoveryRadius,
            boolean hiddenUntilDiscovered, boolean discoverable,
            boolean requiresRegionUnlock, boolean hasWaystone,
            String waystoneStructureType,
            LostTalesMapMarkerVisibility visibility) {
        this(name, iconName, colorName, categoryName, description,
                hasFastTravel, dimensionId, x, y, z,
                compassFadeInRadius, discoveryRadius,
                hiddenUntilDiscovered, discoverable,
                requiresRegionUnlock, hasWaystone,
                waystoneStructureType, 0, visibility);
    }

    public LostTalesMapMarkerEditableSettings(
            String name, String iconName, String colorName,
            String categoryName, String description,
            boolean hasFastTravel,
            int dimensionId, double x, double y, double z,
            double compassFadeInRadius, double discoveryRadius,
            boolean hiddenUntilDiscovered, boolean discoverable,
            boolean requiresRegionUnlock, boolean hasWaystone,
            String waystoneStructureType, int priority,
            LostTalesMapMarkerVisibility visibility) {
        if (priority < LostTalesMapMarkerDefinition.MIN_PRIORITY
                || priority > LostTalesMapMarkerDefinition.MAX_PRIORITY) {
            throw new IllegalArgumentException(
                    "marker priority is out of range");
        }
        this.name = value(name);
        this.iconName = value(iconName);
        this.colorName = value(colorName);
        this.categoryName = value(categoryName);
        this.description = value(description);
        this.hasFastTravel = hasFastTravel;
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
        this.priority = priority;
        this.visibility = visibility;
    }

    public LostTalesMapMarkerEditableSettings(
            String name, String iconName, String colorName,
            String categoryName, String description,
            boolean hasFastTravel,
            int dimensionId, double x, double y, double z,
            double compassFadeInRadius, double discoveryRadius,
            boolean hiddenUntilDiscovered, boolean discoverable,
            boolean requiresRegionUnlock, boolean hasWaystone,
            String waystoneStructureType,
            LostTalesMapMarkerRelevance relevance,
            LostTalesMapMarkerVisibility visibility) {
        this(name, iconName, colorName, categoryName, description,
                hasFastTravel, dimensionId, x, y, z,
                compassFadeInRadius, discoveryRadius,
                hiddenUntilDiscovered, discoverable,
                requiresRegionUnlock, hasWaystone,
                waystoneStructureType,
                relevance == null
                        ? LostTalesMapMarkerRelevance.MEDIUM.getRank()
                        : relevance.getRank(),
                visibility);
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
                record.getDimensionId(), record.getX(), record.getY(),
                record.getZ(), record.getCompassFadeInRadius(),
                record.getDiscoveryRadius(),
                record.isHiddenUntilDiscovered(),
                record.isDiscoverable(),
                record.requiresRegionUnlock(), record.hasWaystone(),
                record.getWaystoneStructureType(),
                record.getPriority(),
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
    public int getPriority() { return this.priority; }
    public LostTalesMapMarkerRelevance getRelevance() {
        return LostTalesMapMarkerRelevance.fromRank(this.priority);
    }
    public LostTalesMapMarkerVisibility getVisibility() {
        return this.visibility;
    }
}
