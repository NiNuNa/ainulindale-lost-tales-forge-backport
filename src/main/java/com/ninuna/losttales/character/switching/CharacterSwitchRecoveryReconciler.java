package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.character.validation.CharacterErrorId;

import java.util.UUID;

/**
 * Settles a switch journal against the character the roster plays after a
 * restart. It touches no store and no player, so every case can be tested
 * without a world.
 */
public final class CharacterSwitchRecoveryReconciler {

    private CharacterSwitchRecoveryReconciler() {}

    public static Result reconcile(UUID activeCharacterId,
                                   CharacterSwitchAccountState account,
                                   long timestamp) {
        if (account == null) {
            throw new IllegalArgumentException("account must not be null");
        }
        CharacterSwitchTransaction transaction = account.getTransaction();
        if (transaction == null) {
            return Result.NO_JOURNAL;
        }

        Action action = decide(activeCharacterId, transaction);
        long safeTimestamp = Math.max(0L, timestamp);
        switch (action) {
            case CLEAR_JOURNAL:
                account.setTransaction(null);
                break;
            case COMMIT_TARGET:
                account.applyCommittedCooldown(transaction);
                transaction.markCommitted(safeTimestamp);
                break;
            case ABORT_TO_SOURCE:
                account.restorePreviousCooldown(transaction);
                transaction.markAborted(safeTimestamp);
                break;
            case REQUIRE_MANUAL_RECOVERY:
                transaction.markRecoveryRequired(safeTimestamp);
                account.setFrozen(true);
                break;
            case HOLD:
                return new Result(false, CharacterErrorId.SWITCH_RECOVERY_REQUIRED);
            default:
                throw new IllegalStateException(
                        "Unhandled character switch recovery action " + action);
        }
        return new Result(true,
                action == Action.REQUIRE_MANUAL_RECOVERY
                        ? CharacterErrorId.SWITCH_RECOVERY_REQUIRED
                        : CharacterErrorId.NONE);
    }

    /**
     * Completes reconciliation after the caller has durably saved the live
     * player represented by {@code activeCharacterId}. A prepared transaction
     * first applies the correct cooldown outcome; the second reconciliation
     * then removes the now-redundant journal. A mismatched roster remains
     * frozen for manual recovery.
     */
    public static Result finalizeAfterDurablePlayerSave(
            UUID activeCharacterId,
            CharacterSwitchAccountState account,
            long timestamp) {
        Result first = reconcile(activeCharacterId, account, timestamp);
        if (first.getErrorId() != CharacterErrorId.NONE
                || account == null || account.getTransaction() == null) {
            return first;
        }
        Result second = reconcile(activeCharacterId, account, timestamp);
        return second.getErrorId() == CharacterErrorId.NONE ? second : first;
    }

    /**
     * Whether the journal still describes work the live player has to be
     * reconciled against, from its status alone.
     *
     * <p>An {@code ABORTED} journal describes a switch whose rollback
     * already put the player back on the source. A held journal is either
     * an attempt that failed part-way, held for an operator, or one an
     * operator discarded, whose source comes back by
     * {@link CharacterSwitchSourceRestore} and not by this. Applying a
     * generation of either again would replace the state the player is on
     * with the state they were on when the attempt was made — for a
     * {@code RECOVERY_REQUIRED} journal by repeating the failure that
     * marked it, on every login. All are answered here rather than by
     * {@link #decide}, because they are true whichever identity the
     * roster ended up on.</p>
     */
    public static boolean requiresLiveReconciliation(
            CharacterSwitchTransaction transaction) {
        if (transaction == null) {
            return false;
        }
        return transaction.getStatus() != CharacterSwitchTransactionStatus.ABORTED
                && !transaction.isHeld();
    }

    /** What to do with the journal; a held journal is always left as it is. */
    public static Action decide(UUID activeCharacterId,
                                CharacterSwitchTransaction transaction) {
        if (transaction == null) {
            return Action.NO_JOURNAL;
        }
        if (transaction.isHeld()) {
            return Action.HOLD;
        }
        boolean targetActive = equalsUuid(
                activeCharacterId, transaction.getTargetCharacterId());
        boolean sourceActive = equalsUuid(
                activeCharacterId, transaction.getSourceCharacterId());

        if (transaction.getStatus() == CharacterSwitchTransactionStatus.ABORTED) {
            return sourceActive
                    ? Action.CLEAR_JOURNAL
                    : Action.REQUIRE_MANUAL_RECOVERY;
        }
        if (targetActive) {
            return transaction.getStatus()
                    == CharacterSwitchTransactionStatus.COMMITTED
                    ? Action.CLEAR_JOURNAL
                    : Action.COMMIT_TARGET;
        }
        if (sourceActive) {
            return Action.ABORT_TO_SOURCE;
        }
        return Action.REQUIRE_MANUAL_RECOVERY;
    }

    private static boolean equalsUuid(UUID left, UUID right) {
        return left == null ? right == null : left.equals(right);
    }

    public enum Action {
        NO_JOURNAL,
        CLEAR_JOURNAL,
        COMMIT_TARGET,
        ABORT_TO_SOURCE,
        REQUIRE_MANUAL_RECOVERY,
        /** A held journal: nothing changes, and switching stays off. */
        HOLD
    }

    public static final class Result {
        private static final Result NO_JOURNAL = new Result(
                false, CharacterErrorId.NONE);

        private final boolean changed;
        private final CharacterErrorId errorId;

        private Result(boolean changed, CharacterErrorId errorId) {
            this.changed = changed;
            this.errorId = errorId;
        }

        public boolean isChanged() {
            return this.changed;
        }

        public CharacterErrorId getErrorId() {
            return this.errorId;
        }
    }
}
