package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.character.validation.CharacterErrorId;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class CharacterSwitchRecoveryReconcilerTest {

    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID TARGET =
            UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID UNKNOWN =
            UUID.fromString("40000000-0000-0000-0000-000000000004");

    @Test
    public void preparedTargetJournalSurvivesUntilDurableFinalization() {
        CharacterSwitchTransaction transaction = transaction(
                SOURCE, CharacterSwitchTransactionStatus.PREPARED);
        CharacterSwitchAccountState account = account(transaction);

        CharacterSwitchRecoveryReconciler.Result result =
                CharacterSwitchRecoveryReconciler.reconcile(
                        TARGET, account, 5000L);

        assertEquals(CharacterSwitchRecoveryReconciler.Action.COMMIT_TARGET,
                result.getAction());
        assertSame(transaction, account.getTransaction());
        assertEquals(CharacterSwitchTransactionStatus.COMMITTED,
                transaction.getStatus());
        assertEquals(4, account.getCooldownStage());
        assertEquals(9000L, account.getNextAllowedAt());
    }

    @Test
    public void durablePreparedTargetIsCommittedAndClearedAtomically() {
        CharacterSwitchAccountState account = account(transaction(
                SOURCE, CharacterSwitchTransactionStatus.PREPARED));

        CharacterSwitchRecoveryReconciler.Result result =
                CharacterSwitchRecoveryReconciler
                        .finalizeAfterDurablePlayerSave(
                                TARGET, account, 5000L);

        assertEquals(CharacterSwitchRecoveryReconciler.Action.CLEAR_JOURNAL,
                result.getAction());
        assertEquals(CharacterErrorId.NONE, result.getErrorId());
        assertNull(account.getTransaction());
        assertEquals(4, account.getCooldownStage());
        assertEquals(9000L, account.getNextAllowedAt());
    }

    @Test
    public void durablePreparedSourceIsAbortedAndCleared() {
        CharacterSwitchAccountState account = account(transaction(
                SOURCE, CharacterSwitchTransactionStatus.PREPARED));

        CharacterSwitchRecoveryReconciler.Result result =
                CharacterSwitchRecoveryReconciler
                        .finalizeAfterDurablePlayerSave(
                                SOURCE, account, 5000L);

        assertEquals(CharacterSwitchRecoveryReconciler.Action.CLEAR_JOURNAL,
                result.getAction());
        assertNull(account.getTransaction());
        assertEquals(2, account.getCooldownStage());
        assertEquals(2000L, account.getNextAllowedAt());
    }

    @Test
    public void alreadyCommittedTargetClearsAfterDurablePlayerSave() {
        CharacterSwitchAccountState account = account(transaction(
                SOURCE, CharacterSwitchTransactionStatus.COMMITTED));

        CharacterSwitchRecoveryReconciler.Result result =
                CharacterSwitchRecoveryReconciler
                        .finalizeAfterDurablePlayerSave(
                                TARGET, account, 5000L);

        assertEquals(CharacterSwitchRecoveryReconciler.Action.CLEAR_JOURNAL,
                result.getAction());
        assertNull(account.getTransaction());
    }

    @Test
    public void firstImportWithoutSourceCanAbortAndClear() {
        CharacterSwitchAccountState account = account(transaction(
                null, CharacterSwitchTransactionStatus.PREPARED));

        CharacterSwitchRecoveryReconciler.Result result =
                CharacterSwitchRecoveryReconciler
                        .finalizeAfterDurablePlayerSave(
                                null, account, 5000L);

        assertEquals(CharacterSwitchRecoveryReconciler.Action.CLEAR_JOURNAL,
                result.getAction());
        assertNull(account.getTransaction());
        assertFalse(account.isFrozen());
    }

    @Test
    public void mismatchedRosterRemainsFrozenForManualRecovery() {
        CharacterSwitchTransaction transaction = transaction(
                SOURCE, CharacterSwitchTransactionStatus.COMMITTED);
        CharacterSwitchAccountState account = account(transaction);

        CharacterSwitchRecoveryReconciler.Result result =
                CharacterSwitchRecoveryReconciler
                        .finalizeAfterDurablePlayerSave(
                                UNKNOWN, account, 5000L);

        assertEquals(
                CharacterSwitchRecoveryReconciler.Action.REQUIRE_MANUAL_RECOVERY,
                result.getAction());
        assertEquals(CharacterErrorId.SWITCH_RECOVERY_REQUIRED,
                result.getErrorId());
        assertSame(transaction, account.getTransaction());
        assertEquals(CharacterSwitchTransactionStatus.RECOVERY_REQUIRED,
                transaction.getStatus());
        assertTrue(account.isFrozen());
    }

    @Test
    public void noJournalFinalizationIsIdempotent() {
        CharacterSwitchAccountState account = account(null);

        CharacterSwitchRecoveryReconciler.Result result =
                CharacterSwitchRecoveryReconciler
                        .finalizeAfterDurablePlayerSave(
                                TARGET, account, 5000L);

        assertEquals(CharacterSwitchRecoveryReconciler.Action.NO_JOURNAL,
                result.getAction());
        assertEquals(CharacterErrorId.NONE, result.getErrorId());
        assertNull(account.getTransaction());
    }

    private static CharacterSwitchAccountState account(
            CharacterSwitchTransaction transaction) {
        return new CharacterSwitchAccountState(
                OWNER,
                2,
                2000L,
                1000L,
                1000L,
                1000L,
                false,
                false,
                0L,
                transaction);
    }

    private static CharacterSwitchTransaction transaction(
            UUID source,
            CharacterSwitchTransactionStatus status) {
        return new CharacterSwitchTransaction(
                UUID.fromString("50000000-0000-0000-0000-000000000005"),
                source,
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
                source == null ? -1L : 20L,
                21L,
                status,
                status == CharacterSwitchTransactionStatus.PREPARED
                        ? 0L : 4500L);
    }
}
