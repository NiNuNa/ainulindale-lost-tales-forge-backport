package com.ninuna.losttales.character.lore.transfer;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.state.CharacterPlayerStateRecord;
import com.ninuna.losttales.character.state.CharacterPlayerStateWorldData;
import com.ninuna.losttales.character.storage.CharacterNbtCodec;

import java.util.Locale;
import java.util.UUID;

/** Durable metadata and player-state copy retained across lore-character owners. */
public final class LoreCharacterVaultEntry {

    private final String loreCharacterId;
    private final UUID capturedOwnerId;
    private final RoleplayCharacter character;
    private final CharacterPlayerStateRecord playerState;
    private final long updatedAt;

    public LoreCharacterVaultEntry(
            String loreCharacterId,
            UUID capturedOwnerId,
            RoleplayCharacter character,
            CharacterPlayerStateRecord playerState,
            long updatedAt) {
        String normalized = normalize(loreCharacterId);
        if (normalized.length() == 0 || capturedOwnerId == null
                || character == null || playerState == null
                || !capturedOwnerId.equals(character.getOwnerId())
                || !character.getCharacterId().equals(
                playerState.getCharacterId())) {
            throw new IllegalArgumentException(
                    "Lore-character vault entry is incomplete or inconsistent");
        }
        this.loreCharacterId = normalized;
        this.capturedOwnerId = capturedOwnerId;
        this.character = copyCharacter(character, capturedOwnerId);
        this.playerState = copyState(playerState);
        this.updatedAt = Math.max(0L, updatedAt);
    }

    public String getLoreCharacterId() {
        return this.loreCharacterId;
    }

    public UUID getCharacterId() {
        return this.character.getCharacterId();
    }

    public UUID getCapturedOwnerId() {
        return this.capturedOwnerId;
    }

    public RoleplayCharacter getCharacterCopy() {
        return copyCharacter(this.character, this.capturedOwnerId);
    }

    public CharacterPlayerStateRecord getPlayerStateCopy() {
        return copyState(this.playerState);
    }

    public long getUpdatedAt() {
        return this.updatedAt;
    }

    private static RoleplayCharacter copyCharacter(
            RoleplayCharacter source, UUID ownerId) {
        return CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(source), ownerId);
    }

    private static CharacterPlayerStateRecord copyState(
            CharacterPlayerStateRecord source) {
        return CharacterPlayerStateWorldData.readRecordCopy(
                CharacterPlayerStateWorldData.writeRecordCopy(source));
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
