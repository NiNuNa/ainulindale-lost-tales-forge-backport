package com.ninuna.losttales.fellowship.sync;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipInvitation;

import java.util.UUID;

/**
 * An invitation as its target, and the leader and guides of the fellowship
 * that sent it, see it, with the fellowship's name and the accounts behind
 * both characters. The accounts are always sent; each reader's Show
 * Accounts decides whether they show (FEL-31).
 */
public final class FellowshipInvitationSnapshot {

    private final UUID invitationId;
    private final UUID fellowshipId;
    private final String fellowshipName;
    private final UUID invitingIdentityId;
    private final UUID invitingOwnerId;
    private final String invitingCharacterName;
    /** The inviting character's account name; empty where the server knows none. */
    private final String invitingAccountName;
    private final UUID targetIdentityId;
    private final String targetCharacterName;
    /** The invited character's account name; empty where the server knows none. */
    private final String targetAccountName;
    private final long expiresAt;

    public FellowshipInvitationSnapshot(UUID invitationId,
                                        UUID fellowshipId,
                                        String fellowshipName,
                                        UUID invitingIdentityId,
                                        UUID invitingOwnerId,
                                        String invitingCharacterName,
                                        String invitingAccountName,
                                        UUID targetIdentityId,
                                        String targetCharacterName,
                                        String targetAccountName,
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
        this.invitingAccountName = normalizeName(invitingAccountName);
        this.targetIdentityId = targetIdentityId;
        this.targetCharacterName = normalizeName(targetCharacterName);
        this.targetAccountName = normalizeName(targetAccountName);
        this.expiresAt = expiresAt;
    }

    /** The invitation with its fellowship's name and the names of the accounts behind it. */
    public static FellowshipInvitationSnapshot fromInvitation(FellowshipInvitation invitation,
                                                              String fellowshipName,
                                                              String invitingAccountName,
                                                              String targetAccountName) {
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
                invitingAccountName,
                invitation.getTargetIdentityId(),
                invitation.getTargetCharacterName(),
                targetAccountName,
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

    /** The inviting character's account name; empty where the server knows none. */
    public String getInvitingAccountName() {
        return this.invitingAccountName;
    }

    public UUID getTargetIdentityId() {
        return this.targetIdentityId;
    }

    public String getTargetCharacterName() {
        return this.targetCharacterName;
    }

    /** The invited character's account name; empty where the server knows none. */
    public String getTargetAccountName() {
        return this.targetAccountName;
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
