package com.ninuna.losttales.client.fellowship;

import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStatusSnapshot;

/**
 * The health and availability of the travelling fellowship's members, as
 * last sent. It is never authoritative.
 */
public final class ClientFellowshipMemberStatusCache {

    public static final long STALE_AFTER_MILLIS = 30000L;

    private static FellowshipStatusSnapshot snapshot;
    private static long receivedAtMillis;

    private ClientFellowshipMemberStatusCache() {}

    public static synchronized void accept(FellowshipStatusSnapshot incoming) {
        if (incoming == null) {
            clear();
            return;
        }
        if (snapshot == null
                || incoming.getSynchronizationSequence()
                > snapshot.getSynchronizationSequence()) {
            snapshot = incoming;
            receivedAtMillis = System.currentTimeMillis();
        }
    }

    public static synchronized FellowshipStatusSnapshot getMatching(
            FellowshipStateSnapshot fellowshipState) {
        if (!matchesFellowshipState(snapshot, fellowshipState)) {
            return null;
        }
        return snapshot;
    }

    public static synchronized boolean isStale(
            FellowshipStateSnapshot fellowshipState) {
        return matchesFellowshipState(snapshot, fellowshipState)
                && System.currentTimeMillis() - receivedAtMillis
                > STALE_AFTER_MILLIS;
    }

    public static synchronized void clear() {
        snapshot = null;
        receivedAtMillis = 0L;
    }

    private static boolean matchesFellowshipState(
            FellowshipStatusSnapshot status,
            FellowshipStateSnapshot fellowshipState) {
        if (status == null || fellowshipState == null
                || !fellowshipState.isAvailable()
                || fellowshipState.getActiveIdentityId() == null
                || !status.getActiveIdentityId().equals(
                fellowshipState.getActiveIdentityId())) {
            return false;
        }
        FellowshipSnapshot travelling = fellowshipState.getTravellingFellowship();
        if (travelling == null) {
            return !status.hasFellowship();
        }
        return status.hasFellowship()
                && travelling.getFellowshipId().equals(status.getFellowshipId())
                && travelling.getRevision() == status.getFellowshipRevision();
    }
}
