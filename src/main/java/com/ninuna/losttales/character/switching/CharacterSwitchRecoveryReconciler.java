package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.character.validation.CharacterErrorId;

import java.util.UUID;

/**
 * Resolves a durable switch journal against the roster identity that survived
 * a restart. The mutation is storage-independent so every recovery branch can
 * be validated without constructing a live player or world.
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
            default:
                throw new IllegalStateException(
                        "Unhandled character switch recovery action " + action);
        }
        return new Result(action, true,
                action == Action.REQUIRE_MANUAL_RECOVERY
                        ? CharacterErrorId.SWITCH_RECOVERY_REQUIRED
                        : CharacterErrorId.NONE);
    }

    public static Action decide(UUID activeCharacterId,
                                CharacterSwitchTransaction transaction) {
        if (transaction == null) {
            return Action.NO_JOURNAL;
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
        REQUIRE_MANUAL_RECOVERY
    }

    public static final class Result {
        private static final Result NO_JOURNAL = new Result(
                Action.NO_JOURNAL, false, CharacterErrorId.NONE);

        private final Action action;
        private final boolean changed;
        private final CharacterErrorId errorId;

        private Result(Action action, boolean changed,
                       CharacterErrorId errorId) {
            this.action = action;
            this.changed = changed;
            this.errorId = errorId;
        }

        public Action getAction() {
            return this.action;
        }

        public boolean isChanged() {
            return this.changed;
        }

        public CharacterErrorId getErrorId() {
            return this.errorId;
        }
    }
}
