package com.ninuna.losttales.mapmarker;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.World;

/**
 * Immutable authoritative marker record. Callers replace records through
 * {@link LostTalesMapMarkerWorldData}; this prevents mutations that bypass
 * markDirty, revision checks, or spatial-index rebuilding.
 */
public final class LostTalesMapMarkerRecord {
    public static final int MAX_ID_LENGTH = 256;
    public static final int MAX_NAME_LENGTH = 256;
    public static final int MAX_TEXT_LENGTH = 1024;
    public static final int MAX_STRUCTURE_ID_LENGTH = 128;
    public static final int MAX_SHARED_PLAYERS = 256;
    public static final double MAX_ABSOLUTE_COORDINATE = 30000000.0D;
    public static final double MAX_RADIUS = 1000000.0D;

    private final String id;
    private final LostTalesMapMarkerSource source;
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
    private final boolean requiresRegionUnlock;
    private final boolean hasWaystone;
    private final String waystoneStructureType;
    private final UUID ownerPlayerId;
    private final LostTalesMapMarkerVisibility visibility;
    private final Set<UUID> sharedPlayerIds;
    private final boolean active;
    private final LostTalesWaystoneGenerationState generationState;
    private final String generationMessage;
    private final boolean linked;
    private final int linkedDimensionId;
    private final int linkedX;
    private final int linkedY;
    private final int linkedZ;
    private final UUID linkToken;
    private final long revision;

    private LostTalesMapMarkerRecord(Builder builder) {
        this.id = requireText(builder.id, "marker id", MAX_ID_LENGTH);
        if (this.id.indexOf(':') <= 0) {
            throw new IllegalArgumentException("marker id must be namespaced");
        }
        this.source = builder.source == null
                ? LostTalesMapMarkerSource.QUEST_DYNAMIC : builder.source;
        this.name = requireText(builder.name, "marker name", MAX_NAME_LENGTH);
        this.iconName = bounded(builder.iconName, MAX_NAME_LENGTH, "undiscovered");
        this.colorName = bounded(builder.colorName, MAX_NAME_LENGTH, "white");
        this.categoryName = bounded(
                builder.categoryName, MAX_NAME_LENGTH,
                LostTalesMapMarkerDefinition.CATEGORY_DEFAULT);
        this.description = bounded(builder.description, MAX_TEXT_LENGTH, "");
        this.hasFastTravel = builder.hasFastTravel;
        this.fastTravelWaypointCode = bounded(
                builder.fastTravelWaypointCode, MAX_NAME_LENGTH, "");
        this.dimensionId = builder.dimensionId;
        this.x = requireCoordinate(builder.x, "x");
        this.y = requireCoordinate(builder.y, "y");
        this.z = requireCoordinate(builder.z, "z");
        this.compassFadeInRadius = requireRadius(
                builder.compassFadeInRadius, "compass fade radius");
        this.discoveryRadius = requireRadius(
                builder.discoveryRadius, "discovery radius");
        this.hiddenUntilDiscovered =
                builder.discoverable && builder.hiddenUntilDiscovered;
        this.discoverable = builder.discoverable;
        this.requiresRegionUnlock = builder.requiresRegionUnlock;
        this.hasWaystone = builder.hasWaystone;
        this.waystoneStructureType = normalizeStructureType(
                builder.waystoneStructureType, builder.hasWaystone);
        this.ownerPlayerId = builder.ownerPlayerId;
        if (this.source == LostTalesMapMarkerSource.PLAYER_CREATED
                && this.ownerPlayerId == null) {
            throw new IllegalArgumentException(
                    "player-created marker must have an owner");
        }
        this.visibility = builder.visibility == null
                ? LostTalesMapMarkerVisibility.PRIVATE : builder.visibility;
        LinkedHashSet<UUID> shared = new LinkedHashSet<UUID>();
        if (builder.sharedPlayerIds != null) {
            for (UUID playerId : builder.sharedPlayerIds) {
                if (playerId != null && !playerId.equals(this.ownerPlayerId)) {
                    shared.add(playerId);
                }
                if (shared.size() >= MAX_SHARED_PLAYERS) {
                    break;
                }
            }
        }
        this.sharedPlayerIds = Collections.unmodifiableSet(shared);
        this.active = builder.active;
        this.generationState = builder.generationState == null
                ? (builder.hasWaystone
                    ? LostTalesWaystoneGenerationState.NOT_ATTEMPTED
                    : LostTalesWaystoneGenerationState.DISABLED)
                : builder.generationState;
        this.generationMessage = bounded(
                builder.generationMessage, MAX_NAME_LENGTH, "");
        this.linked = builder.linked;
        this.linkedDimensionId = builder.linkedDimensionId;
        this.linkedX = builder.linkedX;
        this.linkedY = builder.linkedY;
        this.linkedZ = builder.linkedZ;
        this.linkToken = builder.linkToken;
        if (this.linked && this.linkToken == null) {
            throw new IllegalArgumentException(
                    "linked marker must have a link token");
        }
        if (this.linked && !this.hasWaystone) {
            throw new IllegalArgumentException(
                    "marker without waystone intent cannot have a live link");
        }
        this.revision = Math.max(1L, builder.revision);
    }

    public static LostTalesMapMarkerRecord fromDefinition(
            LostTalesMapMarkerDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        return builder(definition.getId(), definition.getSource())
                .name(definition.getName())
                .iconName(definition.getIconName())
                .colorName(definition.getColorName())
                .categoryName(definition.getCategoryName())
                .description(definition.getDescription())
                .fastTravel(definition.hasFastTravel(),
                        definition.getFastTravelWaypointCode())
                .position(definition.getDimensionId(),
                        definition.getX(), definition.getY(), definition.getZ())
                .radii(definition.getCompassFadeInRadius(),
                        definition.getDiscoveryRadius())
                .discovery(definition.isHiddenUntilDiscovered(),
                        definition.isDiscoverable(),
                        definition.requiresRegionUnlock())
                .waystone(definition.hasWaystone(),
                        definition.getWaystoneStructureType())
                .visibility(LostTalesMapMarkerVisibility.PUBLIC)
                .active(true)
                .build();
    }

    public static LostTalesMapMarkerRecord createPlayerMarker(
            String id, String name, UUID ownerPlayerId,
            int dimensionId, double x, double y, double z,
            UUID linkToken) {
        if (ownerPlayerId == null) {
            throw new IllegalArgumentException(
                    "player marker owner must not be null");
        }
        return builder(id, LostTalesMapMarkerSource.PLAYER_CREATED)
                .name(name)
                .iconName("fort")
                .colorName("white")
                .categoryName("Waystone")
                .description("A player-placed waystone.")
                .fastTravel(true, "")
                .position(dimensionId, x, y, z)
                .radii(128.0D, 8.0D)
                .discovery(true, true, false)
                .waystone(true, "losttales:player_placed")
                .ownerPlayerId(ownerPlayerId)
                .visibility(LostTalesMapMarkerVisibility.PRIVATE)
                .active(true)
                .generationState(LostTalesWaystoneGenerationState.PLACED, "")
                .link(dimensionId, (int)Math.floor(x), (int)Math.floor(y),
                        (int)Math.floor(z), linkToken)
                .build();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public LostTalesMapMarkerRecord withLink(
            int dimensionId, int x, int y, int z, UUID token) {
        return toBuilder()
                .position(dimensionId, x, y, z)
                .link(dimensionId, x, y, z, token)
                .generationState(LostTalesWaystoneGenerationState.PLACED, "")
                .active(true)
                .revision(this.revision + 1L)
                .build();
    }

    public LostTalesMapMarkerRecord withRemoved(String reason) {
        return toBuilder()
                .active(false)
                .clearLink()
                .generationState(
                        LostTalesWaystoneGenerationState.REMOVED, reason)
                .revision(this.revision + 1L)
                .build();
    }

    public LostTalesMapMarkerRecord withGenerationState(
            LostTalesWaystoneGenerationState state, String message) {
        return toBuilder()
                .generationState(state, message)
                .revision(this.revision + 1L)
                .build();
    }

    public LostTalesMapMarkerRecord reconcilePresetDefinition(
            LostTalesMapMarkerDefinition definition) {
        if (definition == null || !this.id.equals(definition.getId())
                || this.source != definition.getSource()
                || this.source == LostTalesMapMarkerSource.PLAYER_CREATED
                || this.source == LostTalesMapMarkerSource.QUEST_DYNAMIC
                || !this.active || this.linked) {
            return this;
        }
        LostTalesWaystoneGenerationState desiredState =
                definition.hasWaystone()
                        ? LostTalesWaystoneGenerationState.NOT_ATTEMPTED
                        : LostTalesWaystoneGenerationState.DISABLED;
        boolean changed = this.dimensionId
                        != definition.getDimensionId()
                || Double.doubleToLongBits(this.x)
                        != Double.doubleToLongBits(definition.getX())
                || Double.doubleToLongBits(this.y)
                        != Double.doubleToLongBits(definition.getY())
                || Double.doubleToLongBits(this.z)
                        != Double.doubleToLongBits(definition.getZ())
                || this.hasWaystone != definition.hasWaystone()
                || !this.waystoneStructureType.equals(
                        definition.getWaystoneStructureType())
                || this.generationState != desiredState;
        if (!changed) {
            return this;
        }
        return toBuilder()
                .position(definition.getDimensionId(),
                        definition.getX(), definition.getY(),
                        definition.getZ())
                .waystone(definition.hasWaystone(),
                        definition.getWaystoneStructureType())
                .generationState(desiredState, "preset_definition_updated")
                .revision(this.revision + 1L)
                .build();
    }

    public LostTalesMapMarkerDefinition toDefinition() {
        return new LostTalesMapMarkerDefinition(
                this.id, this.name, this.iconName, this.colorName,
                this.categoryName, this.description,
                this.hasFastTravel, this.fastTravelWaypointCode,
                this.dimensionId, this.x, this.y, this.z,
                this.compassFadeInRadius, this.discoveryRadius,
                this.hiddenUntilDiscovered, this.discoverable,
                this.requiresRegionUnlock, this.source,
                this.hasWaystone, this.waystoneStructureType);
    }

    public LostTalesMapMarkerRecord withSettings(
            String name, String colorName, boolean hasFastTravel,
            double discoveryRadius,
            LostTalesMapMarkerVisibility visibility) {
        return toBuilder()
                .name(name)
                .colorName(colorName)
                .fastTravel(hasFastTravel, this.fastTravelWaypointCode)
                .radii(this.compassFadeInRadius, discoveryRadius)
                .visibility(visibility)
                .revision(this.revision + 1L)
                .build();
    }

    public LostTalesMapMarkerRecord withEditableSettings(
            LostTalesMapMarkerEditableSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException(
                    "editable marker settings must not be null");
        }
        return toBuilder()
                .name(settings.getName())
                .iconName(settings.getIconName())
                .colorName(settings.getColorName())
                .categoryName(settings.getCategoryName())
                .description(settings.getDescription())
                .fastTravel(settings.hasFastTravel(),
                        settings.getFastTravelWaypointCode())
                .position(settings.getDimensionId(),
                        settings.getX(), settings.getY(),
                        settings.getZ())
                .radii(settings.getCompassFadeInRadius(),
                        settings.getDiscoveryRadius())
                .discovery(settings.isHiddenUntilDiscovered(),
                        settings.isDiscoverable(),
                        settings.requiresRegionUnlock())
                .waystone(settings.hasWaystone(),
                        settings.getWaystoneStructureType())
                .visibility(settings.getVisibility())
                .revision(this.revision + 1L)
                .build();
    }

    public LostTalesMapMarkerRecord withSharedPlayers(
            Set<UUID> sharedPlayerIds,
            LostTalesMapMarkerVisibility visibility) {
        return toBuilder()
                .sharedPlayerIds(sharedPlayerIds)
                .visibility(visibility)
                .revision(this.revision + 1L)
                .build();
    }

    public String getId() { return this.id; }
    public LostTalesMapMarkerSource getSource() { return this.source; }
    public String getName() { return this.name; }
    public String getIconName() { return this.iconName; }
    public String getColorName() { return this.colorName; }
    public String getCategoryName() { return this.categoryName; }
    public String getDescription() { return this.description; }
    public boolean hasFastTravel() { return this.hasFastTravel; }
    public String getFastTravelWaypointCode() { return this.fastTravelWaypointCode; }
    public int getDimensionId() { return this.dimensionId; }
    public double getX() { return this.x; }
    public double getY() { return this.y; }
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
    public double getZ() { return this.z; }
    public double getCompassFadeInRadius() { return this.compassFadeInRadius; }
    public double getDiscoveryRadius() { return this.discoveryRadius; }
    public boolean isHiddenUntilDiscovered() { return this.hiddenUntilDiscovered; }
    public boolean isDiscoverable() { return this.discoverable; }
    public boolean requiresRegionUnlock() { return this.requiresRegionUnlock; }
    public boolean hasWaystone() { return this.hasWaystone; }
    public String getWaystoneStructureType() { return this.waystoneStructureType; }
    public UUID getOwnerPlayerId() { return this.ownerPlayerId; }
    public LostTalesMapMarkerVisibility getVisibility() { return this.visibility; }
    public Set<UUID> getSharedPlayerIds() { return this.sharedPlayerIds; }
    public boolean isActive() { return this.active; }
    public LostTalesWaystoneGenerationState getGenerationState() { return this.generationState; }
    public String getGenerationMessage() { return this.generationMessage; }
    public boolean isLinked() { return this.linked; }
    public int getLinkedDimensionId() { return this.linkedDimensionId; }
    public int getLinkedX() { return this.linkedX; }
    public int getLinkedY() { return this.linkedY; }
    public int getLinkedZ() { return this.linkedZ; }
    public UUID getLinkToken() { return this.linkToken; }
    public long getRevision() { return this.revision; }

    public static Builder builder(String id, LostTalesMapMarkerSource source) {
        return new Builder(id, source);
    }

    private static String requireText(
            String value, String field, int maximumLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() == 0 || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is missing or too long");
        }
        return normalized;
    }

    private static String bounded(
            String value, int maximumLength, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() == 0) {
            return fallback;
        }
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException("marker text is too long");
        }
        return normalized;
    }

    private static double requireCoordinate(double value, String field) {
        if (!isFinite(value) || Math.abs(value) > MAX_ABSOLUTE_COORDINATE) {
            throw new IllegalArgumentException(
                    "marker " + field + " coordinate is invalid");
        }
        return value;
    }

    private static double requireRadius(double value, String field) {
        if (!isFinite(value) || value < 0.0D || value > MAX_RADIUS) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String normalizeStructureType(
            String value, boolean hasWaystone) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (!hasWaystone) {
            return "";
        }
        if (normalized.length() == 0
                || normalized.length() > MAX_STRUCTURE_ID_LENGTH
                || !normalized.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException(
                    "waystone structure id is invalid");
        }
        return normalized;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static final class Builder {
        private final String id;
        private final LostTalesMapMarkerSource source;
        private String name = "Map Marker";
        private String iconName = "undiscovered";
        private String colorName = "white";
        private String categoryName = LostTalesMapMarkerDefinition.CATEGORY_DEFAULT;
        private String description = "";
        private boolean hasFastTravel;
        private String fastTravelWaypointCode = "";
        private int dimensionId;
        private double x;
        private double y = 64.0D;
        private double z;
        private double compassFadeInRadius = 128.0D;
        private double discoveryRadius = 8.0D;
        private boolean hiddenUntilDiscovered;
        private boolean discoverable = true;
        private boolean requiresRegionUnlock;
        private boolean hasWaystone;
        private String waystoneStructureType = "";
        private UUID ownerPlayerId;
        private LostTalesMapMarkerVisibility visibility =
                LostTalesMapMarkerVisibility.PRIVATE;
        private Set<UUID> sharedPlayerIds = Collections.emptySet();
        private boolean active = true;
        private LostTalesWaystoneGenerationState generationState;
        private String generationMessage = "";
        private boolean linked;
        private int linkedDimensionId;
        private int linkedX;
        private int linkedY;
        private int linkedZ;
        private UUID linkToken;
        private long revision = 1L;

        private Builder(String id, LostTalesMapMarkerSource source) {
            this.id = id;
            this.source = source;
        }

        private Builder(LostTalesMapMarkerRecord record) {
            this.id = record.id;
            this.source = record.source;
            this.name = record.name;
            this.iconName = record.iconName;
            this.colorName = record.colorName;
            this.categoryName = record.categoryName;
            this.description = record.description;
            this.hasFastTravel = record.hasFastTravel;
            this.fastTravelWaypointCode = record.fastTravelWaypointCode;
            this.dimensionId = record.dimensionId;
            this.x = record.x;
            this.y = record.y;
            this.z = record.z;
            this.compassFadeInRadius = record.compassFadeInRadius;
            this.discoveryRadius = record.discoveryRadius;
            this.hiddenUntilDiscovered = record.hiddenUntilDiscovered;
            this.discoverable = record.discoverable;
            this.requiresRegionUnlock = record.requiresRegionUnlock;
            this.hasWaystone = record.hasWaystone;
            this.waystoneStructureType = record.waystoneStructureType;
            this.ownerPlayerId = record.ownerPlayerId;
            this.visibility = record.visibility;
            this.sharedPlayerIds = record.sharedPlayerIds;
            this.active = record.active;
            this.generationState = record.generationState;
            this.generationMessage = record.generationMessage;
            this.linked = record.linked;
            this.linkedDimensionId = record.linkedDimensionId;
            this.linkedX = record.linkedX;
            this.linkedY = record.linkedY;
            this.linkedZ = record.linkedZ;
            this.linkToken = record.linkToken;
            this.revision = record.revision;
        }

        public Builder name(String value) { this.name = value; return this; }
        public Builder iconName(String value) { this.iconName = value; return this; }
        public Builder colorName(String value) { this.colorName = value; return this; }
        public Builder categoryName(String value) { this.categoryName = value; return this; }
        public Builder description(String value) { this.description = value; return this; }
        public Builder fastTravel(boolean enabled, String code) {
            this.hasFastTravel = enabled;
            this.fastTravelWaypointCode = code;
            return this;
        }
        public Builder position(int dimensionId, double x, double y, double z) {
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }
        public Builder radii(double compass, double discovery) {
            this.compassFadeInRadius = compass;
            this.discoveryRadius = discovery;
            return this;
        }
        public Builder discovery(boolean hidden, boolean discoverable,
                                 boolean requiresRegion) {
            this.hiddenUntilDiscovered = hidden;
            this.discoverable = discoverable;
            this.requiresRegionUnlock = requiresRegion;
            return this;
        }
        public Builder waystone(boolean enabled, String structureType) {
            this.hasWaystone = enabled;
            this.waystoneStructureType = structureType;
            return this;
        }
        public Builder ownerPlayerId(UUID value) { this.ownerPlayerId = value; return this; }
        public Builder visibility(LostTalesMapMarkerVisibility value) { this.visibility = value; return this; }
        public Builder sharedPlayerIds(Set<UUID> value) { this.sharedPlayerIds = value; return this; }
        public Builder active(boolean value) { this.active = value; return this; }
        public Builder generationState(
                LostTalesWaystoneGenerationState value, String message) {
            this.generationState = value;
            this.generationMessage = message;
            return this;
        }
        public Builder link(int dimensionId, int x, int y, int z, UUID token) {
            this.linked = true;
            this.linkedDimensionId = dimensionId;
            this.linkedX = x;
            this.linkedY = y;
            this.linkedZ = z;
            this.linkToken = token;
            return this;
        }
        public Builder clearLink() {
            this.linked = false;
            this.linkToken = null;
            return this;
        }
        public Builder revision(long value) { this.revision = value; return this; }
        public LostTalesMapMarkerRecord build() {
            return new LostTalesMapMarkerRecord(this);
        }
    }
}
