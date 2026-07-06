package com.ninuna.losttales.client.mapmarker;

/**
 * Simple client-side map marker description used by the 1.7.10 compass HUD.
 *
 * The NeoForge branch loads markers through datapacks/codecs. Minecraft 1.7.10
 * does not have that system, so the backport keeps a small immutable data class
 * loaded from JSON files under assets/losttales/map_marker/.
 */
public final class LostTalesMapMarkerData {
    private final String id;
    private final String name;
    private final String iconName;
    public static final String CATEGORY_DEFAULT = "Map Marker";
    public static final String CATEGORY_POINT_OF_INTEREST = "Point of Interest";

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

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, int dimensionId, double x, double y, double z, double fadeInRadius, double unlockRadius) {
        this(id, name, iconName, colorName, CATEGORY_DEFAULT, false, dimensionId, x, y, z, fadeInRadius, unlockRadius, false);
    }

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, int dimensionId, double x, double y, double z, double fadeInRadius, double unlockRadius, boolean hiddenUntilDiscovered) {
        this(id, name, iconName, colorName, CATEGORY_DEFAULT, false, dimensionId, x, y, z, fadeInRadius, unlockRadius, hiddenUntilDiscovered);
    }

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, String categoryName, boolean waypoint, int dimensionId, double x, double y, double z, double fadeInRadius, double unlockRadius, boolean hiddenUntilDiscovered) {
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
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getIconName() {
        return this.iconName;
    }

    public String getColorName() {
        return this.colorName;
    }

    public String getCategoryName() {
        return this.categoryName;
    }

    public boolean isWaypoint() {
        return this.waypoint;
    }

    public int getDimensionId() {
        return this.dimensionId;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public double getFadeInRadius() {
        return this.fadeInRadius;
    }

    public double getUnlockRadius() {
        return this.unlockRadius;
    }

    /**
     * True for quest/location hints that should only appear after the server has
     * synced discovery for this player. Normal static markers remain visible.
     */
    public boolean isHiddenUntilDiscovered() {
        return this.hiddenUntilDiscovered;
    }
}
