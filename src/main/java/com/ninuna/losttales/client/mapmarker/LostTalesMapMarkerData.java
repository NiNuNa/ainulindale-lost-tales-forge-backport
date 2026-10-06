package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerHeightResolver;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerIdResolver;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNamedAfter;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNames;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import net.minecraft.world.World;

/**
 * A map marker as the client draws it: from the marker files under
 * {@code assets/losttales/map_markers/}, from the server, or made by a
 * screen for a waypoint or a fellowship's mark.
 *
 * <p>Its name and description are read in the game's language: the lang
 * line its id names ({@link LostTalesMapMarkerNames}) where one exists,
 * else the words it was given; a marker with no name of its own by what
 * it is called after ({@link LostTalesMapMarkerNamedAfter}): a waystone
 * its placer has not named reads {@code Nils's Waystone}.
 * {@link #getGivenName} keeps the words given, which a search and a shared
 * name are matched against as well.</p>
 */
public final class LostTalesMapMarkerData {
    private final String id;
    /** The words the file or the server gave the marker. */
    private final String name;
    /** The lang key that names it in the game's language; empty where its words are its own. */
    private final String nameKey;
    /** The lang key that describes it; empty where its description is its own. */
    private final String descriptionKey;
    /** What it is called after while its name is empty. */
    private final String namedAfter;
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
    private final int priority;
    private final LostTalesMapMarkerSource source;

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius) {
        this(id, name, iconName, colorName, "", "", false,
                dimensionId, x, y, z, compassFadeInRadius, discoveryRadius,
                false, false, false, false, 0,
                LostTalesMapMarkerSource.CUSTOM_PRESET);
    }

    public LostTalesMapMarkerData(String id, String name, String iconName, String colorName, String categoryName, String description, boolean hasFastTravel, int dimensionId, double x, double y, double z, double compassFadeInRadius, double discoveryRadius, boolean hiddenUntilDiscovered, boolean discoverable) {
        this(id, name, iconName, colorName, categoryName, description,
                hasFastTravel, dimensionId,
                x, y, z, compassFadeInRadius, discoveryRadius,
                hiddenUntilDiscovered, discoverable, false, false, 0,
                LostTalesMapMarkerSource.CUSTOM_PRESET);
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
                                  boolean hasWaystone,
                                  int priority,
                                  LostTalesMapMarkerSource source) {
        this(id, name, iconName, colorName, categoryName, description,
                hasFastTravel, dimensionId, x, y, z, compassFadeInRadius,
                discoveryRadius, hiddenUntilDiscovered, discoverable,
                requiresRegionUnlock, hasWaystone, priority, source,
                LostTalesMapMarkerNames.nameKey(id),
                LostTalesMapMarkerNames.descriptionKey(id));
    }

    /**
     * A marker whose name and description are read under {@code nameKey}
     * and {@code descriptionKey}; an empty key keeps the words as given,
     * as for a name an operator wrote.
     */
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
                                  boolean hasWaystone,
                                  int priority,
                                  LostTalesMapMarkerSource source,
                                  String nameKey, String descriptionKey) {
        this(id, name, iconName, colorName, categoryName, description,
                hasFastTravel, dimensionId, x, y, z, compassFadeInRadius,
                discoveryRadius, hiddenUntilDiscovered, discoverable,
                requiresRegionUnlock, hasWaystone, priority, source, nameKey,
                descriptionKey, "");
    }

    /** A marker the server sends, called after {@code namedAfter} while its name is empty. */
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
                                  boolean hasWaystone,
                                  int priority,
                                  LostTalesMapMarkerSource source,
                                  String nameKey, String descriptionKey,
                                  String namedAfter) {
        if (priority < LostTalesMapMarkerDefinition.MIN_PRIORITY
                || priority > LostTalesMapMarkerDefinition.MAX_PRIORITY) {
            throw new IllegalArgumentException("marker priority is out of range");
        }
        this.id = id;
        this.name = name == null ? "" : name;
        this.nameKey = nameKey == null ? "" : nameKey;
        this.descriptionKey = descriptionKey == null ? "" : descriptionKey;
        this.namedAfter = LostTalesMapMarkerNamedAfter.isValid(namedAfter)
                && namedAfter != null ? namedAfter : "";
        this.iconName = iconName;
        this.colorName = colorName;
        this.categoryName = categoryName == null ? "" : categoryName.trim();
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
        this.priority = priority;
        this.source = source == null
                ? LostTalesMapMarkerSource.CUSTOM_PRESET : source;
    }


    private static String normalizeDescription(String description) {
        return description == null ? "" : description.trim();
    }

    public String getId() {
        return this.id;
    }

    /**
     * The marker's name in the game's language: the lang line where one
     * names it, else its words, else what it is called after, else its id.
     */
    public String getName() {
        if (this.name.trim().length() == 0) {
            String called = LostTalesMapMarkerNamedAfter.shownName(this.namedAfter);
            return called.length() > 0 ? called : this.id;
        }
        return LostTalesMapMarkerNames.translated(this.nameKey, this.name);
    }

    /** The words the file or the server gave the marker, whatever the game's language. */
    public String getGivenName() {
        return this.name;
    }

    public String getIconName() {
        return this.iconName;
    }

    public String getColorName() {
        return this.colorName;
    }

    /** The category a player or operator gave it; empty for the word its kind is drawn with. */
    public String getCategoryName() {
        return this.categoryName;
    }

    /** What the marker is called after while its name is empty; empty for nothing. */
    public String getNamedAfter() {
        return this.namedAfter;
    }

    /**
     * The marker's description in the game's language; a waystone a player
     * placed and left undescribed reads the words every such waystone has;
     * empty where it has none.
     */
    public String getDescription() {
        String description = LostTalesMapMarkerNames.translated(
                this.descriptionKey, this.description);
        return description.length() > 0 ? description
                : LostTalesMapMarkerNames.defaultDescription(this.source);
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

    public int getPriority() {
        return this.priority;
    }

    public LostTalesMapMarkerSource getSource() {
        return this.source;
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
