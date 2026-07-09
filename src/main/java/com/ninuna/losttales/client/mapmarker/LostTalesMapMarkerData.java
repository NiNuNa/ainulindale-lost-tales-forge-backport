package com.ninuna.losttales.client.mapmarker;

/**
 * Simple client-side map marker description used by the 1.7.10 compass HUD.
 *
 * The NeoForge branch loads markers through datapacks/codecs. Minecraft 1.7.10
 * does not have that system, so the backport keeps a small immutable data class
 * loaded from JSON files under assets/losttales/map_marker/.
 */
public final class LostTalesMapMarkerData {
    public static final String CATEGORY_DEFAULT = "Map Marker";
    public static final String CATEGORY_POINT_OF_INTEREST = "Point of Interest";

    private final String id;
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

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius) {
        this(id, name, iconName, colorName, CATEGORY_DEFAULT, false, dimensionId, x, y, z, compassFadeInRadius, discoveryRadius, false, false);
    }

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered) {
        this(id, name, iconName, colorName, CATEGORY_DEFAULT, false, dimensionId, x, y, z, compassFadeInRadius, discoveryRadius, hiddenUntilDiscovered, hiddenUntilDiscovered);
    }

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, String categoryName, boolean hasFastTravel, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered) {
        this(id, name, iconName, colorName, categoryName, hasFastTravel, dimensionId, x, y, z, compassFadeInRadius, discoveryRadius, hiddenUntilDiscovered, hiddenUntilDiscovered);
    }

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, String categoryName, boolean hasFastTravel, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable) {
        this(id, name, iconName, colorName, categoryName, hasFastTravel, "", dimensionId, x, y, z, compassFadeInRadius, discoveryRadius, hiddenUntilDiscovered, discoverable);
    }

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, String categoryName, boolean hasFastTravel, String fastTravelWaypointCode, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable) {
        this(id, name, iconName, colorName, categoryName, "", hasFastTravel, fastTravelWaypointCode, dimensionId, x, y, z, compassFadeInRadius, discoveryRadius, hiddenUntilDiscovered, discoverable);
    }

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, String categoryName, String description, boolean hasFastTravel, String fastTravelWaypointCode, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable) {
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

    public String getDescription() {
        return this.description;
    }

    public boolean hasFastTravel() {
        return this.hasFastTravel;
    }


    public String getFastTravelWaypointCode() {
        return this.fastTravelWaypointCode;
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

    public double getCompassFadeInRadius() {
        return this.compassFadeInRadius;
    }


    public double getDiscoveryRadius() {
        return this.discoveryRadius;
    }


    /**
     * If true, this marker has no Lost Tales map/compass presentation before
     * discovery unless it is an existing LOTR waypoint, in which case LOTR's own
     * vanilla dot remains visible until Lost Tales discovery replaces it.
     */
    public boolean isHiddenUntilDiscovered() {
        return this.hiddenUntilDiscovered;
    }

    public boolean isDiscoverable() {
        return this.discoverable;
    }

}
