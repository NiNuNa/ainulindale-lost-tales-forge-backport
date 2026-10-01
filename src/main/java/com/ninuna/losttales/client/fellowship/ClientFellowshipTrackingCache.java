package com.ninuna.losttales.client.fellowship;

import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.sync.FellowshipGoHereMarkerSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackingSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Client-only, non-authoritative cache for private fellowship tracking data. */
public final class ClientFellowshipTrackingCache {

    public static final long STALE_AFTER_MILLIS = 30000L;

    private static FellowshipTrackingSnapshot snapshot;
    private static long receivedAtMillis;
    private static List<LostTalesMapMarkerData> renderedMarkers =
            Collections.emptyList();

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

    public static synchronized FellowshipTrackingSnapshot getMatching(
            FellowshipStateSnapshot fellowshipState) {
        return matchesFellowshipState(snapshot, fellowshipState)
                && !isCurrentSnapshotStale() ? snapshot : null;
    }

    public static synchronized List<LostTalesMapMarkerData> getMapMarkers() {
        return isCurrentSnapshotStale()
                ? Collections.<LostTalesMapMarkerData>emptyList()
                : renderedMarkers;
    }

    public static synchronized boolean hasLocalGoHereMarker(
            FellowshipStateSnapshot fellowshipState) {
        FellowshipTrackingSnapshot matching = getMatching(fellowshipState);
        if (matching == null) {
            return false;
        }
        UUID localIdentityId = matching.getActiveIdentityId();
        for (FellowshipGoHereMarkerSnapshot marker : matching.getGoHereMarkers()) {
            if (localIdentityId.equals(marker.getOwnerIdentityId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps a future/out-of-order packet in memory, but exposes no coordinates
     * until the independently synchronized fellowship context matches exactly.
     */
    public static synchronized void validateFellowshipState(
            FellowshipStateSnapshot fellowshipState) {
        renderedMarkers = matchesFellowshipState(snapshot, fellowshipState)
                ? buildMapMarkers(snapshot)
                : Collections.<LostTalesMapMarkerData>emptyList();
    }

    public static synchronized void clear() {
        snapshot = null;
        receivedAtMillis = 0L;
        renderedMarkers = Collections.emptyList();
    }

    private static boolean isCurrentSnapshotStale() {
        return snapshot != null && receivedAtMillis > 0L
                && System.currentTimeMillis() - receivedAtMillis
                > STALE_AFTER_MILLIS;
    }

    private static List<LostTalesMapMarkerData> buildMapMarkers(
            FellowshipTrackingSnapshot tracking) {
        if (tracking == null) {
            return Collections.emptyList();
        }
        ArrayList<LostTalesMapMarkerData> markers =
                new ArrayList<LostTalesMapMarkerData>();
        for (FellowshipGoHereMarkerSnapshot marker
                : tracking.getGoHereMarkers()) {
            markers.add(new LostTalesMapMarkerData(
                    "fellowship_go_here:" + marker.getOwnerIdentityId(),
                    marker.getOwnerCharacterName(),
                    LostTalesCompassMarkerIcon.QUEST.name(),
                    marker.getOwnerColor().getTint(),
                    "Go Here",
                    "A personal marker for this roleplaying character. It is shared while in a fellowship.",
                    false,
                    marker.getDimensionId(),
                    marker.getX(), marker.getY(), marker.getZ(),
                    0.0D,
                    1.0D,
                    false,
                    false));
        }
        return Collections.unmodifiableList(markers);
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
