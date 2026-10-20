package com.ninuna.losttales.mapmarker;

/**
 * The settings a waystone's page edits, as the page, the packets and the
 * server's check share them. Its id, source, place, structure, owner,
 * shares, link, generation state and revision are the record's alone and
 * never travel here.
 */
public final class LostTalesMapMarkerEditableSettings {
    private final String name;
    private final String iconName;
    private final String colorName;
    private final String categoryName;
    private final String description;
    private final boolean hasFastTravel;
    private final double compassFadeInRadius;
    private final double discoveryRadius;
    private final boolean hiddenUntilDiscovered;
    private final boolean discoverable;
    private final boolean requiresRegionUnlock;
    private final int priority;
    private final LostTalesMapMarkerVisibility visibility;

    public LostTalesMapMarkerEditableSettings(
            String name, String iconName, String colorName,
            String categoryName, String description,
            boolean hasFastTravel,
            double compassFadeInRadius, double discoveryRadius,
            boolean hiddenUntilDiscovered, boolean discoverable,
            boolean requiresRegionUnlock, int priority,
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
        this.compassFadeInRadius = compassFadeInRadius;
        this.discoveryRadius = discoveryRadius;
        this.hiddenUntilDiscovered = hiddenUntilDiscovered;
        this.discoverable = discoverable;
        this.requiresRegionUnlock = requiresRegionUnlock;
        this.priority = priority;
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
                record.getCompassFadeInRadius(),
                record.getDiscoveryRadius(),
                record.isHiddenUntilDiscovered(),
                record.isDiscoverable(),
                record.requiresRegionUnlock(),
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
    public int getPriority() { return this.priority; }
    public LostTalesMapMarkerVisibility getVisibility() {
        return this.visibility;
    }
}
