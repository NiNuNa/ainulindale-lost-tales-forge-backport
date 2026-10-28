package com.ninuna.losttales.client.fellowship;

import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackingSnapshot;

/**
 * The positions of the members of the fellowship travelled with, as the
 * server last sent them. The marks ride in the fellowship state
 * ({@link ClientFellowshipMarks}).
 */
public final class ClientFellowshipTrackingCache {

    public static final long STALE_AFTER_MILLIS = 30000L;

    private static FellowshipTrackingSnapshot snapshot;
    private static long receivedAtMillis;

    private ClientFellowshipTrackingCache() {}

    public static synchronized void accept(FellowshipTrackingSnapshot incoming) {
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

    /**
     * The positions while they are fresh and were sent for the fellowship
     * state as it stands; null else. A future or out-of-order packet is
     * kept, but exposes no position until the state matches it exactly.
     */
    public static synchronized FellowshipTrackingSnapshot getMatching(
            FellowshipStateSnapshot fellowshipState) {
        return matchesFellowshipState(snapshot, fellowshipState)
                && !isCurrentSnapshotStale() ? snapshot : null;
    }

    public static synchronized void clear() {
        snapshot = null;
        receivedAtMillis = 0L;
    }

    private static boolean isCurrentSnapshotStale() {
        return snapshot != null && receivedAtMillis > 0L
                && System.currentTimeMillis() - receivedAtMillis
                > STALE_AFTER_MILLIS;
    }

    public static LostTalesCompassMarkerIcon fellowshipIcon(FellowshipColor color) {
        if (color == FellowshipColor.GREEN) {
            return LostTalesCompassMarkerIcon.FELLOWSHIP_GREEN;
        }
        if (color == FellowshipColor.YELLOW) {
            return LostTalesCompassMarkerIcon.FELLOWSHIP_YELLOW;
        }
        if (color == FellowshipColor.PURPLE) {
            return LostTalesCompassMarkerIcon.FELLOWSHIP_PURPLE;
        }
        return LostTalesCompassMarkerIcon.FELLOWSHIP_BLUE;
    }

    private static boolean matchesFellowshipState(
            FellowshipTrackingSnapshot tracking,
            FellowshipStateSnapshot fellowshipState) {
        if (tracking == null || fellowshipState == null) {
            return false;
        }
        if (!fellowshipState.isAvailable()) {
            return false;
        }
        if (fellowshipState.getActiveIdentityId() == null
                || !tracking.getActiveIdentityId().equals(
                fellowshipState.getActiveIdentityId())) {
            return false;
        }
        FellowshipSnapshot travelling = fellowshipState.getTravellingFellowship();
        if (travelling == null) {
            return !tracking.hasFellowship();
        }
        return tracking.hasFellowship()
                && travelling.getFellowshipId().equals(tracking.getFellowshipId())
                && travelling.getRevision() == tracking.getFellowshipRevision();
    }
}
