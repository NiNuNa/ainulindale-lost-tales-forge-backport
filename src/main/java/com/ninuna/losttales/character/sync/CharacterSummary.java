package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.model.CharacterRoster;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;

import java.util.UUID;

/** Immutable client-safe projection of one private roleplaying character. */
public final class CharacterSummary {

    private final UUID characterId;
    private final int slotIndex;
    private final String name;
    private final String raceId;
    private final String genderId;
    private final String skinId;
    private final String bodyTypeId;
    private final String chestTypeId;
    private final String description;
    private final boolean showMinecraftCape;
    private final int cosmeticCapeId;
    private final int age;
    private final String startingFactionId;
    private final int roleplayLevel;
    private final long experiencePoints;
    private final long creationTimestamp;
    private final int dataVersion;

    /** Compatibility constructor for pre-cape packet tests and callers. */
    public CharacterSummary(UUID characterId, int slotIndex, String name,
                            String raceId, String genderId, String skinId, int age,
                            String startingFactionId, int roleplayLevel,
                            long experiencePoints, long creationTimestamp,
                            int dataVersion) {
        this(characterId, slotIndex, name, raceId, genderId, skinId,
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID,
                age, startingFactionId, roleplayLevel, experiencePoints,
                creationTimestamp, dataVersion, "");
    }

    /** Body and chest types default from the sex. */
    public CharacterSummary(UUID characterId, int slotIndex, String name,
                            String raceId, String genderId, String skinId,
                            boolean showMinecraftCape, int cosmeticCapeId,
                            int age, String startingFactionId, int roleplayLevel,
                            long experiencePoints, long creationTimestamp,
                            int dataVersion, String description) {
        this(characterId, slotIndex, name, raceId, genderId, skinId,
                showMinecraftCape, cosmeticCapeId, age, startingFactionId,
                roleplayLevel, experiencePoints, creationTimestamp,
                dataVersion, description,
                CharacterBodyTypeRegistry.defaultFor(genderId));
    }

    /** Chest type defaults from the sex. */
    public CharacterSummary(UUID characterId, int slotIndex, String name,
                            String raceId, String genderId, String skinId,
                            boolean showMinecraftCape, int cosmeticCapeId,
                            int age, String startingFactionId, int roleplayLevel,
                            long experiencePoints, long creationTimestamp,
                            int dataVersion, String description, String bodyTypeId) {
        this(characterId, slotIndex, name, raceId, genderId, skinId,
                showMinecraftCape, cosmeticCapeId, age, startingFactionId,
                roleplayLevel, experiencePoints, creationTimestamp,
                dataVersion, description, bodyTypeId,
                CharacterChestTypeRegistry.defaultFor(genderId));
    }

    public CharacterSummary(UUID characterId, int slotIndex, String name,
                            String raceId, String genderId, String skinId,
                            boolean showMinecraftCape, int cosmeticCapeId,
                            int age, String startingFactionId, int roleplayLevel,
                            long experiencePoints, long creationTimestamp,
                            int dataVersion, String description, String bodyTypeId,
                            String chestTypeId) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        this.characterId = characterId;
        this.slotIndex = slotIndex;
        this.name = name == null ? "" : name;
        this.raceId = raceId == null ? "" : raceId;
        this.genderId = genderId == null ? "" : genderId;
        this.skinId = skinId == null ? "" : skinId;
        this.bodyTypeId = CharacterBodyTypeRegistry.contains(bodyTypeId)
                ? CharacterBodyTypeRegistry.normalizeIdentifier(bodyTypeId)
                : CharacterBodyTypeRegistry.defaultFor(genderId);
        this.chestTypeId = CharacterChestTypeRegistry.contains(chestTypeId)
                ? CharacterChestTypeRegistry.normalizeIdentifier(chestTypeId)
                : CharacterChestTypeRegistry.defaultFor(genderId);
        this.description = description == null ? "" : description;
        this.showMinecraftCape = showMinecraftCape;
        this.cosmeticCapeId = CharacterCapeCatalog.normalizeSelection(cosmeticCapeId);
        this.age = age;
        this.startingFactionId = startingFactionId == null ? "" : startingFactionId;
        this.roleplayLevel = Math.max(RoleplayCharacter.INITIAL_ROLEPLAY_LEVEL, roleplayLevel);
        this.experiencePoints = Math.max(0L, experiencePoints);
        this.creationTimestamp = Math.max(0L, creationTimestamp);
        this.dataVersion = Math.max(1, dataVersion);
    }

    public static CharacterSummary fromCharacter(RoleplayCharacter character) {
        if (character == null) {
            throw new IllegalArgumentException("character must not be null");
        }
        return new CharacterSummary(
                character.getCharacterId(),
                character.getSlotIndex(),
                character.getName(),
                character.getRaceId(),
                character.getGenderId(),
                character.getSkinId(),
                character.isMinecraftCapeVisible(),
                character.getCosmeticCapeId(),
                character.getAge(),
                character.getStartingFactionId(),
                character.getRoleplayLevel(),
                character.getProgression().getExperiencePoints(),
                character.getCreationTimestamp(),
                character.getDataVersion(),
                character.getDescription(),
                character.getBodyTypeId(),
                character.getChestTypeId()
        );
    }

    /**
     * Whether this is the account's own identity. The slot says so: the
     * default character is the one outside the nine, which is also what
     * puts it first when the roster is ordered. Nothing else can be
     * stored there — creation and lore claims both refuse the slot, and
     * the codec quarantines a record whose slot and kind disagree — so
     * the slot answers the question the stored kind answers on the
     * server, without a second field on the wire to fall out of step
     * with it.
     */
    public boolean isDefault() {
        return this.slotIndex == CharacterRoster.DEFAULT_SLOT_INDEX;
    }

    public UUID getCharacterId() {
        return this.characterId;
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

    public String getBodyTypeId() {
        return this.bodyTypeId;
    }

    public String getChestTypeId() {
        return this.chestTypeId;
    }

    public String getDescription() {
        return this.description;
    }

    public boolean isMinecraftCapeVisible() {
        return this.showMinecraftCape;
    }

    public int getCosmeticCapeId() {
        return this.cosmeticCapeId;
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

    public long getExperiencePoints() {
        return this.experiencePoints;
    }

    public long getCreationTimestamp() {
        return this.creationTimestamp;
    }

    public int getDataVersion() {
        return this.dataVersion;
    }
}
