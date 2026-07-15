package com.ninuna.losttales.character.lore.transfer;

import com.ninuna.losttales.character.model.CharacterRoster;

import java.util.Locale;
import java.util.UUID;

/** Immutable crash-recovery marker for one claim or release operation. */
public final class LoreCharacterTransferRecord {

    public static final int CURRENT_DATA_VERSION = 1;

    public enum Type {
        CLAIM,
        RELEASE
    }

    private final UUID transactionId;
    private final Type type;
    private final String loreCharacterId;
    private final UUID characterId;
    private final UUID sourceOwnerId;
    private final UUID targetOwnerId;
    private final int targetSlot;
    private final long expectedOwnershipRevision;
    private final int step;
    private final long createdAt;

    public LoreCharacterTransferRecord(
            UUID transactionId,
            Type type,
            String loreCharacterId,
            UUID characterId,
            UUID sourceOwnerId,
            UUID targetOwnerId,
            int targetSlot,
            long expectedOwnershipRevision,
            int step,
            long createdAt) {
        String normalized = normalize(loreCharacterId);
        if (transactionId == null || type == null || normalized.length() == 0
                || characterId == null || expectedOwnershipRevision < 0L
                || step < 0 || step > 3
                || type == Type.CLAIM && (targetOwnerId == null
                || !CharacterRoster.isValidSlotIndex(targetSlot))
                || type == Type.RELEASE && sourceOwnerId == null) {
            throw new IllegalArgumentException(
                    "Lore-character transfer record is incomplete");
        }
        this.transactionId = transactionId;
        this.type = type;
        this.loreCharacterId = normalized;
        this.characterId = characterId;
        this.sourceOwnerId = sourceOwnerId;
        this.targetOwnerId = targetOwnerId;
        this.targetSlot = targetSlot;
        this.expectedOwnershipRevision = expectedOwnershipRevision;
        this.step = step;
        this.createdAt = Math.max(0L, createdAt);
    }

    public LoreCharacterTransferRecord advance(int nextStep) {
        if (nextStep != this.step + 1) {
            throw new IllegalArgumentException("Transfer steps must advance once");
        }
        return new LoreCharacterTransferRecord(
                this.transactionId, this.type, this.loreCharacterId,
                this.characterId, this.sourceOwnerId, this.targetOwnerId,
                this.targetSlot, this.expectedOwnershipRevision, nextStep,
                this.createdAt);
    }

    public UUID getTransactionId() { return this.transactionId; }
    public Type getType() { return this.type; }
    public String getLoreCharacterId() { return this.loreCharacterId; }
    public UUID getCharacterId() { return this.characterId; }
    public UUID getSourceOwnerId() { return this.sourceOwnerId; }
    public UUID getTargetOwnerId() { return this.targetOwnerId; }
    public int getTargetSlot() { return this.targetSlot; }
    public long getExpectedOwnershipRevision() {
        return this.expectedOwnershipRevision;
    }
    public int getStep() { return this.step; }
    public long getCreatedAt() { return this.createdAt; }

    public boolean involves(UUID ownerId) {
        return ownerId != null && (ownerId.equals(this.sourceOwnerId)
                || ownerId.equals(this.targetOwnerId));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
