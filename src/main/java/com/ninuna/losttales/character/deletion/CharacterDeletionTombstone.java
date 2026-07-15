package com.ninuna.losttales.character.deletion;

import com.ninuna.losttales.character.model.CharacterProgression;
import com.ninuna.losttales.character.model.RoleplayCharacter;

import java.util.UUID;

/** Durable metadata and snapshot reference for a recoverable deletion. */
public final class CharacterDeletionTombstone {

    public static final int CURRENT_DATA_VERSION = 1;

    private final UUID ownerId;
    private final RoleplayCharacter character;
    private final long stateGeneration;
    private final long preparedAt;
    private long deletedAt;
    private long purgeAfter;

    public CharacterDeletionTombstone(UUID ownerId,
                                      RoleplayCharacter character,
                                      long stateGeneration,
                                      long preparedAt,
                                      long deletedAt,
                                      long purgeAfter) {
        if (ownerId == null || character == null
                || !ownerId.equals(character.getOwnerId())) {
            throw new IllegalArgumentException(
                    "Deletion tombstone owner and character must match");
        }
        if (stateGeneration <= 0L || preparedAt <= 0L) {
            throw new IllegalArgumentException(
                    "Deletion tombstone requires a state generation and preparation time");
        }
        if (deletedAt < 0L || purgeAfter < 0L
                || deletedAt == 0L && purgeAfter != 0L
                || deletedAt > 0L && purgeAfter < deletedAt) {
            throw new IllegalArgumentException(
                    "Deletion tombstone timestamps are inconsistent");
        }
        this.ownerId = ownerId;
        this.character = copyCharacter(character);
        this.stateGeneration = stateGeneration;
        this.preparedAt = preparedAt;
        this.deletedAt = deletedAt;
        this.purgeAfter = purgeAfter;
    }

    public static CharacterDeletionTombstone prepared(
            RoleplayCharacter character, long stateGeneration, long preparedAt) {
        return new CharacterDeletionTombstone(
                character.getOwnerId(), character, stateGeneration,
                Math.max(1L, preparedAt), 0L, 0L);
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public UUID getCharacterId() {
        return this.character.getCharacterId();
    }

    public RoleplayCharacter getCharacterCopy() {
        return copyCharacter(this.character);
    }

    public long getStateGeneration() {
        return this.stateGeneration;
    }

    public long getPreparedAt() {
        return this.preparedAt;
    }

    public long getDeletedAt() {
        return this.deletedAt;
    }

    public long getPurgeAfter() {
        return this.purgeAfter;
    }

    public boolean isCommitted() {
        return this.deletedAt > 0L;
    }

    public boolean isPurgeAllowed(long now) {
        return isCommitted() && now >= this.purgeAfter;
    }

    public void commit(long deletedAt, long purgeAfter) {
        long safeDeletedAt = Math.max(this.preparedAt, deletedAt);
        if (safeDeletedAt <= 0L || purgeAfter < safeDeletedAt) {
            throw new IllegalArgumentException(
                    "Committed deletion timestamps are inconsistent");
        }
        this.deletedAt = safeDeletedAt;
        this.purgeAfter = purgeAfter;
    }

    private static RoleplayCharacter copyCharacter(RoleplayCharacter source) {
        CharacterProgression progression = source.getProgression();
        CharacterProgression progressionCopy = progression == null
                ? new CharacterProgression()
                : new CharacterProgression(
                        progression.getDataVersion(),
                        progression.getExperiencePoints(),
                        progression.getExtensionDataCopy());
        return new RoleplayCharacter(
                source.getCharacterId(),
                source.getOwnerId(),
                source.getSlotIndex(),
                source.getName(),
                source.getRaceId(),
                source.getGenderId(),
                source.getSkinId(),
                source.getAge(),
                source.getStartingFactionId(),
                source.getRoleplayLevel(),
                progressionCopy,
                source.getCreationTimestamp(),
                source.getDataVersion(),
                source.isMinecraftCapeVisible(),
                source.getCosmeticCapeId(),
                source.getStartingWaypointId(),
                source.hasUnconventionalSettings(),
                source.getDescription());
    }
}
