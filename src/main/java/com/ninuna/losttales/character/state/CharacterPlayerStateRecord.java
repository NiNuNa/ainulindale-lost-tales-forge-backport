package com.ninuna.losttales.character.state;

import java.util.UUID;

/** Current and previous immutable generations for one role-playing character. */
public final class CharacterPlayerStateRecord {

    public static final int CURRENT_DATA_VERSION = 1;

    private final UUID characterId;
    private CharacterPlayerStateSnapshot current;
    private CharacterPlayerStateSnapshot previous;

    public CharacterPlayerStateRecord(UUID characterId,
                                      CharacterPlayerStateSnapshot current,
                                      CharacterPlayerStateSnapshot previous) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        validateCharacter(characterId, current);
        validateCharacter(characterId, previous);
        if (current == null) {
            throw new IllegalArgumentException("current snapshot must not be null");
        }
        if (previous != null && previous.getGeneration() >= current.getGeneration()) {
            throw new IllegalArgumentException("previous generation must precede current");
        }
        this.characterId = characterId;
        this.current = current;
        this.previous = previous;
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public CharacterPlayerStateSnapshot getCurrent() {
        return this.current;
    }

    public CharacterPlayerStateSnapshot getPrevious() {
        return this.previous;
    }

    public long getCurrentGeneration() {
        return this.current.getGeneration();
    }

    public CharacterPlayerStateSnapshot createNext(
            long capturedAt,
            java.util.Map<String, net.minecraft.nbt.NBTTagCompound> components) {
        long next = this.current.getGeneration() == Long.MAX_VALUE
                ? Long.MAX_VALUE : this.current.getGeneration() + 1L;
        if (next == this.current.getGeneration()) {
            throw new IllegalStateException("character state generation is exhausted");
        }
        return new CharacterPlayerStateSnapshot(
                this.characterId,
                next,
                capturedAt,
                CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION,
                components);
    }

    /** Commits only a previously validated next generation. */
    public void commit(CharacterPlayerStateSnapshot snapshot) {
        validateCharacter(this.characterId, snapshot);
        if (snapshot == null
                || snapshot.getGeneration() != this.current.getGeneration() + 1L) {
            throw new IllegalArgumentException(
                    "snapshot is not the next character state generation");
        }
        this.previous = this.current;
        this.current = snapshot;
    }

    public CharacterPlayerStateSnapshot find(long generation) {
        if (this.current != null && this.current.getGeneration() == generation) {
            return this.current;
        }
        if (this.previous != null && this.previous.getGeneration() == generation) {
            return this.previous;
        }
        return null;
    }

    private static void validateCharacter(UUID characterId,
                                          CharacterPlayerStateSnapshot snapshot) {
        if (snapshot != null && !characterId.equals(snapshot.getCharacterId())) {
            throw new IllegalArgumentException("snapshot belongs to another character");
        }
    }
}
