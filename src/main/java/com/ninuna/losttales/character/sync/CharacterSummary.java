package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.model.RoleplayCharacter;

import java.util.UUID;

/** Immutable client-safe projection of one private roleplaying character. */
public final class CharacterSummary {

    private final UUID characterId;
    private final int slotIndex;
    private final String name;
    private final String raceId;
    private final String genderId;
    private final String skinId;
    private final int age;
    private final String startingFactionId;
    private final int roleplayLevel;
    private final long experiencePoints;
    private final long creationTimestamp;
    private final int dataVersion;

    public CharacterSummary(UUID characterId, int slotIndex, String name,
                            String raceId, String genderId, String skinId, int age,
                            String startingFactionId, int roleplayLevel,
                            long experiencePoints, long creationTimestamp,
                            int dataVersion) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        this.characterId = characterId;
        this.slotIndex = slotIndex;
        this.name = name == null ? "" : name;
        this.raceId = raceId == null ? "" : raceId;
        this.genderId = genderId == null ? "" : genderId;
        this.skinId = skinId == null ? "" : skinId;
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
                character.getAge(),
                character.getStartingFactionId(),
                character.getRoleplayLevel(),
                character.getProgression().getExperiencePoints(),
                character.getCreationTimestamp(),
                character.getDataVersion()
        );
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
