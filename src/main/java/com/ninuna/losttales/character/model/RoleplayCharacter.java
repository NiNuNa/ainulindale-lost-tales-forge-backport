package com.ninuna.losttales.character.model;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;

import java.util.UUID;

/** Persistent server-authoritative record for one roleplaying character. */
public class RoleplayCharacter {

    public static final int CURRENT_DATA_VERSION = 8;
    public static final int INITIAL_ROLEPLAY_LEVEL = 1;
    public static final boolean DEFAULT_SHOW_MINECRAFT_CAPE = true;
    public static final int DEFAULT_COSMETIC_CAPE_ID = CharacterCapeCatalog.NONE_ID;

    private final UUID characterId;
    private final UUID ownerId;
    private final int slotIndex;
    private final String name;
    private final String raceId;
    private final String genderId;
    private final String skinId;
    private final String bodyTypeId;
    private final String chestTypeId;
    private final String description;
    private final int age;
    private final String startingFactionId;
    private final String startingWaypointId;
    private final boolean unconventionalSettings;
    private final long creationTimestamp;
    private final int dataVersion;

    private int roleplayLevel;
    private CharacterProgression progression;
    private boolean showMinecraftCape;
    private int cosmeticCapeId;

    /** A character made now: every optional field at its default. */
    public static RoleplayCharacter createNew(UUID ownerId, int slotIndex, String name,
                                               String raceId, String genderId,
                                               String skinId, int age,
                                               String startingFactionId,
                                               long creationTimestamp) {
        return builder(UUID.randomUUID(), ownerId)
                .slot(slotIndex).name(name).race(raceId).gender(genderId)
                .skin(skinId).age(age).startingFaction(startingFactionId)
                .createdAt(creationTimestamp)
                .build();
    }

    /** A builder for a character with these ids; everything else defaults. */
    public static Builder builder(UUID characterId, UUID ownerId) {
        return new Builder(characterId, ownerId);
    }

    /** A builder holding every field of {@code source}, to change some. */
    public static Builder builder(RoleplayCharacter source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        return new Builder(source.characterId, source.ownerId)
                .slot(source.slotIndex).name(source.name).race(source.raceId)
                .gender(source.genderId).skin(source.skinId).age(source.age)
                .startingFaction(source.startingFactionId)
                .roleplayLevel(source.roleplayLevel)
                .progression(source.progression)
                .createdAt(source.creationTimestamp)
                .dataVersion(source.dataVersion)
                .minecraftCapeVisible(source.showMinecraftCape)
                .cosmeticCape(source.cosmeticCapeId)
                .startingWaypoint(source.startingWaypointId)
                .unconventionalSettings(source.unconventionalSettings)
                .description(source.description)
                .bodyType(source.bodyTypeId)
                .chestType(source.chestTypeId);
    }

    private RoleplayCharacter(Builder builder) {
        this.characterId = builder.characterId;
        this.ownerId = builder.ownerId;
        this.slotIndex = builder.slotIndex;
        this.name = builder.name;
        this.raceId = builder.raceId;
        this.genderId = builder.genderId;
        this.skinId = builder.skinId;
        this.bodyTypeId = CharacterBodyTypeRegistry.contains(builder.bodyTypeId)
                ? CharacterBodyTypeRegistry.normalizeIdentifier(builder.bodyTypeId)
                : CharacterBodyTypeRegistry.defaultFor(builder.genderId);
        this.chestTypeId = CharacterChestTypeRegistry.contains(builder.chestTypeId)
                ? CharacterChestTypeRegistry.normalizeIdentifier(builder.chestTypeId)
                : CharacterChestTypeRegistry.defaultFor(builder.genderId);
        this.description = builder.description;
        this.age = builder.age;
        this.startingFactionId = builder.startingFactionId;
        this.startingWaypointId = builder.startingWaypointId;
        this.unconventionalSettings = builder.unconventionalSettings;
        this.roleplayLevel = Math.max(INITIAL_ROLEPLAY_LEVEL, builder.roleplayLevel);
        this.progression = builder.progression == null
                ? new CharacterProgression() : builder.progression;
        this.creationTimestamp = Math.max(0L, builder.creationTimestamp);
        this.dataVersion = builder.dataVersion <= 0
                ? CURRENT_DATA_VERSION : builder.dataVersion;
        this.showMinecraftCape = builder.showMinecraftCape;
        this.cosmeticCapeId = CharacterCapeCatalog.normalizeSelection(
                builder.cosmeticCapeId);
    }

    /**
     * Every field a character record holds, with the default a record
     * written before the field existed gets: the body and chest types
     * follow the sex, the level is the first, the capes are the
     * catalogue's defaults. A schema bump adds one setter here.
     */
    public static final class Builder {
        private final UUID characterId;
        private UUID ownerId;
        private int slotIndex;
        private String name = "";
        private String raceId = "";
        private String genderId = "";
        private String skinId = "";
        private int age;
        private String startingFactionId = "";
        private int roleplayLevel = INITIAL_ROLEPLAY_LEVEL;
        private CharacterProgression progression;
        private long creationTimestamp;
        private int dataVersion = CURRENT_DATA_VERSION;
        private boolean showMinecraftCape = DEFAULT_SHOW_MINECRAFT_CAPE;
        private int cosmeticCapeId = DEFAULT_COSMETIC_CAPE_ID;
        private String startingWaypointId = "";
        private boolean unconventionalSettings;
        private String description = "";
        private String bodyTypeId;
        private String chestTypeId;

        private Builder(UUID characterId, UUID ownerId) {
            if (characterId == null) {
                throw new IllegalArgumentException("characterId must not be null");
            }
            if (ownerId == null) {
                throw new IllegalArgumentException("ownerId must not be null");
            }
            this.characterId = characterId;
            this.ownerId = ownerId;
        }

        /** The account the character belongs to, when it changes hands. */
        public Builder owner(UUID ownerId) {
            if (ownerId == null) {
                throw new IllegalArgumentException("ownerId must not be null");
            }
            this.ownerId = ownerId;
            return this;
        }

        public Builder slot(int slotIndex) {
            this.slotIndex = slotIndex;
            return this;
        }

        public Builder name(String name) {
            this.name = name == null ? "" : name;
            return this;
        }

        public Builder race(String raceId) {
            this.raceId = raceId == null ? "" : raceId;
            return this;
        }

        public Builder gender(String genderId) {
            this.genderId = genderId == null ? "" : genderId;
            return this;
        }

        public Builder skin(String skinId) {
            this.skinId = skinId == null ? "" : skinId;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public Builder startingFaction(String startingFactionId) {
            this.startingFactionId = startingFactionId == null
                    ? "" : startingFactionId;
            return this;
        }

        public Builder roleplayLevel(int roleplayLevel) {
            this.roleplayLevel = roleplayLevel;
            return this;
        }

        public Builder progression(CharacterProgression progression) {
            this.progression = progression;
            return this;
        }

        public Builder createdAt(long creationTimestamp) {
            this.creationTimestamp = creationTimestamp;
            return this;
        }

        public Builder dataVersion(int dataVersion) {
            this.dataVersion = dataVersion;
            return this;
        }

        public Builder minecraftCapeVisible(boolean showMinecraftCape) {
            this.showMinecraftCape = showMinecraftCape;
            return this;
        }

        public Builder cosmeticCape(int cosmeticCapeId) {
            this.cosmeticCapeId = cosmeticCapeId;
            return this;
        }

        public Builder startingWaypoint(String startingWaypointId) {
            this.startingWaypointId = startingWaypointId == null
                    ? "" : startingWaypointId;
            return this;
        }

        public Builder unconventionalSettings(boolean unconventionalSettings) {
            this.unconventionalSettings = unconventionalSettings;
            return this;
        }

        public Builder description(String description) {
            this.description = description == null ? "" : description;
            return this;
        }

        /** Null, or an id the registry does not hold, is the sex's default. */
        public Builder bodyType(String bodyTypeId) {
            this.bodyTypeId = bodyTypeId;
            return this;
        }

        /** Null, or an id the registry does not hold, is the sex's default. */
        public Builder chestType(String chestTypeId) {
            this.chestTypeId = chestTypeId;
            return this;
        }

        public RoleplayCharacter build() {
            return new RoleplayCharacter(this);
        }
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public int getSlotIndex() {
        return this.slotIndex;
    }

    public String getName() {
        return this.name;
    }

    public String getRaceId() {
        return this.raceId;
    }

    public String getGenderId() {
        return this.genderId;
    }

    public String getSkinId() {
        return this.skinId;
    }

    /** Arm width of the body, one of {@link CharacterBodyTypeRegistry}; stored apart from sex. */
    public String getBodyTypeId() {
        return this.bodyTypeId;
    }

    /** Chest shape and size, one of {@link CharacterChestTypeRegistry}; stored apart from sex. */
    public String getChestTypeId() {
        return this.chestTypeId;
    }

    public String getDescription() {
        return this.description;
    }

    public int getAge() {
        return this.age;
    }

    public String getStartingFactionId() {
        return this.startingFactionId;
    }

    public String getStartingWaypointId() {
        return this.startingWaypointId;
    }

    public boolean hasUnconventionalSettings() {
        return this.unconventionalSettings;
    }

    /**
     * The level and progression are stored and shown but nothing
     * awards them yet; they change only through the codec that reads
     * them back.
     */
    public int getRoleplayLevel() {
        return this.roleplayLevel;
    }

    public CharacterProgression getProgression() {
        return this.progression;
    }

    public boolean isMinecraftCapeVisible() {
        return this.showMinecraftCape;
    }

    public int getCosmeticCapeId() {
        return this.cosmeticCapeId;
    }

    /** Called only after server-side catalog and eligibility validation. */
    public boolean setCapeSettings(boolean showMinecraftCape, int cosmeticCapeId) {
        int normalizedCapeId = CharacterCapeCatalog.normalizeSelection(cosmeticCapeId);
        boolean changed = this.showMinecraftCape != showMinecraftCape
                || this.cosmeticCapeId != normalizedCapeId;
        this.showMinecraftCape = showMinecraftCape;
        this.cosmeticCapeId = normalizedCapeId;
        return changed;
    }

    public long getCreationTimestamp() {
        return this.creationTimestamp;
    }

    public int getDataVersion() {
        return this.dataVersion;
    }
}
