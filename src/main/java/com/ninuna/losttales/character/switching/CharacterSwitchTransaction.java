package com.ninuna.losttales.character.switching;

import java.util.UUID;

/** Persistent intent/commit record used to reconcile an interrupted switch. */
public final class CharacterSwitchTransaction {

    public static final int CURRENT_DATA_VERSION = 2;

    private final UUID transactionId;
    private final UUID sourceCharacterId;
    private final UUID targetCharacterId;
    private final long sourceRosterRevision;
    private final long targetRosterRevision;
    private final long preparedAt;
    private final long requestEpoch;
    private final int requestId;

    private final int previousCooldownStage;
    private final long previousNextAllowedAt;
    private final long previousLastSuccessfulSwitchAt;
    private final long previousDecayAnchorAt;
    private final long previousLastObservedWallClock;

    private final int committedCooldownStage;
    private final long committedNextAllowedAt;
    private final long committedLastSuccessfulSwitchAt;
    private final long committedDecayAnchorAt;
    private final long committedLastObservedWallClock;

    /** -1 for version-1 journals that predate player-state generations. */
    private long sourceStateGeneration;
    /** -1 for version-1 journals that predate player-state generations. */
    private long targetStateGeneration;

    private CharacterSwitchTransactionStatus status;
    private long completedAt;

    /** Compatibility constructor for version-1 journals and focused tests. */
    public CharacterSwitchTransaction(UUID transactionId,
                                      UUID sourceCharacterId,
                                      UUID targetCharacterId,
                                      long sourceRosterRevision,
                                      long targetRosterRevision,
                                      long preparedAt,
                                      long requestEpoch,
                                      int requestId,
                                      int previousCooldownStage,
                                      long previousNextAllowedAt,
                                      long previousLastSuccessfulSwitchAt,
                                      long previousDecayAnchorAt,
                                      long previousLastObservedWallClock,
                                      int committedCooldownStage,
                                      long committedNextAllowedAt,
                                      long committedLastSuccessfulSwitchAt,
                                      long committedDecayAnchorAt,
                                      long committedLastObservedWallClock,
                                      CharacterSwitchTransactionStatus status,
                                      long completedAt) {
        this(transactionId, sourceCharacterId, targetCharacterId,
                sourceRosterRevision, targetRosterRevision, preparedAt,
                requestEpoch, requestId,
                previousCooldownStage, previousNextAllowedAt,
                previousLastSuccessfulSwitchAt, previousDecayAnchorAt,
                previousLastObservedWallClock,
                committedCooldownStage, committedNextAllowedAt,
                committedLastSuccessfulSwitchAt, committedDecayAnchorAt,
                committedLastObservedWallClock,
                -1L, -1L, status, completedAt);
    }

    public CharacterSwitchTransaction(UUID transactionId,
                                      UUID sourceCharacterId,
                                      UUID targetCharacterId,
                                      long sourceRosterRevision,
                                      long targetRosterRevision,
                                      long preparedAt,
                                      long requestEpoch,
                                      int requestId,
                                      int previousCooldownStage,
                                      long previousNextAllowedAt,
                                      long previousLastSuccessfulSwitchAt,
                                      long previousDecayAnchorAt,
                                      long previousLastObservedWallClock,
                                      int committedCooldownStage,
                                      long committedNextAllowedAt,
                                      long committedLastSuccessfulSwitchAt,
                                      long committedDecayAnchorAt,
                                      long committedLastObservedWallClock,
                                      long sourceStateGeneration,
                                      long targetStateGeneration,
                                      CharacterSwitchTransactionStatus status,
                                      long completedAt) {
        if (transactionId == null || targetCharacterId == null) {
            throw new IllegalArgumentException(
                    "transaction and target character IDs are required");
        }
        this.transactionId = transactionId;
        this.sourceCharacterId = sourceCharacterId;
        this.targetCharacterId = targetCharacterId;
        this.sourceRosterRevision = Math.max(0L, sourceRosterRevision);
        this.targetRosterRevision = Math.max(this.sourceRosterRevision,
                targetRosterRevision);
        this.preparedAt = Math.max(0L, preparedAt);
        this.requestEpoch = requestEpoch;
        this.requestId = requestId;
        this.previousCooldownStage = Math.max(0, previousCooldownStage);
        this.previousNextAllowedAt = Math.max(0L, previousNextAllowedAt);
        this.previousLastSuccessfulSwitchAt = Math.max(0L,
                previousLastSuccessfulSwitchAt);
        this.previousDecayAnchorAt = Math.max(0L, previousDecayAnchorAt);
        this.previousLastObservedWallClock = Math.max(0L,
                previousLastObservedWallClock);
        this.committedCooldownStage = Math.max(0, committedCooldownStage);
        this.committedNextAllowedAt = Math.max(0L, committedNextAllowedAt);
        this.committedLastSuccessfulSwitchAt = Math.max(0L,
                committedLastSuccessfulSwitchAt);
        this.committedDecayAnchorAt = Math.max(0L, committedDecayAnchorAt);
        this.committedLastObservedWallClock = Math.max(0L,
                committedLastObservedWallClock);
        this.sourceStateGeneration = sourceStateGeneration <= 0L
                ? -1L : sourceStateGeneration;
        this.targetStateGeneration = targetStateGeneration <= 0L
                ? -1L : targetStateGeneration;
        this.status = status == null
                ? CharacterSwitchTransactionStatus.RECOVERY_REQUIRED : status;
        this.completedAt = Math.max(0L, completedAt);
    }

    public UUID getTransactionId() { return this.transactionId; }
    public UUID getSourceCharacterId() { return this.sourceCharacterId; }
    public UUID getTargetCharacterId() { return this.targetCharacterId; }
    public long getSourceRosterRevision() { return this.sourceRosterRevision; }
    public long getTargetRosterRevision() { return this.targetRosterRevision; }
    public long getPreparedAt() { return this.preparedAt; }
    public long getRequestEpoch() { return this.requestEpoch; }
    public int getRequestId() { return this.requestId; }
    public int getPreviousCooldownStage() { return this.previousCooldownStage; }
    public long getPreviousNextAllowedAt() { return this.previousNextAllowedAt; }
    public long getPreviousLastSuccessfulSwitchAt() { return this.previousLastSuccessfulSwitchAt; }
    public long getPreviousDecayAnchorAt() { return this.previousDecayAnchorAt; }
    public long getPreviousLastObservedWallClock() { return this.previousLastObservedWallClock; }
    public int getCommittedCooldownStage() { return this.committedCooldownStage; }
    public long getCommittedNextAllowedAt() { return this.committedNextAllowedAt; }
    public long getCommittedLastSuccessfulSwitchAt() { return this.committedLastSuccessfulSwitchAt; }
    public long getCommittedDecayAnchorAt() { return this.committedDecayAnchorAt; }
    public long getCommittedLastObservedWallClock() { return this.committedLastObservedWallClock; }
    public long getSourceStateGeneration() { return this.sourceStateGeneration; }
    public void setSourceStateGeneration(long generation) {
        if (generation <= 0L) {
            throw new IllegalArgumentException("source state generation must be positive");
        }
        this.sourceStateGeneration = generation;
    }
    public long getTargetStateGeneration() { return this.targetStateGeneration; }
    public void setTargetStateGeneration(long generation) {
        if (generation <= 0L) {
            throw new IllegalArgumentException("target state generation must be positive");
        }
        this.targetStateGeneration = generation;
    }
    public boolean hasPlayerStateGenerations() {
        return this.targetStateGeneration > 0L
                && (this.sourceCharacterId == null || this.sourceStateGeneration > 0L);
    }
    public CharacterSwitchTransactionStatus getStatus() { return this.status; }
    public long getCompletedAt() { return this.completedAt; }

    public void markCommitted(long timestamp) {
        this.status = CharacterSwitchTransactionStatus.COMMITTED;
        this.completedAt = Math.max(0L, timestamp);
    }

    public void markAborted(long timestamp) {
        this.status = CharacterSwitchTransactionStatus.ABORTED;
        this.completedAt = Math.max(0L, timestamp);
    }

    public void markRecoveryRequired(long timestamp) {
        this.status = CharacterSwitchTransactionStatus.RECOVERY_REQUIRED;
        this.completedAt = Math.max(0L, timestamp);
    }
}
