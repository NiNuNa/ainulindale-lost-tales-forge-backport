package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.validation.CharacterErrorId;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-only synchronized view model. It is never authoritative.
 *
 * <p>Each request is answered on its own, as the party's are: the answer
 * waits under its request id until whoever sent it reads it, so a second
 * quick request never takes the first one's answer. A roster older than
 * the one held, arriving late, still completes its request but is not
 * shown.</p>
 */
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
    private static final Map<Integer, CharacterOperationFeedback> COMPLETED_OPERATIONS =
            new LinkedHashMap<Integer, CharacterOperationFeedback>();

    private static SyncState state = SyncState.UNKNOWN;
    private static CharacterRosterSnapshot snapshot;

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

    /**
     * Takes a roster from the server. One older than the one held — a
     * lower revision of the same owner's — completes its request and is
     * dropped.
     */
    public static synchronized void acceptRoster(int requestId,
                                                 CharacterRosterSnapshot incoming) {
        if (incoming == null) {
            markProtocolError(requestId);
            return;
        }
        if (isCurrent(snapshot, incoming)) {
            snapshot = incoming;
            state = SyncState.READY;
        }
        if (requestId != 0) {
            PENDING_REQUESTS.remove(Integer.valueOf(requestId));
        }
    }

    /** Whether {@code incoming} is at least as new as {@code held}. */
    static boolean isCurrent(CharacterRosterSnapshot held,
                             CharacterRosterSnapshot incoming) {
        return held == null
                || !held.getOwnerId().equals(incoming.getOwnerId())
                || incoming.getRevision() >= held.getRevision();
    }

    public static synchronized void acceptOperation(CharacterOperationFeedback feedback) {
        if (feedback == null) {
            markProtocolError(0);
            return;
        }
        rememberCompleted(feedback);
        if (!feedback.isRosterFollows()) {
            PENDING_REQUESTS.remove(Integer.valueOf(feedback.getRequestId()));
        }
        if (!feedback.isSuccessful() && snapshot == null && !feedback.isRosterFollows()) {
            state = SyncState.ERROR;
        }
    }

    public static synchronized void failLocalRequest(int requestId,
                                                     CharacterOperationType operationType) {
        rememberCompleted(new CharacterOperationFeedback(
                requestId,
                operationType,
                false,
                CharacterErrorId.INTERNAL_ERROR,
                -1L,
                false
        ));
        PENDING_REQUESTS.remove(Integer.valueOf(requestId));
        if (snapshot == null) {
            state = SyncState.ERROR;
        }
    }

    public static synchronized void markProtocolError(int requestId) {
        if (requestId != 0) {
            rememberCompleted(new CharacterOperationFeedback(
                    requestId,
                    CharacterOperationType.UNKNOWN,
                    false,
                    CharacterErrorId.INTERNAL_ERROR,
                    -1L,
                    false
            ));
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

    /** The server's answer to that request; null while none has come. */
    public static synchronized CharacterOperationFeedback getOperation(int requestId) {
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

    private static void rememberCompleted(CharacterOperationFeedback feedback) {
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
