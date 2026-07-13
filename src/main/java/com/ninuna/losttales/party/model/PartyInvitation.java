package com.ninuna.losttales.party.model;

import java.util.UUID;

/**
 * Immutable server-owned invitation for one character to join one party.
 *
 * Account UUIDs and names are validation/display snapshots. Character UUIDs
 * remain the authoritative identities.
 */
public final class PartyInvitation {

    public static final int CURRENT_DATA_VERSION = 1;

    private final UUID invitationId;
    private final UUID partyId;
    private final UUID invitingCharacterId;
    private final UUID invitingOwnerId;
    private final String invitingCharacterName;
    private final UUID targetCharacterId;
    private final UUID targetOwnerId;
    private final String targetCharacterName;
    private final long createdAt;
    private final long expiresAt;

    public PartyInvitation(UUID invitationId,
                           UUID partyId,
                           UUID invitingCharacterId,
                           UUID invitingOwnerId,
                           String invitingCharacterName,
                           UUID targetCharacterId,
                           UUID targetOwnerId,
                           String targetCharacterName,
                           long createdAt,
                           long expiresAt) {
        if (invitationId == null) {
            throw new IllegalArgumentException("invitationId must not be null");
        }
        if (partyId == null) {
            throw new IllegalArgumentException("partyId must not be null");
        }
        if (invitingCharacterId == null || invitingOwnerId == null) {
            throw new IllegalArgumentException("inviting identity must not be null");
        }
        if (targetCharacterId == null || targetOwnerId == null) {
            throw new IllegalArgumentException("target identity must not be null");
        }
        if (invitingCharacterId.equals(targetCharacterId)) {
            throw new IllegalArgumentException("a character cannot invite itself");
        }
        if (createdAt < 0L || expiresAt <= createdAt) {
            throw new IllegalArgumentException("invitation timestamps are invalid");
        }
        this.invitationId = invitationId;
        this.partyId = partyId;
        this.invitingCharacterId = invitingCharacterId;
        this.invitingOwnerId = invitingOwnerId;
        this.invitingCharacterName = normalizeName(invitingCharacterName);
        this.targetCharacterId = targetCharacterId;
        this.targetOwnerId = targetOwnerId;
        this.targetCharacterName = normalizeName(targetCharacterName);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getInvitationId() {
        return this.invitationId;
    }

    public UUID getPartyId() {
        return this.partyId;
    }

    public UUID getInvitingCharacterId() {
        return this.invitingCharacterId;
    }

    public UUID getInvitingOwnerId() {
        return this.invitingOwnerId;
    }

    public String getInvitingCharacterName() {
        return this.invitingCharacterName;
    }

    public UUID getTargetCharacterId() {
        return this.targetCharacterId;
    }

    public UUID getTargetOwnerId() {
        return this.targetOwnerId;
    }

    public String getTargetCharacterName() {
        return this.targetCharacterName;
    }

    public long getCreatedAt() {
        return this.createdAt;
    }

    public long getExpiresAt() {
        return this.expiresAt;
    }

    public boolean isExpired(long now) {
        return now >= this.expiresAt;
    }

    public boolean matchesPartyAndTarget(UUID expectedPartyId,
                                         UUID expectedTargetCharacterId) {
        return expectedPartyId != null
                && expectedTargetCharacterId != null
                && expectedPartyId.equals(this.partyId)
                && expectedTargetCharacterId.equals(this.targetCharacterId);
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
