package com.ninuna.losttales.character.switching;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class CharacterSwitchAccountStateTest {

    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID TARGET =
            UUID.fromString("30000000-0000-0000-0000-000000000003");

    /** Ascending cooldowns, the shape the coordinator supplies. */
    private static final long[] COOLDOWNS = new long[] {1000L, 2000L, 4000L};

    /**
     * A fresh account pays the first cooldown and moves one stage up; the plan
     * describes the switch but leaves the live state alone until it is committed.
     */
    @Test
    public void aFirstSwitchPaysTheFirstDurationAndAdvancesOneStage() {
        CharacterSwitchAccountState account = freshAccount();

        CharacterSwitchAccountState.CooldownCommit commit =
                account.planSuccessfulSwitch(5000L, COOLDOWNS);

        assertEquals(1, commit.getStage());
        assertEquals(6000L, commit.getNextAllowedAt());
        assertEquals(5000L, commit.getLastSuccessfulSwitchAt());
        assertEquals(5000L, commit.getDecayAnchorAt());
        assertEquals(5000L, commit.getLastObservedWallClock());
        assertEquals(0, account.getCooldownStage());
        assertEquals(0L, account.getNextAllowedAt());
    }

    /** Each further switch charges the duration for the stage it starts from. */
    @Test
    public void everyStageChargesItsOwnDuration() {
        assertEquals(2000L,
                account(1, 0L, 0L, 0L, 0L)
                        .planSuccessfulSwitch(5000L, COOLDOWNS).getNextAllowedAt()
                        - 5000L);
        assertEquals(4000L,
                account(2, 0L, 0L, 0L, 0L)
                        .planSuccessfulSwitch(5000L, COOLDOWNS).getNextAllowedAt()
                        - 5000L);
    }

    /**
     * The last configured stage is the ceiling: it keeps charging its own
     * duration instead of running off the end of the array.
     */
    @Test
    public void theLastStageIsTheCeilingAndKeepsCharging() {
        CharacterSwitchAccountState.CooldownCommit commit =
                account(2, 0L, 0L, 0L, 0L).planSuccessfulSwitch(5000L, COOLDOWNS);

        assertEquals(2, commit.getStage());
        assertEquals(9000L, commit.getNextAllowedAt());
    }

    /**
     * A stage saved above the configured ceiling — a shortened durations list —
     * is charged as the last stage rather than throwing.
     */
    @Test
    public void aStageAboveTheConfiguredCeilingIsChargedAsTheLastStage() {
        CharacterSwitchAccountState.CooldownCommit commit =
                account(9, 0L, 0L, 0L, 0L).planSuccessfulSwitch(5000L, COOLDOWNS);

        assertEquals(2, commit.getStage());
        assertEquals(9000L, commit.getNextAllowedAt());
    }

    /** A single configured duration means one stage that never escalates. */
    @Test
    public void aSingleDurationNeverEscalates() {
        CharacterSwitchAccountState.CooldownCommit commit =
                freshAccount().planSuccessfulSwitch(
                        5000L, new long[] {1500L});

        assertEquals(0, commit.getStage());
        assertEquals(6500L, commit.getNextAllowedAt());
    }

    /** A negative configured duration is charged as no wait, never as a rebate. */
    @Test
    public void aNegativeDurationIsChargedAsNoWait() {
        assertEquals(5000L, freshAccount()
                .planSuccessfulSwitch(5000L, new long[] {-9000L, 1000L})
                .getNextAllowedAt());
    }

    /** A clock near the end of the range saturates instead of wrapping negative. */
    @Test
    public void aCooldownNearTheEndOfTimeSaturates() {
        assertEquals(Long.MAX_VALUE, freshAccount()
                .planSuccessfulSwitch(Long.MAX_VALUE - 10L, COOLDOWNS)
                .getNextAllowedAt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aPlanWithNoConfiguredDurationsIsRejected() {
        freshAccount().planSuccessfulSwitch(5000L, new long[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aPlanWithoutADurationsArrayIsRejected() {
        freshAccount().planSuccessfulSwitch(5000L, null);
    }

    /**
     * An exempt switch carries the cooldown across untouched, so leaving it
     * cannot be used to shed a wait that was already owed.
     */
    @Test
    public void anExemptSwitchCarriesTheCooldownAcrossUnchanged() {
        CharacterSwitchAccountState account = account(2, 2000L, 1000L, 1000L, 1000L);

        CharacterSwitchAccountState.CooldownCommit commit =
                account.planCooldownExemptSwitch(5000L);

        assertEquals(2, commit.getStage());
        assertEquals(2000L, commit.getNextAllowedAt());
        assertEquals(1000L, commit.getLastSuccessfulSwitchAt());
        assertEquals(1000L, commit.getDecayAnchorAt());
        assertEquals(5000L, commit.getLastObservedWallClock());
    }

    /** The observed clock in an exempt plan only ever moves forward. */
    @Test
    public void anExemptSwitchNeverRewindsTheObservedClock() {
        assertEquals(4000L, account(2, 2000L, 1000L, 1000L, 4000L)
                .planCooldownExemptSwitch(1000L)
                .getLastObservedWallClock());
    }

    /**
     * The journal holds both sides of the switch, so a rollback can put the
     * cooldown back from the record alone without consulting a clock.
     */
    @Test
    public void aRollbackRestoresExactlyWhatTheJournalRecorded() {
        CharacterSwitchAccountState account = account(2, 2000L, 1000L, 1000L, 1000L);
        CharacterSwitchTransaction transaction = journal();

        account.applyCommittedCooldown(transaction);
        assertEquals(4, account.getCooldownStage());
        assertEquals(9000L, account.getNextAllowedAt());
        assertEquals(4000L, account.getLastSuccessfulSwitchAt());
        assertEquals(4000L, account.getDecayAnchorAt());
        assertEquals(4000L, account.getLastObservedWallClock());

        account.restorePreviousCooldown(transaction);
        assertEquals(2, account.getCooldownStage());
        assertEquals(2000L, account.getNextAllowedAt());
        assertEquals(1000L, account.getLastSuccessfulSwitchAt());
        assertEquals(1000L, account.getDecayAnchorAt());
    }

    /**
     * The observed clock is the one value a rollback leaves alone. Winding it
     * back would hand an interrupted switch a way to shorten the next wait.
     */
    @Test
    public void aRollbackDoesNotWindTheObservedClockBack() {
        CharacterSwitchAccountState account = account(2, 2000L, 1000L, 1000L, 1000L);
        CharacterSwitchTransaction transaction = journal();

        account.applyCommittedCooldown(transaction);
        account.restorePreviousCooldown(transaction);

        assertEquals(4000L, account.getLastObservedWallClock());
    }

    /** Applying or restoring nothing leaves the cooldown as it stands. */
    @Test
    public void aMissingJournalChangesNothing() {
        CharacterSwitchAccountState account = account(2, 2000L, 1000L, 1000L, 1000L);

        account.applyCommittedCooldown(null);
        account.restorePreviousCooldown(null);

        assertEquals(2, account.getCooldownStage());
        assertEquals(2000L, account.getNextAllowedAt());
        assertEquals(1000L, account.getLastObservedWallClock());
    }

    /**
     * A wall clock that jumps backwards — a corrected server clock, a restore
     * from a backup — must never shorten a cooldown that is already persisted.
     */
    @Test
    public void aBackwardsWallClockCannotShortenAPersistedCooldown() {
        CharacterSwitchAccountState account = account(2, 9000L, 4000L, 4000L, 8000L);

        assertEquals(8000L, account.observeClock(3000L));
        assertEquals(8000L, account.getLastObservedWallClock());
        assertEquals(8000L, account.observeClock(0L));
        assertEquals(8000L, account.observeClock(-5000L));
        assertEquals(8000L, account.getLastObservedWallClock());
    }

    /** A clock that moves forward is adopted and reported. */
    @Test
    public void aForwardWallClockIsAdopted() {
        CharacterSwitchAccountState account = account(2, 9000L, 4000L, 4000L, 8000L);

        assertEquals(12000L, account.observeClock(12000L));
        assertEquals(12000L, account.getLastObservedWallClock());
    }

    /**
     * Decay charges the period configured for the stage being left, and stops
     * at the first period that has not fully elapsed. The anchor advances by
     * exactly what was spent so the remainder is not lost.
     */
    @Test
    public void decayStepsDownOnePeriodAtATimeAndKeepsTheRemainder() {
        CharacterSwitchAccountState account = account(3, 9000L, 500L, 1000L, 1000L);

        assertTrue(account.applyDecay(1600L, new long[] {0L, 100L, 200L, 400L}, 5));

        assertEquals(1, account.getCooldownStage());
        assertEquals(1600L, account.getDecayAnchorAt());
    }

    /** Decay stops at stage zero rather than going negative. */
    @Test
    public void decayStopsAtStageZero() {
        CharacterSwitchAccountState account = account(2, 9000L, 500L, 1000L, 1000L);

        assertTrue(account.applyDecay(100000L, new long[] {0L, 100L, 200L}, 5));

        assertEquals(0, account.getCooldownStage());
    }

    /**
     * The maximum stage is the configured ceiling, applied before any decay, so
     * a shortened cooldown list pulls a saved stage down with it.
     */
    @Test
    public void aStageAboveTheMaximumIsClampedBeforeDecay() {
        CharacterSwitchAccountState account = account(5, 9000L, 500L, 1000L, 1000L);

        assertTrue(account.applyDecay(1400L, new long[] {0L, 100L, 200L}, 2));

        assertEquals(0, account.getCooldownStage());
        assertEquals(1300L, account.getDecayAnchorAt());
    }

    /** A negative maximum stage is read as zero. */
    @Test
    public void aNegativeMaximumStageClearsTheStage() {
        CharacterSwitchAccountState account = account(3, 9000L, 500L, 1000L, 1000L);

        assertTrue(account.applyDecay(1000L, null, -4));

        assertEquals(0, account.getCooldownStage());
    }

    /** Without configured periods the stage only ever gets clamped. */
    @Test
    public void withoutConfiguredPeriodsNothingDecays() {
        CharacterSwitchAccountState account = account(2, 9000L, 500L, 1000L, 1000L);

        assertFalse(account.applyDecay(100000L, new long[0], 5));
        assertFalse(account.applyDecay(100000L, null, 5));

        assertEquals(2, account.getCooldownStage());
        assertEquals(1000L, account.getDecayAnchorAt());
    }

    /** An account at stage zero has nothing to decay and reports no change. */
    @Test
    public void stageZeroReportsNoChange() {
        CharacterSwitchAccountState account = account(0, 0L, 500L, 1000L, 1000L);

        assertFalse(account.applyDecay(100000L, new long[] {0L, 100L}, 5));

        assertEquals(1000L, account.getDecayAnchorAt());
    }

    /**
     * A missing anchor is seeded from the last switch, so an older manifest
     * does not get its whole absence credited as decay in one call.
     */
    @Test
    public void aMissingAnchorIsSeededFromTheLastSwitch() {
        CharacterSwitchAccountState account = account(1, 9000L, 500L, 0L, 0L);

        assertTrue(account.applyDecay(900L, new long[] {0L, 1000L}, 5));

        assertEquals(500L, account.getDecayAnchorAt());
        assertEquals(1, account.getCooldownStage());
    }

    /** With no switch on record either, the anchor starts from now. */
    @Test
    public void aMissingAnchorWithoutAPriorSwitchStartsFromNow() {
        CharacterSwitchAccountState account = account(1, 9000L, 0L, 0L, 0L);

        assertTrue(account.applyDecay(5000L, new long[] {0L, 1000L}, 5));

        assertEquals(5000L, account.getDecayAnchorAt());
        assertEquals(1, account.getCooldownStage());
    }

    /** A clock behind the anchor decays nothing rather than counting backwards. */
    @Test
    public void aClockBehindTheAnchorDecaysNothing() {
        CharacterSwitchAccountState account = account(2, 9000L, 4000L, 5000L, 5000L);

        assertFalse(account.applyDecay(1000L, new long[] {0L, 100L, 200L}, 5));

        assertEquals(2, account.getCooldownStage());
        assertEquals(5000L, account.getDecayAnchorAt());
    }

    /**
     * A period configured as zero or below still costs a millisecond, so decay
     * advances one stage per call instead of looping on a zero-length period.
     */
    @Test
    public void aNonPositivePeriodStillCostsAMillisecond() {
        CharacterSwitchAccountState account = account(1, 9000L, 500L, 1000L, 1000L);

        assertTrue(account.applyDecay(1001L, new long[] {0L, -5L}, 5));

        assertEquals(0, account.getCooldownStage());
        assertEquals(1001L, account.getDecayAnchorAt());
    }

    /** An operator reset clears the wait and re-anchors decay, clock intact. */
    @Test
    public void aResetClearsTheWaitAndReAnchorsDecay() {
        CharacterSwitchAccountState account = account(3, 9000L, 4000L, 4000L, 8000L);

        account.resetCooldown(1000L);

        assertEquals(0, account.getCooldownStage());
        assertEquals(0L, account.getNextAllowedAt());
        assertEquals(0L, account.getLastSuccessfulSwitchAt());
        assertEquals(1000L, account.getDecayAnchorAt());
        assertEquals(8000L, account.getLastObservedWallClock());
    }

    @Test
    public void freezingAndThawingIsCarried() {
        CharacterSwitchAccountState account = freshAccount();

        assertFalse(account.isFrozen());
        account.setFrozen(true);
        assertTrue(account.isFrozen());
        account.setFrozen(false);
        assertFalse(account.isFrozen());
    }

    /** A pending death carries its timestamp, and clearing takes both away. */
    @Test
    public void aPendingDeathCarriesItsTimestampUntilCleared() {
        CharacterSwitchAccountState account = freshAccount();

        account.markDeathPending(7000L);
        assertTrue(account.isDeathPending());
        assertEquals(7000L, account.getDeathPendingAt());

        account.clearDeathPending();
        assertFalse(account.isDeathPending());
        assertEquals(0L, account.getDeathPendingAt());
    }

    /** A death timestamp from before the epoch is stored as zero. */
    @Test
    public void aNegativeDeathTimestampIsStoredAsZero() {
        CharacterSwitchAccountState account = freshAccount();

        account.markDeathPending(-7000L);

        assertTrue(account.isDeathPending());
        assertEquals(0L, account.getDeathPendingAt());
    }

    @Test
    public void theJournalIsHeldAndReleasedAsGiven() {
        CharacterSwitchAccountState account = freshAccount();
        CharacterSwitchTransaction transaction = journal();

        assertNull(account.getTransaction());
        account.setTransaction(transaction);
        assertSame(transaction, account.getTransaction());
        account.setTransaction(null);
        assertNull(account.getTransaction());
    }

    /** A new account owes nothing and holds no journal. */
    @Test
    public void aNewAccountStartsClean() {
        CharacterSwitchAccountState account = freshAccount();

        assertEquals(OWNER, account.getOwnerId());
        assertEquals(0, account.getCooldownStage());
        assertEquals(0L, account.getNextAllowedAt());
        assertEquals(0L, account.getLastSuccessfulSwitchAt());
        assertEquals(0L, account.getDecayAnchorAt());
        assertEquals(0L, account.getLastObservedWallClock());
        assertFalse(account.isFrozen());
        assertFalse(account.isDeathPending());
        assertEquals(0L, account.getDeathPendingAt());
        assertNull(account.getTransaction());
    }

    @Test(expected = IllegalArgumentException.class)
    public void anAccountWithoutAnOwnerIsRejected() {
        new CharacterSwitchAccountState(null);
    }

    /** A version-1 manifest carries no death lockout and reads as none pending. */
    @Test
    public void aVersionOneManifestHasNoPendingDeath() {
        CharacterSwitchTransaction transaction = journal();
        CharacterSwitchAccountState account = new CharacterSwitchAccountState(
                OWNER, 2, 2000L, 1000L, 1000L, 1000L, true, transaction);

        assertEquals(2, account.getCooldownStage());
        assertEquals(2000L, account.getNextAllowedAt());
        assertEquals(1000L, account.getLastSuccessfulSwitchAt());
        assertEquals(1000L, account.getDecayAnchorAt());
        assertEquals(1000L, account.getLastObservedWallClock());
        assertTrue(account.isFrozen());
        assertFalse(account.isDeathPending());
        assertEquals(0L, account.getDeathPendingAt());
        assertSame(transaction, account.getTransaction());
    }

    /** Negative stored values are clamped so a corrupt manifest owes nothing. */
    @Test
    public void negativeStoredValuesAreClamped() {
        CharacterSwitchAccountState account = new CharacterSwitchAccountState(
                OWNER, -3, -1L, -2L, -3L, -4L, false, true, -5L, null);

        assertEquals(0, account.getCooldownStage());
        assertEquals(0L, account.getNextAllowedAt());
        assertEquals(0L, account.getLastSuccessfulSwitchAt());
        assertEquals(0L, account.getDecayAnchorAt());
        assertEquals(0L, account.getLastObservedWallClock());
        assertTrue(account.isDeathPending());
        assertEquals(0L, account.getDeathPendingAt());
    }

    /** A death timestamp without the flag is dropped rather than half-kept. */
    @Test
    public void aDeathTimestampWithoutTheFlagIsDropped() {
        CharacterSwitchAccountState account = new CharacterSwitchAccountState(
                OWNER, 1, 2000L, 1000L, 1000L, 1000L, false, false, 900L, null);

        assertFalse(account.isDeathPending());
        assertEquals(0L, account.getDeathPendingAt());
    }

    private static CharacterSwitchAccountState freshAccount() {
        return new CharacterSwitchAccountState(OWNER);
    }

    private static CharacterSwitchAccountState account(int cooldownStage,
                                                       long nextAllowedAt,
                                                       long lastSuccessfulSwitchAt,
                                                       long decayAnchorAt,
                                                       long lastObservedWallClock) {
        return new CharacterSwitchAccountState(
                OWNER,
                cooldownStage,
                nextAllowedAt,
                lastSuccessfulSwitchAt,
                decayAnchorAt,
                lastObservedWallClock,
                false,
                false,
                0L,
                null);
    }

    /** A prepared journal whose pre- and post-switch cooldowns differ in every field. */
    private static CharacterSwitchTransaction journal() {
        return new CharacterSwitchTransaction(
                UUID.fromString("50000000-0000-0000-0000-000000000005"),
                SOURCE,
                TARGET,
                10L,
                11L,
                3000L,
                7L,
                8,
                2,
                2000L,
                1000L,
                1000L,
                1000L,
                4,
                9000L,
                4000L,
                4000L,
                4000L,
                20L,
                21L,
                CharacterSwitchTransactionStatus.PREPARED,
                0L);
    }
}
