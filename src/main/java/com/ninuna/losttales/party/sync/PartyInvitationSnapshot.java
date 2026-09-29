package com.ninuna.losttales.party.sync;

import com.ninuna.losttales.party.model.PartyInvitation;

import java.util.UUID;

/** Immutable invitation projection visible only to its target or party leader. */
public final class PartyInvitationSnapshot {

    private final UUID invitationId;
    private final UUID partyId;
    private final UUID invitingIdentityId;
    private final UUID invitingOwnerId;
    private final String invitingCharacterName;
    private final UUID targetIdentityId;
    private final UUID targetOwnerId;
    private final String targetCharacterName;
    private final long createdAt;
    private final long expiresAt;

    public PartyInvitationSnapshot(UUID invitationId,
                                   UUID partyId,
                                   UUID invitingIdentityId,
                                   UUID invitingOwnerId,
                                   String invitingCharacterName,
                                   UUID targetIdentityId,
                                   UUID targetOwnerId,
                                   String targetCharacterName,
                                   long createdAt,
                                   long expiresAt) {
        if (invitationId == null || partyId == null
                || invitingIdentityId == null || invitingOwnerId == null
                || targetIdentityId == null || targetOwnerId == null) {
            throw new IllegalArgumentException("invitation identities must not be null");
        }
        if (invitingIdentityId.equals(targetIdentityId)
                || createdAt < 0L || expiresAt <= createdAt) {
            throw new IllegalArgumentException("invitation snapshot is invalid");
        }
        this.invitationId = invitationId;
        this.partyId = partyId;
        this.invitingIdentityId = invitingIdentityId;
        this.invitingOwnerId = invitingOwnerId;
        this.invitingCharacterName = normalizeName(invitingCharacterName);
        this.targetIdentityId = targetIdentityId;
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
                invitation.getInvitingIdentityId(),
                invitation.getInvitingOwnerId(),
                invitation.getInvitingCharacterName(),
                invitation.getTargetIdentityId(),
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

    public UUID getInvitingIdentityId() {
        return this.invitingIdentityId;
    }

    public UUID getInvitingOwnerId() {
        return this.invitingOwnerId;
    }

    public String getInvitingCharacterName() {
        return this.invitingCharacterName;
    }

    public UUID getTargetIdentityId() {
        return this.targetIdentityId;
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
