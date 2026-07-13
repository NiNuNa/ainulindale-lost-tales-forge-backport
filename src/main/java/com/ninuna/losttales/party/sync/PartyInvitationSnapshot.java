package com.ninuna.losttales.party.sync;

import com.ninuna.losttales.party.model.PartyInvitation;

import java.util.UUID;

/** Immutable invitation projection visible only to its target or party leader. */
public final class PartyInvitationSnapshot {

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

    public PartyInvitationSnapshot(UUID invitationId,
                                   UUID partyId,
                                   UUID invitingCharacterId,
                                   UUID invitingOwnerId,
                                   String invitingCharacterName,
                                   UUID targetCharacterId,
                                   UUID targetOwnerId,
                                   String targetCharacterName,
                                   long createdAt,
                                   long expiresAt) {
        if (invitationId == null || partyId == null
                || invitingCharacterId == null || invitingOwnerId == null
                || targetCharacterId == null || targetOwnerId == null) {
            throw new IllegalArgumentException("invitation identities must not be null");
        }
        if (invitingCharacterId.equals(targetCharacterId)
                || createdAt < 0L || expiresAt <= createdAt) {
            throw new IllegalArgumentException("invitation snapshot is invalid");
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

    public static PartyInvitationSnapshot fromInvitation(PartyInvitation invitation) {
        if (invitation == null) {
            throw new IllegalArgumentException("invitation must not be null");
        }
        return new PartyInvitationSnapshot(
                invitation.getInvitationId(),
                invitation.getPartyId(),
                invitation.getInvitingCharacterId(),
                invitation.getInvitingOwnerId(),
                invitation.getInvitingCharacterName(),
                invitation.getTargetCharacterId(),
                invitation.getTargetOwnerId(),
                invitation.getTargetCharacterName(),
                invitation.getCreatedAt(),
                invitation.getExpiresAt());
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
