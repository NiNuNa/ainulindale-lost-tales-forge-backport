package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.validation.CharacterErrorId;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-only synchronized view model. It is never authoritative. */
public final class ClientCharacterRosterCache {

    public enum SyncState {
        UNKNOWN,
        LOADING,
        READY,
        ERROR
    }

    private static final int MAX_PENDING_REQUESTS = 32;
    private static final Map<Integer, CharacterOperationType> PENDING_REQUESTS =
            new LinkedHashMap<Integer, CharacterOperationType>();

    private static SyncState state = SyncState.UNKNOWN;
    private static CharacterRosterSnapshot snapshot;
    private static CharacterOperationFeedback lastOperation;

    private ClientCharacterRosterCache() {}

    public static synchronized void beginRequest(int requestId,
                                                 CharacterOperationType operationType) {
        if (PENDING_REQUESTS.size() >= MAX_PENDING_REQUESTS) {
            Iterator<Integer> iterator = PENDING_REQUESTS.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        PENDING_REQUESTS.put(Integer.valueOf(requestId), operationType);
        if (operationType == CharacterOperationType.REQUEST_ROSTER && snapshot == null) {
            state = SyncState.LOADING;
        }
    }

    public static synchronized void acceptRoster(int requestId,
                                                 CharacterRosterSnapshot incoming) {
        if (incoming == null) {
            markProtocolError(requestId);
            return;
        }
        snapshot = incoming;
        state = SyncState.READY;
        if (requestId != 0) {
            PENDING_REQUESTS.remove(Integer.valueOf(requestId));
        }
    }

    public static synchronized void acceptOperation(CharacterOperationFeedback feedback) {
        if (feedback == null) {
            markProtocolError(0);
            return;
        }
        lastOperation = feedback;
        if (!feedback.isRosterFollows()) {
            PENDING_REQUESTS.remove(Integer.valueOf(feedback.getRequestId()));
        }
        if (!feedback.isSuccessful() && snapshot == null && !feedback.isRosterFollows()) {
            state = SyncState.ERROR;
        }
    }

    public static synchronized void failLocalRequest(int requestId,
                                                     CharacterOperationType operationType) {
        lastOperation = new CharacterOperationFeedback(
                requestId,
                operationType,
                false,
                false,
                CharacterErrorId.INTERNAL_ERROR,
                snapshot == null ? -1L : snapshot.getRevision(),
                -1L,
                false
        );
        PENDING_REQUESTS.remove(Integer.valueOf(requestId));
        if (snapshot == null) {
            state = SyncState.ERROR;
        }
    }

    public static synchronized void markProtocolError(int requestId) {
        lastOperation = new CharacterOperationFeedback(
                requestId,
                CharacterOperationType.UNKNOWN,
                false,
                false,
                CharacterErrorId.INTERNAL_ERROR,
                snapshot == null ? -1L : snapshot.getRevision(),
                -1L,
                false
        );
        if (requestId != 0) {
            PENDING_REQUESTS.remove(Integer.valueOf(requestId));
        }
        if (snapshot == null) {
            state = SyncState.ERROR;
        }
    }

    public static synchronized SyncState getState() {
        return state;
    }

    public static synchronized CharacterRosterSnapshot getSnapshot() {
        return snapshot;
    }

    public static synchronized CharacterOperationFeedback getLastOperation() {
        return lastOperation;
    }

    public static synchronized CharacterOperationFeedback getOperation(int requestId) {
        return lastOperation != null && lastOperation.getRequestId() == requestId
                ? lastOperation : null;
    }

    public static synchronized void clearOperation(int requestId) {
        if (lastOperation != null && lastOperation.getRequestId() == requestId) {
            lastOperation = null;
        }
    }

    public static synchronized boolean isRequestPending(int requestId) {
        return PENDING_REQUESTS.containsKey(Integer.valueOf(requestId));
    }

    public static synchronized boolean hasPendingRequest(CharacterOperationType operationType) {
        return PENDING_REQUESTS.containsValue(operationType);
    }

    public static synchronized void clearLastOperation() {
        lastOperation = null;
    }

    public static synchronized void clear() {
        PENDING_REQUESTS.clear();
        snapshot = null;
        lastOperation = null;
        state = SyncState.UNKNOWN;
    }
}
