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
    private final boolean waypoint;
    private final int dimensionId;
    private final double x;
    private final double y;
    private final double z;
    private final double fadeInRadius;
    private final double unlockRadius;
    private final boolean hiddenUntilDiscovered;

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, int dimensionId, double x, double y, double z, boolean hiddenUntilDiscovered) {
        this(id, name, iconName, colorName, CATEGORY_DEFAULT, false, dimensionId, x, y, z, 128.0D, 8.0D, hiddenUntilDiscovered);
    }

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, String categoryName, boolean waypoint, int dimensionId, double x, double y, double z, double fadeInRadius, double unlockRadius, boolean hiddenUntilDiscovered) {
        this.id = id;
        this.name = name;
        this.iconName = iconName;
        this.colorName = colorName;
        this.categoryName = categoryName == null || categoryName.length() == 0 ? (waypoint ? CATEGORY_POINT_OF_INTEREST : CATEGORY_DEFAULT) : categoryName;
        this.waypoint = waypoint;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.fadeInRadius = fadeInRadius;
        this.unlockRadius = unlockRadius;
        this.hiddenUntilDiscovered = hiddenUntilDiscovered;
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

    public boolean isWaypoint() {
        return waypoint;
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

    public double getFadeInRadius() {
        return fadeInRadius;
    }

    public double getUnlockRadius() {
        return unlockRadius;
    }

    public boolean isHiddenUntilDiscovered() {
        return hiddenUntilDiscovered;
    }

    public String getShortDescription() {
        return id + " (" + name + ", dim " + dimensionId + " @ " + format(x) + ", " + format(y) + ", " + format(z) + ")";
    }

    private static String format(double value) {
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.01D ? String.valueOf(rounded) : String.valueOf(value);
    }
}
