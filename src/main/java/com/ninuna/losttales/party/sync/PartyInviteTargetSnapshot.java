package com.ninuna.losttales.party.sync;

import java.util.UUID;

/**
 * Minimal client-safe projection of one currently online account whose active
 * role-playing character may be invited by the receiving party leader.
 */
public final class PartyInviteTargetSnapshot {

    private final UUID ownerId;
    private final UUID characterId;
    private final String playerName;
    private final String characterName;

    public PartyInviteTargetSnapshot(UUID ownerId,
                                     UUID characterId,
                                     String playerName,
                                     String characterName) {
        if (ownerId == null || characterId == null) {
            throw new IllegalArgumentException("invite target identities must not be null");
        }
        this.ownerId = ownerId;
        this.characterId = characterId;
        this.playerName = normalizeName(playerName);
        this.characterName = normalizeName(characterName);
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public String getPlayerName() {
        return this.playerName;
    }

    public String getCharacterName() {
        return this.characterName;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "Unknown";
        }
        String normalized = name.trim();
        if (normalized.length() == 0) {
            return "Unknown";
        }
        return normalized.length() <= 64
                ? normalized : normalized.substring(0, 64);
    }
}
