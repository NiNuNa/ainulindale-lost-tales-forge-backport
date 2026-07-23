package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerHeightResolver;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerIdResolver;
import net.minecraft.world.World;

/**
 * Simple client-side map marker description used by the 1.7.10 compass HUD.
 *
 * The NeoForge branch loads markers through datapacks/codecs. Minecraft 1.7.10
 * does not have that system, so the backport keeps a small immutable data class
 * loaded from JSON files under assets/losttales/map_markers/.
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
        this(id, name, iconName, colorName, categoryName, "",
                hasFastTravel, dimensionId, x, y, z,
                compassFadeInRadius, discoveryRadius,
                hiddenUntilDiscovered, discoverable);
    }

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, String categoryName, boolean hasFastTravel, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable, boolean requiresRegionUnlock) {
        this(id, name, iconName, colorName, categoryName, "",
                hasFastTravel, dimensionId, x, y, z,
                compassFadeInRadius, discoveryRadius,
                hiddenUntilDiscovered, discoverable,
                requiresRegionUnlock);
    }

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, String categoryName, String description, boolean hasFastTravel, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable) {
        this(id, name, iconName, colorName, categoryName, description,
                hasFastTravel, dimensionId,
                x, y, z, compassFadeInRadius, discoveryRadius,
                hiddenUntilDiscovered, discoverable, false);
    }

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, String categoryName, String description, boolean hasFastTravel, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable, boolean requiresRegionUnlock) {
        this(id, name, iconName, colorName, categoryName, description,
                hasFastTravel, dimensionId,
                x, y, z, compassFadeInRadius, discoveryRadius,
                hiddenUntilDiscovered, discoverable,
                requiresRegionUnlock, false);
    }

    public LostTalesMapMarkerData(String id, String name, String iconName,
                                  String colorName, String categoryName,
                                  String description,
                                  boolean hasFastTravel,
                                  int dimensionId,
                                  double x, double y, double z,
                                  double compassFadeInRadius,
                                  double discoveryRadius,
                                  boolean hiddenUntilDiscovered,
                                  boolean discoverable,
                                  boolean requiresRegionUnlock,
                                  boolean hasWaystone) {
        this.id = id;
        this.name = name;
        this.iconName = iconName;
        this.colorName = colorName;
        this.categoryName = categoryName == null || categoryName.length() == 0 ? (hasFastTravel ? CATEGORY_POINT_OF_INTEREST : CATEGORY_DEFAULT) : categoryName;
        this.description = normalizeDescription(description);
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
        this.hasWaystone = hasWaystone;
    }


    private static String normalizeDescription(String description) {
        return description == null ? "" : description.trim();
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


    public String getLotrWaypointId() {
        return LostTalesMapMarkerIdResolver.resolveLotrWaypointId(this.id);
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
        return this.z;
    }

    public double getCompassFadeInRadius() {
        return this.compassFadeInRadius;
    }


    public double getDiscoveryRadius() {
        return this.discoveryRadius;
    }


    /**
     * If true, proximity discovery is required before full-map presentation.
     * This is independent of {@link #requiresRegionUnlock()} and is ignored
     * when {@link #isDiscoverable()} is false.
     */
    public boolean isHiddenUntilDiscovered() {
        return this.hiddenUntilDiscovered;
    }

    public boolean isDiscoverable() {
        return this.discoverable;
    }

    public boolean requiresRegionUnlock() {
        return this.requiresRegionUnlock;
    }

    public boolean hasWaystone() {
        return this.hasWaystone;
    }

    /**
     * A waystone's stored X/Z identify its block. The compass points at the
     * visible center of that block, while non-waystone markers retain their
     * exact configured coordinates.
     */
    public double getCompassTargetX() {
        return this.hasWaystone ? this.x + 0.5D : this.x;
    }

    public double getCompassTargetZ() {
        return this.hasWaystone ? this.z + 0.5D : this.z;
    }

}
