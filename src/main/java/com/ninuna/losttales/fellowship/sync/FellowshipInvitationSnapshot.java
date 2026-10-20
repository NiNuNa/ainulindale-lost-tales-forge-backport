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
    private final String targetCharacterName;
    private final long expiresAt;

    public FellowshipInvitationSnapshot(UUID invitationId,
                                        UUID fellowshipId,
                                        String fellowshipName,
                                        UUID invitingIdentityId,
                                        UUID invitingOwnerId,
                                        String invitingCharacterName,
                                        UUID targetIdentityId,
                                        String targetCharacterName,
                                        long expiresAt) {
        if (invitationId == null || fellowshipId == null
                || invitingIdentityId == null || invitingOwnerId == null
                || targetIdentityId == null) {
            throw new IllegalArgumentException("invitation identities must not be null");
        }
        if (invitingIdentityId.equals(targetIdentityId) || expiresAt <= 0L
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
        this.targetCharacterName = normalizeName(targetCharacterName);
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
                invitation.getTargetCharacterName(),
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

    public String getTargetCharacterName() {
        return this.targetCharacterName;
    }

    public long getExpiresAt() {
        return this.expiresAt;
    }

    public boolean isExpired(long now) {
        return now >= this.expiresAt;
    }

    /** The name trimmed and bounded; empty where there is none, which the client words. */
    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        return normalized.length() <= 64
                ? normalized : normalized.substring(0, 64);
    }
}
