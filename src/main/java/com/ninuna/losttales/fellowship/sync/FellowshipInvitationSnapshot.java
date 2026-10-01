package com.ninuna.losttales.fellowship.sync;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipInvitation;

import java.util.UUID;

/**
 * An invitation as its target, and the leader and guides of the fellowship
 * that sent it, see it, with the fellowship's name.
 */
public final class FellowshipInvitationSnapshot {

    private final UUID invitationId;
    private final UUID fellowshipId;
    private final String fellowshipName;
    private final UUID invitingIdentityId;
    private final UUID invitingOwnerId;
    private final String invitingCharacterName;
    private final UUID targetIdentityId;
    private final UUID targetOwnerId;
    private final String targetCharacterName;
    private final long createdAt;
    private final long expiresAt;

    public FellowshipInvitationSnapshot(UUID invitationId,
                                        UUID fellowshipId,
                                        String fellowshipName,
                                        UUID invitingIdentityId,
                                        UUID invitingOwnerId,
                                        String invitingCharacterName,
                                        UUID targetIdentityId,
                                        UUID targetOwnerId,
                                        String targetCharacterName,
                                        long createdAt,
                                        long expiresAt) {
        if (invitationId == null || fellowshipId == null
                || invitingIdentityId == null || invitingOwnerId == null
                || targetIdentityId == null || targetOwnerId == null) {
            throw new IllegalArgumentException("invitation identities must not be null");
        }
        if (invitingIdentityId.equals(targetIdentityId)
                || createdAt < 0L || expiresAt <= createdAt
                || !Fellowship.isWellFormedName(fellowshipName)) {
            throw new IllegalArgumentException("invitation snapshot is invalid");
        }
        this.invitationId = invitationId;
        this.fellowshipId = fellowshipId;
        this.fellowshipName = fellowshipName;
        this.invitingIdentityId = invitingIdentityId;
        this.invitingOwnerId = invitingOwnerId;
        this.invitingCharacterName = normalizeName(invitingCharacterName);
        this.targetIdentityId = targetIdentityId;
        this.targetOwnerId = targetOwnerId;
        this.targetCharacterName = normalizeName(targetCharacterName);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static FellowshipInvitationSnapshot fromInvitation(FellowshipInvitation invitation,
                                                              String fellowshipName) {
        if (invitation == null) {
            throw new IllegalArgumentException("invitation must not be null");
        }
        return new FellowshipInvitationSnapshot(
                invitation.getInvitationId(),
                invitation.getFellowshipId(),
                fellowshipName,
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

    public UUID getFellowshipId() {
        return this.fellowshipId;
    }

    public String getFellowshipName() {
        return this.fellowshipName;
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
