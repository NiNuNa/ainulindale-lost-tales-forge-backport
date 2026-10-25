package com.ninuna.losttales.mapmarker;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.World;

/**
 * Immutable authoritative marker record. Callers replace records through
 * {@link LostTalesMapMarkerWorldData}; this prevents mutations that bypass
 * markDirty, revision checks, or spatial-index rebuilding.
 *
 * <p>A record keeps only words a player or an operator gave it. A waystone
 * its placer has not named keeps an empty name and is called after the
 * placing character, kept by its id ({@link LostTalesMapMarkerNamedAfter},
 * WAY-01), which every map is sent by the character's name as it stands
 * ({@link #toDefinition(LostTalesMapMarkerNamedAfter.Names)}); an empty
 * category or description is drawn with the words for its kind, in each
 * player's language.</p>
 *
 * <p>A placed waystone belongs to the character that placed it
 * ({@link #getOwnerCharacterId}), never to its account (WAY-08). Its
 * access is public or not; a waystone that is not public reads as shared
 * while it has any share and private without one ({@link #access}), so
 * sharing and unsharing keep no access of their own (WAY-14).</p>
 *
 * <p>A record keeps which settings were saved on its page with another
 * value than it had ({@link #getSavedSettings}); a reseed keeps those and
 * takes every other from the marker's file (WAY-16).</p>
 */
public final class LostTalesMapMarkerRecord {
    public static final int MAX_ID_LENGTH = 256;
    public static final int MAX_NAME_LENGTH = 256;
    public static final int MAX_TEXT_LENGTH = 1024;
    public static final int MAX_STRUCTURE_ID_LENGTH = 128;
    public static final int MAX_SHARED_CHARACTERS = 256;
    public static final int MAX_SHARED_FELLOWSHIPS = 64;
    public static final double MAX_ABSOLUTE_COORDINATE = 30000000.0D;
    public static final double MAX_RADIUS = 1000000.0D;
    /**
     * The widest discovery radius a placed waystone keeps, in blocks: as
     * far as the discovery scan reaches, 64 chunks (WAY-15). Bundled
     * markers keep theirs, up to {@link #MAX_RADIUS}.
     */
    public static final double MAX_PLACED_DISCOVERY_RADIUS = 1024.0D;

    private final String id;
    private final LostTalesMapMarkerSource source;
    private final String name;
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
    private final String waystoneStructureType;
    private final int priority;
    private final UUID ownerCharacterId;
    private final LostTalesMapMarkerVisibility visibility;
    private final Set<UUID> sharedCharacterIds;
    private final Set<UUID> sharedFellowshipIds;
    private final LostTalesWaystoneGenerationState generationState;
    private final boolean linked;
    private final int linkedDimensionId;
    private final int linkedX;
    private final int linkedY;
    private final int linkedZ;
    private final UUID linkToken;
    private final long revision;
    private final Set<LostTalesMapMarkerSetting> savedSettings;

    private LostTalesMapMarkerRecord(Builder builder) {
        this.id = requireText(builder.id, "marker id", MAX_ID_LENGTH);
        if (this.id.indexOf(':') <= 0) {
            throw new IllegalArgumentException("marker id must be namespaced");
        }
        this.source = builder.source == null
                ? LostTalesMapMarkerSource.QUEST_DYNAMIC : builder.source;
        if (!LostTalesMapMarkerNamedAfter.isKept(builder.namedAfter)) {
            throw new IllegalArgumentException("marker is named after nothing it can be");
        }
        this.namedAfter = builder.namedAfter == null ? "" : builder.namedAfter;
        this.name = this.namedAfter.length() > 0
                ? bounded(builder.name, MAX_NAME_LENGTH, "")
                : requireText(builder.name, "marker name", MAX_NAME_LENGTH);
        this.iconName = bounded(builder.iconName, MAX_NAME_LENGTH, "undiscovered");
        this.colorName = bounded(builder.colorName, MAX_NAME_LENGTH, "white");
        this.categoryName = bounded(
                builder.categoryName, MAX_NAME_LENGTH, "");
        this.description = bounded(builder.description, MAX_TEXT_LENGTH, "");
        this.hasFastTravel = builder.hasFastTravel;
        this.dimensionId = builder.dimensionId;
        this.x = requireCoordinate(builder.x, "x");
        this.y = requireCoordinate(builder.y, "y");
        this.z = requireCoordinate(builder.z, "z");
        this.compassFadeInRadius = requireRadius(
                builder.compassFadeInRadius, "compass fade radius",
                MAX_RADIUS);
        this.discoveryRadius = requireRadius(
                builder.discoveryRadius, "discovery radius",
                maxDiscoveryRadius(this.source));
        this.hiddenUntilDiscovered =
                builder.discoverable && builder.hiddenUntilDiscovered;
        this.discoverable = builder.discoverable;
        this.requiresRegionUnlock = builder.requiresRegionUnlock;
        this.hasWaystone = builder.hasWaystone;
        this.waystoneStructureType = normalizeStructureType(
                builder.waystoneStructureType, builder.hasWaystone);
        if (builder.priority < LostTalesMapMarkerDefinition.MIN_PRIORITY
                || builder.priority > LostTalesMapMarkerDefinition.MAX_PRIORITY) {
            throw new IllegalArgumentException("marker priority is out of range");
        }
        this.priority = builder.priority;
        this.ownerCharacterId = builder.ownerCharacterId;
        if (this.source == LostTalesMapMarkerSource.PLAYER_CREATED
                && this.ownerCharacterId == null) {
            throw new IllegalArgumentException(
                    "player-created marker must have an owner");
        }
        LinkedHashSet<UUID> shared = new LinkedHashSet<UUID>();
        if (builder.sharedCharacterIds != null) {
            for (UUID characterId : builder.sharedCharacterIds) {
                if (characterId != null) {
                    shared.add(characterId);
                }
                if (shared.size() >= MAX_SHARED_CHARACTERS) {
                    break;
                }
            }
        }
        this.sharedCharacterIds = Collections.unmodifiableSet(shared);
        LinkedHashSet<UUID> sharedFellowships =
                new LinkedHashSet<UUID>();
        if (builder.sharedFellowshipIds != null) {
            for (UUID fellowshipId : builder.sharedFellowshipIds) {
                if (fellowshipId != null) {
                    sharedFellowships.add(fellowshipId);
                }
                if (sharedFellowships.size()
                        >= MAX_SHARED_FELLOWSHIPS) {
                    break;
                }
            }
        }
        this.sharedFellowshipIds =
                Collections.unmodifiableSet(sharedFellowships);
        this.visibility = access(
                builder.visibility == LostTalesMapMarkerVisibility.PUBLIC,
                !shared.isEmpty() || !sharedFellowships.isEmpty());
        this.generationState = builder.generationState == null
                ? (builder.hasWaystone
                    ? LostTalesWaystoneGenerationState.NOT_ATTEMPTED
                    : LostTalesWaystoneGenerationState.DISABLED)
                : builder.generationState;
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
        EnumSet<LostTalesMapMarkerSetting> saved =
                EnumSet.noneOf(LostTalesMapMarkerSetting.class);
        if (builder.savedSettings != null) {
            for (LostTalesMapMarkerSetting setting : builder.savedSettings) {
                if (setting != null) {
                    saved.add(setting);
                }
            }
        }
        this.savedSettings = Collections.unmodifiableSet(saved);
    }

    public static LostTalesMapMarkerRecord fromDefinition(
            LostTalesMapMarkerDefinition definition) {
        return fromDefinition(definition,
                definition == null ? null : definition.getId());
    }

    static LostTalesMapMarkerRecord fromDefinition(
            LostTalesMapMarkerDefinition definition,
            String persistedMarkerId) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        return builder(persistedMarkerId, definition.getSource())
                .name(definition.getName())
                .namedAfter(definition.getNamedAfter())
                .iconName(definition.getIconName())
                .colorName(definition.getColorName())
                .categoryName(definition.getCategoryName())
                .description(definition.getDescription())
                .fastTravel(definition.hasFastTravel())
                .position(definition.getDimensionId(),
                        definition.getX(), definition.getY(), definition.getZ())
                .radii(definition.getCompassFadeInRadius(),
                        definition.getDiscoveryRadius())
                .discovery(definition.isHiddenUntilDiscovered(),
                        definition.isDiscoverable(),
                        definition.requiresRegionUnlock())
                .waystone(definition.hasWaystone(),
                        definition.getWaystoneStructureType())
                .priority(definition.getPriority())
                .visibility(LostTalesMapMarkerVisibility.PUBLIC)
                .build();
    }

    /**
     * A waystone a character just placed: unnamed, called after the
     * character that placed it ({@code Nils's Waystone}), which owns it,
     * with no category or description of its own, until its owner gives it
     * some. Its icon is the shack, one of the icons its page offers.
     */
    public static LostTalesMapMarkerRecord createPlayerMarker(
            String id, UUID placerCharacterId,
            int dimensionId, double x, double y, double z,
            UUID linkToken) {
        if (placerCharacterId == null) {
            throw new IllegalArgumentException(
                    "player marker owner must not be null");
        }
        return builder(id, LostTalesMapMarkerSource.PLAYER_CREATED)
                .name("")
                .namedAfter(LostTalesMapMarkerNamedAfter.character(
                        placerCharacterId))
                .iconName("shack")
                .colorName("white")
                .categoryName("")
                .description("")
                .fastTravel(true)
                .position(dimensionId, x, y, z)
                .radii(128.0D, 8.0D)
                .discovery(true, true, false)
                .waystone(true, "losttales:player_placed")
                .ownerCharacterId(placerCharacterId)
                .visibility(LostTalesMapMarkerVisibility.PRIVATE)
                .generationState(LostTalesWaystoneGenerationState.PLACED)
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
                .generationState(LostTalesWaystoneGenerationState.PLACED)
                .revision(this.revision + 1L)
                .build();
    }

    public LostTalesMapMarkerRecord withGenerationState(
            LostTalesWaystoneGenerationState state) {
        return toBuilder()
                .generationState(state)
                .revision(this.revision + 1L)
                .build();
    }

    /**
     * Whether the record is linked to the waystone block at that place in
     * that world, by the same token the block holds: the one check every
     * use of the block passes.
     */
    public boolean isLinkedTo(int dimensionId, int x, int y, int z,
                              UUID token) {
        return this.linked && this.hasWaystone && token != null
                && token.equals(this.linkToken)
                && this.linkedDimensionId == dimensionId
                && this.linkedX == x && this.linkedY == y
                && this.linkedZ == z;
    }

    /**
     * The marker as the server's own code reads it, called after what the
     * record keeps. What a player's game is sent goes through
     * {@link #toDefinition(LostTalesMapMarkerNamedAfter.Names)}.
     */
    public LostTalesMapMarkerDefinition toDefinition() {
        return toDefinition((LostTalesMapMarkerNamedAfter.Names)null);
    }

    /**
     * The marker as a player's game is sent it: an unnamed waystone called
     * after its placing character by the name {@code placers} gives it
     * ({@link LostTalesMapMarkerNamedAfter#shown}).
     */
    public LostTalesMapMarkerDefinition toDefinition(
            LostTalesMapMarkerNamedAfter.Names placers) {
        return new LostTalesMapMarkerDefinition(
                this.id, this.name, this.iconName, this.colorName,
                this.categoryName, this.description,
                this.hasFastTravel,
                this.dimensionId, this.x, this.y, this.z,
                this.compassFadeInRadius, this.discoveryRadius,
                this.hiddenUntilDiscovered, this.discoverable,
                this.requiresRegionUnlock, this.source,
                this.hasWaystone, this.waystoneStructureType,
                this.priority,
                LostTalesMapMarkerNamedAfter.shown(this.namedAfter, placers));
    }

    /**
     * The record with the settings its page saved. Each setting that comes
     * out with another value than it had joins the saved settings, which a
     * reseed keeps (WAY-16).
     */
    public LostTalesMapMarkerRecord withEditableSettings(
            LostTalesMapMarkerEditableSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException(
                    "editable marker settings must not be null");
        }
        LostTalesMapMarkerRecord edited = toBuilder()
                .name(settings.getName())
                .iconName(settings.getIconName())
                .colorName(settings.getColorName())
                .categoryName(settings.getCategoryName())
                .description(settings.getDescription())
                .fastTravel(settings.hasFastTravel())
                .radii(settings.getCompassFadeInRadius(),
                        settings.getDiscoveryRadius())
                .discovery(settings.isHiddenUntilDiscovered(),
                        settings.isDiscoverable(),
                        settings.requiresRegionUnlock())
                .priority(settings.getPriority())
                .visibility(settings.getVisibility())
                .revision(this.revision + 1L)
                .build();
        EnumSet<LostTalesMapMarkerSetting> saved =
                EnumSet.noneOf(LostTalesMapMarkerSetting.class);
        saved.addAll(this.savedSettings);
        saved.addAll(LostTalesMapMarkerSetting.changed(this, edited));
        return edited.toBuilder().savedSettings(saved).build();
    }

    /** The record shared with these characters; its access follows ({@link #access}). */
    public LostTalesMapMarkerRecord withSharedCharacters(
            Set<UUID> sharedCharacterIds) {
        return toBuilder()
                .sharedCharacterIds(sharedCharacterIds)
                .revision(this.revision + 1L)
                .build();
    }

    /** The record shared with these fellowships of ours; its access follows ({@link #access}). */
    public LostTalesMapMarkerRecord withSharedFellowships(
            Set<UUID> sharedFellowshipIds) {
        return toBuilder()
                .sharedFellowshipIds(sharedFellowshipIds)
                .revision(this.revision + 1L)
                .build();
    }

    /**
     * The record owned by another character, handed over by a manager.
     * Its shares and what it is called after stay as they are, so the
     * character it was called after keeps it only through a share.
     */
    public LostTalesMapMarkerRecord withOwner(UUID characterId) {
        if (characterId == null) {
            throw new IllegalArgumentException(
                    "a waystone is handed over to a character");
        }
        return toBuilder()
                .ownerCharacterId(characterId)
                .revision(this.revision + 1L)
                .build();
    }

    /**
     * The one rule for a record's access: public, else shared while it has
     * a share, else private.
     */
    public static LostTalesMapMarkerVisibility access(
            boolean open, boolean shared) {
        return open ? LostTalesMapMarkerVisibility.PUBLIC
                : shared ? LostTalesMapMarkerVisibility.SHARED
                : LostTalesMapMarkerVisibility.PRIVATE;
    }

    /**
     * The widest discovery radius a marker from {@code source} keeps:
     * {@link #MAX_PLACED_DISCOVERY_RADIUS} for a placed waystone,
     * {@link #MAX_RADIUS} for every other.
     */
    public static double maxDiscoveryRadius(
            LostTalesMapMarkerSource source) {
        return source == LostTalesMapMarkerSource.PLAYER_CREATED
                ? MAX_PLACED_DISCOVERY_RADIUS : MAX_RADIUS;
    }

    public String getId() { return this.id; }
    public LostTalesMapMarkerSource getSource() { return this.source; }
    /** The name a player or operator gave it; empty for one called after its placer. */
    public String getName() { return this.name; }
    /**
     * What it is called after while its name is empty, as the record keeps
     * it: nothing, or the placing character by its id
     * ({@link LostTalesMapMarkerNamedAfter#character}).
     */
    public String getNamedAfter() { return this.namedAfter; }

    /** Whether it is called after that character (WAY-01). */
    public boolean isCalledAfter(UUID characterId) {
        return characterId != null && characterId.equals(
                LostTalesMapMarkerNamedAfter.characterId(this.namedAfter));
    }

    /** The record without the share it gives the character, a newer revision; the record itself where it gives none. */
    public LostTalesMapMarkerRecord withoutSharedCharacter(UUID characterId) {
        if (characterId == null
                || !this.sharedCharacterIds.contains(characterId)) {
            return this;
        }
        Set<UUID> shared = new LinkedHashSet<UUID>(this.sharedCharacterIds);
        shared.remove(characterId);
        return withSharedCharacters(shared);
    }
    public String getIconName() { return this.iconName; }
    public String getColorName() { return this.colorName; }
    public String getCategoryName() { return this.categoryName; }
    public String getDescription() { return this.description; }
    public boolean hasFastTravel() { return this.hasFastTravel; }
    public int getDimensionId() { return this.dimensionId; }
    public double getX() { return this.x; }
    public double getY() { return this.y; }
    public boolean hasExplicitY() {
        return !LostTalesMapMarkerHeightResolver.isAutomatic(this.y);
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
    public int getPriority() { return this.priority; }
    /** The character that owns it; null for a marker nobody placed. */
    public UUID getOwnerCharacterId() { return this.ownerCharacterId; }
    /** Public, or not: shared while it has any share, else private. */
    public LostTalesMapMarkerVisibility getVisibility() { return this.visibility; }
    /** The characters it is shared with: a share holds for that character alone, whoever plays it. */
    public Set<UUID> getSharedCharacterIds() { return this.sharedCharacterIds; }
    public Set<UUID> getSharedFellowshipIds() {
        return this.sharedFellowshipIds;
    }
    public LostTalesWaystoneGenerationState getGenerationState() { return this.generationState; }
    public boolean isLinked() { return this.linked; }
    public int getLinkedDimensionId() { return this.linkedDimensionId; }
    public int getLinkedX() { return this.linkedX; }
    public int getLinkedY() { return this.linkedY; }
    public int getLinkedZ() { return this.linkedZ; }
    public UUID getLinkToken() { return this.linkToken; }
    public long getRevision() { return this.revision; }
    /** The settings saved on its page with another value than they had; none for a marker nobody edited. */
    public Set<LostTalesMapMarkerSetting> getSavedSettings() { return this.savedSettings; }

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
        if (!Double.isFinite(value) || Math.abs(value) > MAX_ABSOLUTE_COORDINATE) {
            throw new IllegalArgumentException(
                    "marker " + field + " coordinate is invalid");
        }
        return value;
    }

    private static double requireRadius(double value, String field,
                                        double maximum) {
        if (!Double.isFinite(value) || value < 0.0D || value > maximum) {
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

    public static final class Builder {
        private final String id;
        private final LostTalesMapMarkerSource source;
        private String name = "";
        private String namedAfter = "";
        private String iconName = "undiscovered";
        private String colorName = "white";
        private String categoryName = "";
        private String description = "";
        private boolean hasFastTravel;
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
        private int priority;
        private UUID ownerCharacterId;
        private LostTalesMapMarkerVisibility visibility =
                LostTalesMapMarkerVisibility.PRIVATE;
        private Set<UUID> sharedCharacterIds = Collections.emptySet();
        private Set<UUID> sharedFellowshipIds =
                Collections.emptySet();
        private LostTalesWaystoneGenerationState generationState;
        private boolean linked;
        private int linkedDimensionId;
        private int linkedX;
        private int linkedY;
        private int linkedZ;
        private UUID linkToken;
        private long revision = 1L;
        private Set<LostTalesMapMarkerSetting> savedSettings =
                Collections.emptySet();

        private Builder(String id, LostTalesMapMarkerSource source) {
            this.id = id;
            this.source = source;
        }

        private Builder(LostTalesMapMarkerRecord record) {
            this.id = record.id;
            this.source = record.source;
            this.name = record.name;
            this.namedAfter = record.namedAfter;
            this.iconName = record.iconName;
            this.colorName = record.colorName;
            this.categoryName = record.categoryName;
            this.description = record.description;
            this.hasFastTravel = record.hasFastTravel;
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
            this.priority = record.priority;
            this.ownerCharacterId = record.ownerCharacterId;
            this.visibility = record.visibility;
            this.sharedCharacterIds = record.sharedCharacterIds;
            this.sharedFellowshipIds =
                    record.sharedFellowshipIds;
            this.generationState = record.generationState;
            this.linked = record.linked;
            this.linkedDimensionId = record.linkedDimensionId;
            this.linkedX = record.linkedX;
            this.linkedY = record.linkedY;
            this.linkedZ = record.linkedZ;
            this.linkToken = record.linkToken;
            this.revision = record.revision;
            this.savedSettings = record.savedSettings;
        }

        public Builder name(String value) { this.name = value; return this; }
        public Builder namedAfter(String value) {
            this.namedAfter = value == null ? "" : value;
            return this;
        }
        public Builder iconName(String value) { this.iconName = value; return this; }
        public Builder colorName(String value) { this.colorName = value; return this; }
        public Builder categoryName(String value) { this.categoryName = value; return this; }
        public Builder description(String value) { this.description = value; return this; }
        public Builder fastTravel(boolean enabled) {
            this.hasFastTravel = enabled;
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
        public Builder priority(int value) {
            this.priority = value;
            return this;
        }
        public Builder ownerCharacterId(UUID value) { this.ownerCharacterId = value; return this; }
        /** Public or not; a record that is not public reads as shared or private by its shares. */
        public Builder visibility(LostTalesMapMarkerVisibility value) { this.visibility = value; return this; }
        public Builder sharedCharacterIds(Set<UUID> value) { this.sharedCharacterIds = value; return this; }
        public Builder sharedFellowshipIds(Set<UUID> value) {
            this.sharedFellowshipIds = value;
            return this;
        }
        public Builder generationState(
                LostTalesWaystoneGenerationState value) {
            this.generationState = value;
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
        public Builder revision(long value) { this.revision = value; return this; }
        /** The settings saved on its page with another value than they had (WAY-16). */
        public Builder savedSettings(Set<LostTalesMapMarkerSetting> value) {
            this.savedSettings = value;
            return this;
        }
        public LostTalesMapMarkerRecord build() {
            return new LostTalesMapMarkerRecord(this);
        }
    }
}
