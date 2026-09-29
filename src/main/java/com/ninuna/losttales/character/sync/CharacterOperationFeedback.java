package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.validation.CharacterErrorId;

/** Immutable client-side record of the latest server operation result. */
public final class CharacterOperationFeedback {

    private final int requestId;
    private final CharacterOperationType operationType;
    private final boolean successful;
    private final CharacterErrorId errorId;
    private final long retryAfterMillis;
    private final boolean rosterFollows;

    public CharacterOperationFeedback(int requestId,
                                      CharacterOperationType operationType,
                                      boolean successful,
                                      CharacterErrorId errorId,
                                      long retryAfterMillis,
                                      boolean rosterFollows) {
        this.requestId = requestId;
        this.operationType = operationType == null
                ? CharacterOperationType.UNKNOWN : operationType;
        this.successful = successful;
        this.errorId = errorId == null ? CharacterErrorId.INTERNAL_ERROR : errorId;
        this.retryAfterMillis = retryAfterMillis;
        this.rosterFollows = rosterFollows;
    }

    public int getRequestId() {
        return this.requestId;
    }

    public CharacterOperationType getOperationType() {
        return this.operationType;
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public CharacterErrorId getErrorId() {
        return this.errorId;
    }

    public long getRetryAfterMillis() {
        return this.retryAfterMillis;
    }

    public boolean isRosterFollows() {
        return this.rosterFollows;
    }
}
