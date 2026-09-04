package com.ninuna.losttales.character.identity;

import com.ninuna.losttales.character.model.CharacterRoster;

import java.util.UUID;

/**
 * One identity a player can play as: the Minecraft account itself, or one
 * of the account's roleplay characters. The account is a full identity with
 * saved state of its own, not a gap between characters.
 *
 * <p>The gameplay id is what gameplay systems key on — party membership,
 * saved player state, LOTR bounty records: the character's UUID for a
 * character, the account's own UUID for the account. Character UUIDs are
 * random, so the two never collide.</p>
 */
public final class PlayableIdentity {

    private final UUID ownerId;
    private final UUID characterId;

    private PlayableIdentity(UUID ownerId, UUID characterId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        this.ownerId = ownerId;
        this.characterId = characterId;
    }

    /** The account itself. */
    public static PlayableIdentity account(UUID ownerId) {
        return new PlayableIdentity(ownerId, null);
    }

    /** One of the account's characters. */
    public static PlayableIdentity character(UUID ownerId, UUID characterId) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        return new PlayableIdentity(ownerId, characterId);
    }

    /** The character when one is given, else the account. */
    public static PlayableIdentity of(UUID ownerId, UUID activeCharacterIdOrNull) {
        return new PlayableIdentity(ownerId, activeCharacterIdOrNull);
    }

    /** The identity the roster says its owner is playing as. */
    public static PlayableIdentity fromRoster(CharacterRoster roster) {
        if (roster == null) {
            throw new IllegalArgumentException("roster must not be null");
        }
        return new PlayableIdentity(roster.getOwnerId(), roster.getActiveCharacterId());
    }

    /**
     * The one rule for the gameplay id, shared by the roster, the identity
     * hook and party markers: the character when there is one, else the
     * owner. Null in, null out, so callers with nothing known get nothing.
     */
    public static UUID gameplayId(UUID characterIdOrNull, UUID ownerIdOrNull) {
        return characterIdOrNull != null ? characterIdOrNull : ownerIdOrNull;
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    /** The character's UUID, or null for the account. */
    public UUID getCharacterId() {
        return this.characterId;
    }

    public UUID getGameplayId() {
        return gameplayId(this.characterId, this.ownerId);
    }

    public boolean isAccount() {
        return this.characterId == null;
    }

    public boolean matchesGameplayId(UUID id) {
        return id != null && id.equals(getGameplayId());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlayableIdentity)) {
            return false;
        }
        PlayableIdentity that = (PlayableIdentity) other;
        return this.ownerId.equals(that.ownerId)
                && (this.characterId == null ? that.characterId == null
                        : this.characterId.equals(that.characterId));
    }

    @Override
    public int hashCode() {
        return this.ownerId.hashCode() * 31
                + (this.characterId == null ? 0 : this.characterId.hashCode());
    }

    @Override
    public String toString() {
        return this.characterId == null
                ? "account:" + this.ownerId : "character:" + this.characterId;
    }
}
