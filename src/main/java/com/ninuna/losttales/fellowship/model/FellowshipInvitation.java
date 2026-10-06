package com.ninuna.losttales.fellowship.model;

import java.util.UUID;

/**
 * Immutable server-owned invitation for one identity to join one fellowship.
 *
 * Account UUIDs and names are validation/display snapshots. The identity
 * ids, a character's or the account's own, are what the fellowship goes by.
 */
public final class FellowshipInvitation {

    public static final int CURRENT_DATA_VERSION = 1;

    private final UUID invitationId;
    private final UUID fellowshipId;
    private final UUID invitingIdentityId;
    private final UUID invitingOwnerId;
    private final String invitingCharacterName;
    private final UUID targetIdentityId;
    private final UUID targetOwnerId;
    private final String targetCharacterName;
    private final long createdAt;
    private final long expiresAt;

    public FellowshipInvitation(UUID invitationId,
                           UUID fellowshipId,
                           UUID invitingIdentityId,
                           UUID invitingOwnerId,
                           String invitingCharacterName,
                           UUID targetIdentityId,
                           UUID targetOwnerId,
                           String targetCharacterName,
                           long createdAt,
                           long expiresAt) {
        if (invitationId == null) {
            throw new IllegalArgumentException("invitationId must not be null");
        }
        if (fellowshipId == null) {
            throw new IllegalArgumentException("fellowshipId must not be null");
        }
        if (invitingIdentityId == null || invitingOwnerId == null) {
            throw new IllegalArgumentException("inviting identity must not be null");
        }
        if (targetIdentityId == null || targetOwnerId == null) {
            throw new IllegalArgumentException("target identity must not be null");
        }
        if (invitingIdentityId.equals(targetIdentityId)) {
            throw new IllegalArgumentException("a character cannot invite itself");
        }
        if (createdAt < 0L || expiresAt <= createdAt) {
            throw new IllegalArgumentException("invitation timestamps are invalid");
        }
        this.invitationId = invitationId;
        this.fellowshipId = fellowshipId;
        this.invitingIdentityId = invitingIdentityId;
        this.invitingOwnerId = invitingOwnerId;
        this.invitingCharacterName = normalizeName(invitingCharacterName);
        this.targetIdentityId = targetIdentityId;
        this.targetOwnerId = targetOwnerId;
        this.targetCharacterName = normalizeName(targetCharacterName);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getInvitationId() {
        return this.invitationId;
    }

    public UUID getFellowshipId() {
        return this.fellowshipId;
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

    /** The name trimmed and cut to its bound; empty for none, which each reader's game words ({@link FellowshipNames}). */
    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }
}
