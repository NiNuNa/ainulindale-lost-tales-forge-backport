package com.ninuna.losttales.party.sync;

import com.ninuna.losttales.party.server.PartyErrorId;

/** Client-side immutable feedback for one party mutation request. */
public final class PartyOperationFeedback {

    private final int requestId;
    private final PartyOperationType operationType;
    private final boolean successful;
    private final boolean changed;
    private final boolean partyDisbanded;
    private final PartyErrorId errorId;
    private final long partyRevision;
    private final boolean stateFollows;

    public PartyOperationFeedback(int requestId,
                                  PartyOperationType operationType,
                                  boolean successful,
                                  boolean changed,
                                  boolean partyDisbanded,
                                  PartyErrorId errorId,
                                  long partyRevision,
                                  boolean stateFollows) {
        this.requestId = requestId;
        this.operationType = operationType == null
                ? PartyOperationType.UNKNOWN : operationType;
        this.successful = successful;
        this.changed = changed;
        this.partyDisbanded = partyDisbanded;
        this.errorId = errorId == null
                ? PartyErrorId.INTERNAL_ERROR : errorId;
        this.partyRevision = partyRevision;
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

    public boolean wasChanged() {
        return this.changed;
    }

    public boolean wasPartyDisbanded() {
        return this.partyDisbanded;
    }

    public PartyErrorId getErrorId() {
        return this.errorId;
    }

    public long getPartyRevision() {
        return this.partyRevision;
    }

    public boolean isStateFollows() {
        return this.stateFollows;
    }
}
