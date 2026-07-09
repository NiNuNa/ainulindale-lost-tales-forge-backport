package com.ninuna.losttales.mapmarker;

/** Server-safe metadata for bundled/static map markers. */
public final class LostTalesMapMarkerDefinition {
    public static final String CATEGORY_DEFAULT = "Map Marker";
    public static final String CATEGORY_POINT_OF_INTEREST = "Point of Interest";

    private final String id;
    private final String name;
    private final String iconName;
    private final String colorName;
    private final String categoryName;
    private final String description;
    /** True when this marker is backed by a LOTR waypoint/fast travel entry. */
    private final boolean hasFastTravel;
    /** Existing LOTR waypoint code to link this marker to, if any. */
    private final String fastTravelWaypointCode;
    private final int dimensionId;
    private final double x;
    private final double y;
    private final double z;
    private final double compassFadeInRadius;
    private final double discoveryRadius;
    private final boolean hiddenUntilDiscovered;
    /** True when walking into discoveryRadius should unlock this marker. */
    private final boolean discoverable;

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, int dimensionId, double x, double y, double z, boolean hiddenUntilDiscovered) {
        this(id, name, iconName, colorName, CATEGORY_DEFAULT, false, dimensionId, x, y, z, 128.0D, 8.0D, hiddenUntilDiscovered, hiddenUntilDiscovered);
    }

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, String categoryName, boolean hasFastTravel, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered) {
        this(id, name, iconName, colorName, categoryName, hasFastTravel, dimensionId, x, y, z, compassFadeInRadius, discoveryRadius, hiddenUntilDiscovered, hiddenUntilDiscovered);
    }

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, String categoryName, boolean hasFastTravel, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable) {
        this(id, name, iconName, colorName, categoryName, hasFastTravel, "", dimensionId, x, y, z, compassFadeInRadius, discoveryRadius, hiddenUntilDiscovered, discoverable);
    }

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, String categoryName, boolean hasFastTravel, String fastTravelWaypointCode, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable) {
        this(id, name, iconName, colorName, categoryName, "", hasFastTravel, fastTravelWaypointCode, dimensionId, x, y, z, compassFadeInRadius, discoveryRadius, hiddenUntilDiscovered, discoverable);
    }

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, String categoryName, String description, boolean hasFastTravel, String fastTravelWaypointCode, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable) {
        this.id = id;
        this.name = name;
        this.iconName = iconName;
        this.colorName = colorName;
        this.categoryName = categoryName == null || categoryName.length() == 0 ? (hasFastTravel ? CATEGORY_POINT_OF_INTEREST : CATEGORY_DEFAULT) : categoryName;
        this.description = normalizeDescription(description, this.categoryName);
        this.hasFastTravel = hasFastTravel;
        this.fastTravelWaypointCode = fastTravelWaypointCode == null ? "" : fastTravelWaypointCode.trim();
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.compassFadeInRadius = compassFadeInRadius;
        this.discoveryRadius = discoveryRadius;
        this.hiddenUntilDiscovered = hiddenUntilDiscovered;
        this.discoverable = discoverable;
    }


    private static String normalizeDescription(String description, String categoryName) {
        if (description != null && description.trim().length() > 0) {
            return description.trim();
        }
        if (categoryName != null && categoryName.length() > 0) {
            return "A discovered " + categoryName.toLowerCase() + " in Middle-earth.";
        }
        return "A discovered location in Middle-earth.";
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIconName() {
        return iconName;
    }

    public String getColorName() {
        return colorName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasFastTravel() {
        return hasFastTravel;
    }


    public String getFastTravelWaypointCode() {
        return fastTravelWaypointCode;
    }


    public int getDimensionId() {
        return dimensionId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double getCompassFadeInRadius() {
        return compassFadeInRadius;
    }


    public double getDiscoveryRadius() {
        return discoveryRadius;
    }


    public boolean isHiddenUntilDiscovered() {
        return hiddenUntilDiscovered;
    }

    public boolean isDiscoverable() {
        return discoverable;
    }


    public String getShortDescription() {
        return id + " (" + name + ", dim " + dimensionId + " @ " + format(x) + ", " + format(y) + ", " + format(z) + ", discovery " + format(discoveryRadius) + ")";
    }

    private static String format(double value) {
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.01D ? String.valueOf(rounded) : String.valueOf(value);
    }
}
