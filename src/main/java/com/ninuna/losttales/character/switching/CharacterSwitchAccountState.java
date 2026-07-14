package com.ninuna.losttales.character.switching;

import java.util.UUID;

/** Persistent account-owned switch cooldown, lifecycle lockout, and journal state. */
public final class CharacterSwitchAccountState {

    public static final int CURRENT_DATA_VERSION = 2;

    private final UUID ownerId;
    private int cooldownStage;
    private long nextAllowedAt;
    private long lastSuccessfulSwitchAt;
    private long decayAnchorAt;
    private long lastObservedWallClock;
    private boolean frozen;
    private boolean deathPending;
    private long deathPendingAt;
    private CharacterSwitchTransaction transaction;

    public CharacterSwitchAccountState(UUID ownerId) {
        this(ownerId, 0, 0L, 0L, 0L, 0L,
                false, false, 0L, null);
    }

    /** Compatibility constructor for version-1 account manifests. */
    public CharacterSwitchAccountState(UUID ownerId,
                                       int cooldownStage,
                                       long nextAllowedAt,
                                       long lastSuccessfulSwitchAt,
                                       long decayAnchorAt,
                                       long lastObservedWallClock,
                                       boolean frozen,
                                       CharacterSwitchTransaction transaction) {
        this(ownerId, cooldownStage, nextAllowedAt, lastSuccessfulSwitchAt,
                decayAnchorAt, lastObservedWallClock, frozen,
                false, 0L, transaction);
    }

    public CharacterSwitchAccountState(UUID ownerId,
                                       int cooldownStage,
                                       long nextAllowedAt,
                                       long lastSuccessfulSwitchAt,
                                       long decayAnchorAt,
                                       long lastObservedWallClock,
                                       boolean frozen,
                                       boolean deathPending,
                                       long deathPendingAt,
                                       CharacterSwitchTransaction transaction) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        this.ownerId = ownerId;
        this.cooldownStage = Math.max(0, cooldownStage);
        this.nextAllowedAt = Math.max(0L, nextAllowedAt);
        this.lastSuccessfulSwitchAt = Math.max(0L, lastSuccessfulSwitchAt);
        this.decayAnchorAt = Math.max(0L, decayAnchorAt);
        this.lastObservedWallClock = Math.max(0L, lastObservedWallClock);
        this.frozen = frozen;
        this.deathPending = deathPending;
        this.deathPendingAt = deathPending ? Math.max(0L, deathPendingAt) : 0L;
        this.transaction = transaction;
    }

    public UUID getOwnerId() { return this.ownerId; }
    public int getCooldownStage() { return this.cooldownStage; }
    public long getNextAllowedAt() { return this.nextAllowedAt; }
    public long getLastSuccessfulSwitchAt() { return this.lastSuccessfulSwitchAt; }
    public long getDecayAnchorAt() { return this.decayAnchorAt; }
    public long getLastObservedWallClock() { return this.lastObservedWallClock; }
    public boolean isFrozen() { return this.frozen; }
    public boolean isDeathPending() { return this.deathPending; }
    public long getDeathPendingAt() { return this.deathPendingAt; }
    public CharacterSwitchTransaction getTransaction() { return this.transaction; }

    /** Never lets a backwards wall-clock adjustment shorten a persisted cooldown. */
    public long observeClock(long wallClockMillis) {
        long supplied = Math.max(0L, wallClockMillis);
        if (supplied > this.lastObservedWallClock) {
            this.lastObservedWallClock = supplied;
        }
        return Math.max(supplied, this.lastObservedWallClock);
    }

    public boolean applyDecay(long safeNow, long[] decayDurationsMillis, int maximumStage) {
        int maxStage = Math.max(0, maximumStage);
        boolean changed = false;
        if (this.cooldownStage > maxStage) {
            this.cooldownStage = maxStage;
            changed = true;
        }
        if (this.cooldownStage <= 0 || decayDurationsMillis == null
                || decayDurationsMillis.length == 0) {
            return changed;
        }
        if (this.decayAnchorAt <= 0L) {
            this.decayAnchorAt = this.lastSuccessfulSwitchAt > 0L
                    ? this.lastSuccessfulSwitchAt : safeNow;
            changed = true;
        }

        while (this.cooldownStage > 0) {
            int index = Math.min(this.cooldownStage, decayDurationsMillis.length - 1);
            long required = Math.max(1L, decayDurationsMillis[index]);
            if (safeNow < this.decayAnchorAt
                    || safeNow - this.decayAnchorAt < required) {
                break;
            }
            this.decayAnchorAt += required;
            this.cooldownStage--;
            changed = true;
        }
        return changed;
    }

    public CooldownCommit planSuccessfulSwitch(long safeNow, long[] cooldownDurationsMillis) {
        if (cooldownDurationsMillis == null || cooldownDurationsMillis.length == 0) {
            throw new IllegalArgumentException("At least one cooldown duration is required");
        }
        int appliedStage = Math.min(Math.max(0, this.cooldownStage),
                cooldownDurationsMillis.length - 1);
        long duration = Math.max(0L, cooldownDurationsMillis[appliedStage]);
        long nextAllowed = safeAdd(safeNow, duration);
        int nextStage = Math.min(appliedStage + 1, cooldownDurationsMillis.length - 1);
        return new CooldownCommit(nextStage, nextAllowed, safeNow, safeNow, safeNow);
    }

    /** A creative switch preserves, but does not enforce or escalate, cooldown state. */
    public CooldownCommit planCooldownExemptSwitch(long safeNow) {
        return new CooldownCommit(
                this.cooldownStage,
                this.nextAllowedAt,
                this.lastSuccessfulSwitchAt,
                this.decayAnchorAt,
                Math.max(this.lastObservedWallClock, Math.max(0L, safeNow)));
    }

    public void applyCommittedCooldown(CharacterSwitchTransaction transaction) {
        if (transaction == null) {
            return;
        }
        setCooldownValues(
                transaction.getCommittedCooldownStage(),
                transaction.getCommittedNextAllowedAt(),
                transaction.getCommittedLastSuccessfulSwitchAt(),
                transaction.getCommittedDecayAnchorAt(),
                transaction.getCommittedLastObservedWallClock());
    }

    public void restorePreviousCooldown(CharacterSwitchTransaction transaction) {
        if (transaction == null) {
            return;
        }
        setCooldownValues(
                transaction.getPreviousCooldownStage(),
                transaction.getPreviousNextAllowedAt(),
                transaction.getPreviousLastSuccessfulSwitchAt(),
                transaction.getPreviousDecayAnchorAt(),
                transaction.getPreviousLastObservedWallClock());
    }

    public void resetCooldown(long safeNow) {
        this.cooldownStage = 0;
        this.nextAllowedAt = 0L;
        this.lastSuccessfulSwitchAt = 0L;
        this.decayAnchorAt = safeNow;
        this.lastObservedWallClock = Math.max(this.lastObservedWallClock, safeNow);
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public void markDeathPending(long timestamp) {
        this.deathPending = true;
        this.deathPendingAt = Math.max(0L, timestamp);
    }

    public void clearDeathPending() {
        this.deathPending = false;
        this.deathPendingAt = 0L;
    }

    public void setTransaction(CharacterSwitchTransaction transaction) {
        this.transaction = transaction;
    }

    private void setCooldownValues(int stage, long nextAllowedAt,
                                   long lastSuccessfulSwitchAt, long decayAnchorAt,
                                   long lastObservedWallClock) {
        this.cooldownStage = Math.max(0, stage);
        this.nextAllowedAt = Math.max(0L, nextAllowedAt);
        this.lastSuccessfulSwitchAt = Math.max(0L, lastSuccessfulSwitchAt);
        this.decayAnchorAt = Math.max(0L, decayAnchorAt);
        this.lastObservedWallClock = Math.max(this.lastObservedWallClock,
                Math.max(0L, lastObservedWallClock));
    }

    private static long safeAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    public static final class CooldownCommit {
        private final int stage;
        private final long nextAllowedAt;
        private final long lastSuccessfulSwitchAt;
        private final long decayAnchorAt;
        private final long lastObservedWallClock;

        private CooldownCommit(int stage, long nextAllowedAt,
                               long lastSuccessfulSwitchAt, long decayAnchorAt,
                               long lastObservedWallClock) {
            this.stage = stage;
            this.nextAllowedAt = nextAllowedAt;
            this.lastSuccessfulSwitchAt = lastSuccessfulSwitchAt;
            this.decayAnchorAt = decayAnchorAt;
            this.lastObservedWallClock = lastObservedWallClock;
        }

        public int getStage() { return this.stage; }
        public long getNextAllowedAt() { return this.nextAllowedAt; }
        public long getLastSuccessfulSwitchAt() { return this.lastSuccessfulSwitchAt; }
        public long getDecayAnchorAt() { return this.decayAnchorAt; }
        public long getLastObservedWallClock() { return this.lastObservedWallClock; }
    }
}
