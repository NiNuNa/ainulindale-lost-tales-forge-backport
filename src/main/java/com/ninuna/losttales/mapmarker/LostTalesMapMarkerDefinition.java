package com.ninuna.losttales.mapmarker;

import net.minecraft.world.World;

/** Server-safe metadata for bundled/static map markers. */
public final class LostTalesMapMarkerDefinition {
    public static final String CATEGORY_DEFAULT = "Map Marker";
    public static final String CATEGORY_POINT_OF_INTEREST = "Point of Interest";
    public static final double AUTOMATIC_Y =
            LostTalesMapMarkerHeightResolver.AUTOMATIC_Y;

    private final String id;
    private final String name;
    private final String iconName;
    private final String colorName;
    private final String categoryName;
    private final String description;
    /** True when this marker is backed by a LOTR waypoint/fast travel entry. */
    private final boolean hasFastTravel;
    private final int dimensionId;
    private final double x;
    private final double y;
    private final double z;
    private final double compassFadeInRadius;
    private final double discoveryRadius;
    private final boolean hiddenUntilDiscovered;
    /** True when walking into discoveryRadius should unlock this marker. */
    private final boolean discoverable;
    /** True when visibility also requires the marker's LOTR region to be visited. */
    private final boolean requiresRegionUnlock;
    private final LostTalesMapMarkerSource source;
    /** Desired physical representation; placement/link state is persisted elsewhere. */
    private final boolean hasWaystone;
    /** Namespaced structure placer selected if physical generation is enabled. */
    private final String waystoneStructureType;

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, int dimensionId, double x, double y, double z, boolean hiddenUntilDiscovered) {
        this(id, name, iconName, colorName, CATEGORY_DEFAULT, false, dimensionId, x, y, z, 128.0D, 8.0D, hiddenUntilDiscovered, hiddenUntilDiscovered);
    }

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, String categoryName, boolean hasFastTravel, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered) {
        this(id, name, iconName, colorName, categoryName, hasFastTravel, dimensionId, x, y, z, compassFadeInRadius, discoveryRadius, hiddenUntilDiscovered, hiddenUntilDiscovered);
    }

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, String categoryName, boolean hasFastTravel, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable) {
        this(id, name, iconName, colorName, categoryName, "",
                hasFastTravel, dimensionId, x, y, z,
                compassFadeInRadius, discoveryRadius,
                hiddenUntilDiscovered, discoverable);
    }

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, String categoryName, boolean hasFastTravel, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable, boolean requiresRegionUnlock) {
        this(id, name, iconName, colorName, categoryName, "",
                hasFastTravel, dimensionId, x, y, z,
                compassFadeInRadius, discoveryRadius,
                hiddenUntilDiscovered, discoverable,
                requiresRegionUnlock);
    }

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, String categoryName, String description, boolean hasFastTravel, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable) {
        this(id, name, iconName, colorName, categoryName, description,
                hasFastTravel, dimensionId,
                x, y, z, compassFadeInRadius, discoveryRadius,
                hiddenUntilDiscovered, discoverable, false);
    }

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, String categoryName, String description, boolean hasFastTravel, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable, boolean requiresRegionUnlock) {
        this(id, name, iconName, colorName, categoryName, description,
                hasFastTravel, dimensionId,
                x, y, z, compassFadeInRadius, discoveryRadius,
                hiddenUntilDiscovered, discoverable, requiresRegionUnlock,
                LostTalesMapMarkerSource.QUEST_DYNAMIC, false, "");
    }

    public LostTalesMapMarkerDefinition(String id, String name,
                                        String iconName, String colorName,
                                        String categoryName,
                                        String description,
                                        boolean hasFastTravel,
                                        int dimensionId,
                                        double x, double y, double z,
                                        double compassFadeInRadius,
                                        double discoveryRadius,
                                        boolean hiddenUntilDiscovered,
                                        boolean discoverable,
                                        boolean requiresRegionUnlock,
                                        LostTalesMapMarkerSource source,
                                        boolean hasWaystone,
                                        String waystoneStructureType) {
        this.id = id;
        this.name = name;
        this.iconName = iconName;
        this.colorName = colorName;
        this.categoryName = categoryName == null || categoryName.length() == 0 ? (hasFastTravel ? CATEGORY_POINT_OF_INTEREST : CATEGORY_DEFAULT) : categoryName;
        this.description = description == null ? "" : description.trim();
        this.hasFastTravel = hasFastTravel;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.compassFadeInRadius = compassFadeInRadius;
        this.discoveryRadius = discoveryRadius;
        this.hiddenUntilDiscovered = discoverable
                && hiddenUntilDiscovered;
        this.discoverable = discoverable;
        this.requiresRegionUnlock = requiresRegionUnlock;
        this.source = source == null
                ? LostTalesMapMarkerSource.QUEST_DYNAMIC : source;
        this.hasWaystone = hasWaystone;
        this.waystoneStructureType = waystoneStructureType == null
                ? "" : waystoneStructureType.trim().toLowerCase();
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


    public String getLotrWaypointId() {
        return LostTalesMapMarkerIdResolver.resolveLotrWaypointId(this.id);
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

    public boolean hasExplicitY() {
        return !LostTalesMapMarkerHeightResolver.isAutomatic(this.y);
    }

    public double getEffectiveY(World world) {
        return LostTalesMapMarkerHeightResolver.resolve(
                world, this.dimensionId,
                this.x, this.y, this.z);
    }

    public double getEffectiveY(World world, double fallbackY) {
        return LostTalesMapMarkerHeightResolver.resolveOr(
                world, this.dimensionId,
                this.x, this.y, this.z, fallbackY);
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

    public boolean requiresRegionUnlock() {
        return requiresRegionUnlock;
    }

    public LostTalesMapMarkerSource getSource() {
        return this.source;
    }

    public boolean hasWaystone() {
        return this.hasWaystone;
    }

    public String getWaystoneStructureType() {
        return this.waystoneStructureType;
    }

    public String getShortDescription() {
        return id + " (" + name + ", dim " + dimensionId + " @ " + format(x) + ", " + format(y) + ", " + format(z) + ", discovery " + format(discoveryRadius) + ")";
    }

    private static String format(double value) {
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.01D ? String.valueOf(rounded) : String.valueOf(value);
    }
}
