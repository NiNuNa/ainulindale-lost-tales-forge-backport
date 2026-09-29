package com.ninuna.losttales.party.sync;

import com.ninuna.losttales.party.server.PartyErrorId;

/** Client-side immutable feedback for one party mutation request. */
public final class PartyOperationFeedback {

    private final int requestId;
    private final PartyOperationType operationType;
    private final boolean successful;
    private final PartyErrorId errorId;
    private final boolean stateFollows;

    public PartyOperationFeedback(int requestId,
                                  PartyOperationType operationType,
                                  boolean successful,
                                  PartyErrorId errorId,
                                  boolean stateFollows) {
        this.requestId = requestId;
        this.operationType = operationType == null
                ? PartyOperationType.UNKNOWN : operationType;
        this.successful = successful;
        this.errorId = errorId == null
                ? PartyErrorId.INTERNAL_ERROR : errorId;
        this.stateFollows = stateFollows;
    }

    public int getRequestId() {
        return this.requestId;
    }

    public PartyOperationType getOperationType() {
        return this.operationType;
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public PartyErrorId getErrorId() {
        return this.errorId;
    }

    public boolean isStateFollows() {
        return this.stateFollows;
    }
}
