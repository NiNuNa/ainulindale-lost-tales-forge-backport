package com.ninuna.losttales.fellowship.sync;

import com.ninuna.losttales.fellowship.server.FellowshipErrorId;

/** Client-side immutable feedback for one fellowship mutation request. */
public final class FellowshipOperationFeedback {

    private final int requestId;
    private final FellowshipOperationType operationType;
    private final boolean successful;
    private final FellowshipErrorId errorId;
    private final boolean stateFollows;

    public FellowshipOperationFeedback(int requestId,
                                  FellowshipOperationType operationType,
                                  boolean successful,
                                  FellowshipErrorId errorId,
                                  boolean stateFollows) {
        this.requestId = requestId;
        this.operationType = operationType == null
                ? FellowshipOperationType.UNKNOWN : operationType;
        this.successful = successful;
        this.errorId = errorId == null
                ? FellowshipErrorId.INTERNAL_ERROR : errorId;
        this.stateFollows = stateFollows;
    }

    public int getRequestId() {
        return this.requestId;
    }

    public FellowshipOperationType getOperationType() {
        return this.operationType;
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public FellowshipErrorId getErrorId() {
        return this.errorId;
    }

    public boolean isStateFollows() {
        return this.stateFollows;
    }
}
