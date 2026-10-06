package com.ninuna.losttales.mapmarker;

/**
 * A map marker as the server knows it and sends it: bundled, a player's
 * waystone, or a quest's. A name or category left empty is drawn in each
 * player's language ({@link LostTalesMapMarkerNames}): a marker with no
 * name of its own by what it is called after
 * ({@link LostTalesMapMarkerNamedAfter}), one with no category by the
 * word for its kind.
 */
public final class LostTalesMapMarkerDefinition {
    public static final double AUTOMATIC_Y =
            LostTalesMapMarkerHeightResolver.AUTOMATIC_Y;
    public static final int MIN_PRIORITY = -1000000;
    public static final int MAX_PRIORITY = 1000000;

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
    /** Screen-space overlap priority; larger values win. */
    private final int priority;
    /** What the marker is called after while its name is empty ({@link LostTalesMapMarkerNamedAfter}). */
    private final String namedAfter;

    public LostTalesMapMarkerDefinition(String id, String name, String iconName, String colorName, int dimensionId, double x, double y, double z, boolean hiddenUntilDiscovered) {
        this(id, name, iconName, colorName, "", "", false,
                dimensionId, x, y, z, 128.0D, 8.0D,
                hiddenUntilDiscovered, hiddenUntilDiscovered, false,
                LostTalesMapMarkerSource.QUEST_DYNAMIC, false, "", 0);
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
                                        String waystoneStructureType,
                                        int priority) {
        this(id, name, iconName, colorName, categoryName, description,
                hasFastTravel, dimensionId, x, y, z, compassFadeInRadius,
                discoveryRadius, hiddenUntilDiscovered, discoverable,
                requiresRegionUnlock, source, hasWaystone,
                waystoneStructureType, priority, "");
    }

    /** A marker called after {@code namedAfter} while {@code name} is empty. */
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
                                        String waystoneStructureType,
                                        int priority, String namedAfter) {
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException("marker priority is out of range");
        }
        this.id = id;
        this.name = name;
        this.iconName = iconName;
        this.colorName = colorName;
        this.categoryName = categoryName == null ? "" : categoryName.trim();
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
        this.priority = priority;
        this.namedAfter = LostTalesMapMarkerNamedAfter.isValid(namedAfter)
                && namedAfter != null ? namedAfter : "";
    }

    public String getId() {
        return id;
    }

    /** The marker's own name; empty for one called after something ({@link #getNamedAfter}). */
    public String getName() {
        return name;
    }

    /** What the marker is called after while its name is empty; empty for nothing. */
    public String getNamedAfter() {
        return namedAfter;
    }

    public String getIconName() {
        return iconName;
    }

    public String getColorName() {
        return colorName;
    }

    /** The category a player or operator gave it; empty for the word its kind is drawn with. */
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

    public int getPriority() {
        return this.priority;
    }

}
