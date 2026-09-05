package com.ninuna.losttales.config.client;

import com.ninuna.losttales.config.server.ServerConfigApplyResult;
import com.ninuna.losttales.config.server.ServerConfigEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the server last told this client about its config: the snapshot
 * an operator's screen edits and the result of the last apply. Each
 * arrival bumps a sequence so a screen waiting for an answer knows it is
 * the answer to its own request. Cleared on disconnect.
 */
public final class ClientServerConfigCache {

    private static List<ServerConfigEntry> snapshot = Collections.emptyList();
    private static int snapshotSequence;
    private static ServerConfigApplyResult result;
    private static int resultSequence;

    private ClientServerConfigCache() {}

    public static synchronized void acceptSnapshot(List<ServerConfigEntry> entries) {
        snapshot = Collections.unmodifiableList(new ArrayList<ServerConfigEntry>(
                entries == null ? Collections.<ServerConfigEntry>emptyList() : entries));
        snapshotSequence++;
    }

    public static synchronized void acceptResult(ServerConfigApplyResult applied) {
        result = applied;
        resultSequence++;
    }

    public static synchronized List<ServerConfigEntry> getSnapshot() {
        return snapshot;
    }

    /** Bumps whenever a snapshot arrives; a screen compares against what it saw. */
    public static synchronized int getSnapshotSequence() {
        return snapshotSequence;
    }

    public static synchronized ServerConfigApplyResult getResult() {
        return result;
    }

    public static synchronized int getResultSequence() {
        return resultSequence;
    }

    public static synchronized void clear() {
        snapshot = Collections.emptyList();
        snapshotSequence = 0;
        result = null;
        resultSequence = 0;
    }
}
