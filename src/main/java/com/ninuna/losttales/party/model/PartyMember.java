package com.ninuna.losttales.party.model;

import java.util.UUID;

/** Immutable persisted identity snapshot for one character in a party. */
public final class PartyMember {

    public static final int CURRENT_DATA_VERSION = 1;

    private final UUID characterId;
    private final UUID ownerId;
    private final String characterName;
    private final long joinedAt;
    private final PartyColor color;

    public PartyMember(UUID characterId, UUID ownerId, String characterName,
                       long joinedAt, PartyColor color) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        if (color == null) {
            throw new IllegalArgumentException("color must not be null");
        }
        this.characterId = characterId;
        this.ownerId = ownerId;
        this.characterName = normalizeName(characterName);
        this.joinedAt = Math.max(0L, joinedAt);
        this.color = color;
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public String getCharacterName() {
        return this.characterName;
    }

    public long getJoinedAt() {
        return this.joinedAt;
    }

    public PartyColor getColor() {
        return this.color;
    }

    public PartyMember withColor(PartyColor newColor) {
        return new PartyMember(this.characterId, this.ownerId, this.characterName,
                this.joinedAt, newColor);
    }

    public PartyMember withIdentity(UUID newOwnerId, String newCharacterName) {
        return new PartyMember(this.characterId, newOwnerId, newCharacterName,
                this.joinedAt, this.color);
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "Unknown";
        }
        String normalized = name.trim();
        if (normalized.length() == 0) {
            return "Unknown";
        }
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }
}
