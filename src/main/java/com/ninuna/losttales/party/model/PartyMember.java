package com.ninuna.losttales.party.model;

import java.util.UUID;

/**
 * One member of a party: an identity, which is a character or the account
 * playing as itself, with its owner and name as last seen, when it joined,
 * and the colour it wears.
 */
public final class PartyMember {

    public static final int CURRENT_DATA_VERSION = 1;

    private final UUID identityId;
    private final UUID ownerId;
    private final String characterName;
    private final long joinedAt;
    private final PartyColor color;

    public PartyMember(UUID identityId, UUID ownerId, String characterName,
                       long joinedAt, PartyColor color) {
        if (identityId == null) {
            throw new IllegalArgumentException("identityId must not be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        if (color == null) {
            throw new IllegalArgumentException("color must not be null");
        }
        this.identityId = identityId;
        this.ownerId = ownerId;
        this.characterName = normalizeName(characterName);
        this.joinedAt = Math.max(0L, joinedAt);
        this.color = color;
    }

    public UUID getIdentityId() {
        return this.identityId;
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
        return new PartyMember(this.identityId, this.ownerId, this.characterName,
                this.joinedAt, newColor);
    }

    public PartyMember withIdentity(UUID newOwnerId, String newCharacterName) {
        return new PartyMember(this.identityId, newOwnerId, newCharacterName,
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
