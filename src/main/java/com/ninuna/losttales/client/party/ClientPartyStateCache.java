package com.ninuna.losttales.client.party;

import com.ninuna.losttales.party.server.PartyErrorId;
import com.ninuna.losttales.party.sync.PartyOperationFeedback;
import com.ninuna.losttales.party.sync.PartyOperationType;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-only synchronized party view. It is never authoritative. */
public final class ClientPartyStateCache {

    public enum SyncState {
        UNKNOWN,
        LOADING,
        READY,
        ERROR
    }

    private static final int MAX_PENDING_REQUESTS = 32;
    private static final Map<Integer, PartyOperationType> PENDING_REQUESTS =
            new LinkedHashMap<Integer, PartyOperationType>();
    private static final Map<Integer, PartyOperationFeedback> COMPLETED_OPERATIONS =
            new LinkedHashMap<Integer, PartyOperationFeedback>();

    private static SyncState state = SyncState.UNKNOWN;
    private static PartyStateSnapshot snapshot;
    private static PartyOperationFeedback lastOperation;

    private ClientPartyStateCache() {}

    public static synchronized void beginRequest(int requestId,
                                                 PartyOperationType operationType) {
        if (PENDING_REQUESTS.size() >= MAX_PENDING_REQUESTS) {
            Iterator<Integer> iterator = PENDING_REQUESTS.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        PENDING_REQUESTS.put(Integer.valueOf(requestId), operationType);
        if (operationType == PartyOperationType.REQUEST_STATE
                && snapshot == null) {
            state = SyncState.LOADING;
        }
    }

    /**
     * Accepts only newer server sequences. A stale response may still complete
     * its matching request because a later unsolicited snapshot already won.
     */
    public static synchronized void acceptState(int requestId,
                                                PartyStateSnapshot incoming) {
        if (incoming == null) {
            markProtocolError(requestId);
            return;
        }
        if (snapshot == null
                || incoming.getSynchronizationSequence()
                > snapshot.getSynchronizationSequence()) {
            snapshot = incoming;
            state = incoming.isAvailable()
                    ? SyncState.READY : SyncState.ERROR;
        }
        if (requestId != 0) {
            PENDING_REQUESTS.remove(Integer.valueOf(requestId));
        }
    }

    public static synchronized void acceptOperation(
            PartyOperationFeedback feedback) {
        if (feedback == null) {
            markProtocolError(0);
            return;
        }
        lastOperation = feedback;
        rememberCompleted(feedback);
        if (!feedback.isStateFollows()) {
            PENDING_REQUESTS.remove(Integer.valueOf(feedback.getRequestId()));
        }
        if (!feedback.isSuccessful() && snapshot == null
                && !feedback.isStateFollows()) {
            state = SyncState.ERROR;
        }
    }

    public static synchronized void failLocalRequest(
            int requestId, PartyOperationType operationType) {
        lastOperation = new PartyOperationFeedback(
                requestId,
                operationType,
                false,
                false,
                false,
                PartyErrorId.INTERNAL_ERROR,
                snapshot == null ? -1L : snapshot.getPartyRevision(),
                false);
        rememberCompleted(lastOperation);
        PENDING_REQUESTS.remove(Integer.valueOf(requestId));
        if (snapshot == null) {
            state = SyncState.ERROR;
        }
    }

    public static synchronized void markProtocolError(int requestId) {
        lastOperation = new PartyOperationFeedback(
                requestId,
                PartyOperationType.UNKNOWN,
                false,
                false,
                false,
                PartyErrorId.INTERNAL_ERROR,
                snapshot == null ? -1L : snapshot.getPartyRevision(),
                false);
        if (requestId != 0) {
            rememberCompleted(lastOperation);
            PENDING_REQUESTS.remove(Integer.valueOf(requestId));
        }
        if (snapshot == null) {
            state = SyncState.ERROR;
        }
    }

    public static synchronized SyncState getState() {
        return state;
    }

    public static synchronized PartyStateSnapshot getSnapshot() {
        return snapshot;
    }

    public static synchronized PartyOperationFeedback getLastOperation() {
        return lastOperation;
    }

    public static synchronized PartyOperationFeedback getOperation(int requestId) {
        return COMPLETED_OPERATIONS.get(Integer.valueOf(requestId));
    }

    public static synchronized void clearOperation(int requestId) {
        COMPLETED_OPERATIONS.remove(Integer.valueOf(requestId));
        if (lastOperation != null && lastOperation.getRequestId() == requestId) {
            lastOperation = null;
        }
    }

    public static synchronized boolean isRequestPending(int requestId) {
        return PENDING_REQUESTS.containsKey(Integer.valueOf(requestId));
    }

    public static synchronized boolean hasPendingRequest(
            PartyOperationType operationType) {
        return PENDING_REQUESTS.containsValue(operationType);
    }

    public static synchronized void clearLastOperation() {
        lastOperation = null;
    }

    public static synchronized void clear() {
        PENDING_REQUESTS.clear();
        COMPLETED_OPERATIONS.clear();
        snapshot = null;
        lastOperation = null;
        state = SyncState.UNKNOWN;
    }

    private static void rememberCompleted(PartyOperationFeedback feedback) {
        if (feedback == null || feedback.getRequestId() == 0) {
            return;
        }
        if (COMPLETED_OPERATIONS.size() >= MAX_PENDING_REQUESTS
                && !COMPLETED_OPERATIONS.containsKey(
                Integer.valueOf(feedback.getRequestId()))) {
            Iterator<Integer> iterator = COMPLETED_OPERATIONS.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        COMPLETED_OPERATIONS.put(
                Integer.valueOf(feedback.getRequestId()), feedback);
    }
}
