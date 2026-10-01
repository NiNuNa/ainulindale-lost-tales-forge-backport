package com.ninuna.losttales.client.fellowship;

import com.ninuna.losttales.fellowship.server.FellowshipErrorId;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationFeedback;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-only synchronized fellowship view. It is never authoritative. */
public final class ClientFellowshipStateCache {

    public enum SyncState {
        UNKNOWN,
        LOADING,
        READY,
        ERROR
    }

    private static final int MAX_PENDING_REQUESTS = 32;
    private static final Map<Integer, FellowshipOperationType> PENDING_REQUESTS =
            new LinkedHashMap<Integer, FellowshipOperationType>();
    private static final Map<Integer, FellowshipOperationFeedback> COMPLETED_OPERATIONS =
            new LinkedHashMap<Integer, FellowshipOperationFeedback>();

    private static SyncState state = SyncState.UNKNOWN;
    private static FellowshipStateSnapshot snapshot;

    private ClientFellowshipStateCache() {}

    public static synchronized void beginRequest(int requestId,
                                                 FellowshipOperationType operationType) {
        if (PENDING_REQUESTS.size() >= MAX_PENDING_REQUESTS) {
            Iterator<Integer> iterator = PENDING_REQUESTS.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        PENDING_REQUESTS.put(Integer.valueOf(requestId), operationType);
        if (operationType == FellowshipOperationType.REQUEST_STATE
                && snapshot == null) {
            state = SyncState.LOADING;
        }
    }

    /**
     * Accepts only newer server sequences. A stale response may still complete
     * its matching request because a later unsolicited snapshot already won.
     */
    public static synchronized void acceptState(int requestId,
                                                FellowshipStateSnapshot incoming) {
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
            FellowshipOperationFeedback feedback) {
        if (feedback == null) {
            markProtocolError(0);
            return;
        }
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
            int requestId, FellowshipOperationType operationType) {
        rememberCompleted(new FellowshipOperationFeedback(
                requestId,
                operationType,
                false,
                FellowshipErrorId.INTERNAL_ERROR,
                false));
        PENDING_REQUESTS.remove(Integer.valueOf(requestId));
        if (snapshot == null) {
            state = SyncState.ERROR;
        }
    }

    public static synchronized void markProtocolError(int requestId) {
        if (requestId != 0) {
            rememberCompleted(new FellowshipOperationFeedback(
                    requestId,
                    FellowshipOperationType.UNKNOWN,
                    false,
                    FellowshipErrorId.INTERNAL_ERROR,
                    false));
            PENDING_REQUESTS.remove(Integer.valueOf(requestId));
        }
        if (snapshot == null) {
            state = SyncState.ERROR;
        }
    }

    public static synchronized SyncState getState() {
        return state;
    }

    public static synchronized FellowshipStateSnapshot getSnapshot() {
        return snapshot;
    }

    public static synchronized FellowshipOperationFeedback getOperation(int requestId) {
        return COMPLETED_OPERATIONS.get(Integer.valueOf(requestId));
    }

    public static synchronized void clearOperation(int requestId) {
        COMPLETED_OPERATIONS.remove(Integer.valueOf(requestId));
    }

    public static synchronized boolean isRequestPending(int requestId) {
        return PENDING_REQUESTS.containsKey(Integer.valueOf(requestId));
    }

    public static synchronized void clear() {
        PENDING_REQUESTS.clear();
        COMPLETED_OPERATIONS.clear();
        snapshot = null;
        state = SyncState.UNKNOWN;
    }

    private static void rememberCompleted(FellowshipOperationFeedback feedback) {
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
