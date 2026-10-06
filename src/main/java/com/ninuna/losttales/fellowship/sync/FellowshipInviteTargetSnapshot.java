package com.ninuna.losttales.fellowship.sync;

import java.util.UUID;

/**
 * Minimal client-safe projection of one currently online account whose active
 * identity may be invited by the receiving fellowship leader.
 */
public final class FellowshipInviteTargetSnapshot {

    private final UUID ownerId;
    private final UUID identityId;
    private final String playerName;
    private final String characterName;

    public FellowshipInviteTargetSnapshot(UUID ownerId,
                                     UUID identityId,
                                     String playerName,
                                     String characterName) {
        if (ownerId == null || identityId == null) {
            throw new IllegalArgumentException("invite target identities must not be null");
        }
        this.ownerId = ownerId;
        this.identityId = identityId;
        this.playerName = normalizeName(playerName);
        this.characterName = normalizeName(characterName);
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public UUID getIdentityId() {
        return this.identityId;
    }

    public String getPlayerName() {
        return this.playerName;
    }

    public String getCharacterName() {
        return this.characterName;
    }

    /** The name trimmed and bounded; empty where there is none, which the client words. */
    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        return normalized.length() <= 64
                ? normalized : normalized.substring(0, 64);
    }
}
