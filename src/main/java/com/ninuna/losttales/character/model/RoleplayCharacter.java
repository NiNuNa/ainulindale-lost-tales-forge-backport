package com.ninuna.losttales.character.model;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;

import java.util.UUID;

/** Persistent server-authoritative record for one roleplaying character. */
public class RoleplayCharacter {

    public static final int CURRENT_DATA_VERSION = 4;
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
    private final int age;
    private final String startingFactionId;
    private final long creationTimestamp;
    private final int dataVersion;

    private int roleplayLevel;
    private CharacterProgression progression;
    private boolean showMinecraftCape;
    private int cosmeticCapeId;

    public static RoleplayCharacter createNew(UUID ownerId, int slotIndex, String name,
                                               String raceId, String genderId,
                                               String skinId, int age,
                                               String startingFactionId,
                                               long creationTimestamp) {
        return new RoleplayCharacter(
                UUID.randomUUID(), ownerId, slotIndex, name, raceId, genderId,
                skinId, age, startingFactionId, INITIAL_ROLEPLAY_LEVEL,
                new CharacterProgression(), creationTimestamp,
                CURRENT_DATA_VERSION, DEFAULT_SHOW_MINECRAFT_CAPE,
                DEFAULT_COSMETIC_CAPE_ID
        );
    }

    /** Compatibility constructor for pre-cape call sites and tests. */
    public RoleplayCharacter(UUID characterId, UUID ownerId, int slotIndex, String name,
                             String raceId, String genderId, String skinId, int age,
                             String startingFactionId, int roleplayLevel,
                             CharacterProgression progression, long creationTimestamp,
                             int dataVersion) {
        this(characterId, ownerId, slotIndex, name, raceId, genderId, skinId,
                age, startingFactionId, roleplayLevel, progression,
                creationTimestamp, dataVersion, DEFAULT_SHOW_MINECRAFT_CAPE,
                DEFAULT_COSMETIC_CAPE_ID);
    }

    public RoleplayCharacter(UUID characterId, UUID ownerId, int slotIndex, String name,
                             String raceId, String genderId, String skinId, int age,
                             String startingFactionId, int roleplayLevel,
                             CharacterProgression progression, long creationTimestamp,
                             int dataVersion, boolean showMinecraftCape,
                             int cosmeticCapeId) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }

        this.characterId = characterId;
        this.ownerId = ownerId;
        this.slotIndex = slotIndex;
        this.name = name == null ? "" : name;
        this.raceId = raceId == null ? "" : raceId;
        this.genderId = genderId == null ? "" : genderId;
        this.skinId = skinId == null ? "" : skinId;
        this.age = age;
        this.startingFactionId = startingFactionId == null ? "" : startingFactionId;
        this.roleplayLevel = Math.max(INITIAL_ROLEPLAY_LEVEL, roleplayLevel);
        this.progression = progression == null ? new CharacterProgression() : progression;
        this.creationTimestamp = Math.max(0L, creationTimestamp);
        this.dataVersion = dataVersion <= 0 ? CURRENT_DATA_VERSION : dataVersion;
        this.showMinecraftCape = showMinecraftCape;
        this.cosmeticCapeId = CharacterCapeCatalog.normalizeSelection(cosmeticCapeId);
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

    public int getAge() {
        return this.age;
    }

    public String getStartingFactionId() {
        return this.startingFactionId;
    }

    public int getRoleplayLevel() {
        return this.roleplayLevel;
    }

    /** Intended for future authoritative progression code only. */
    public void setRoleplayLevel(int roleplayLevel) {
        this.roleplayLevel = Math.max(INITIAL_ROLEPLAY_LEVEL, roleplayLevel);
    }

    public CharacterProgression getProgression() {
        return this.progression;
    }

    public void setProgression(CharacterProgression progression) {
        this.progression = progression == null ? new CharacterProgression() : progression;
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
