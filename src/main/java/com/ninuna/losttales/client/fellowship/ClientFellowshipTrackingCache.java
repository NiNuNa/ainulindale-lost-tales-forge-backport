package com.ninuna.losttales.client.fellowship;

import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMark;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberSnapshot;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerHeightResolver;
import net.minecraft.util.StatCollector;
import com.ninuna.losttales.fellowship.sync.FellowshipGoHereMarkerSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackingSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Client-only, non-authoritative cache for private fellowship tracking data,
 * and the map's markers of the fellowships: the go-here markers of the
 * fellowship travelled with, while its tracking is fresh, and the marks of
 * every fellowship of the character played.
 */
public final class ClientFellowshipTrackingCache {

    public static final long STALE_AFTER_MILLIS = 30000L;

    private static FellowshipTrackingSnapshot snapshot;
    private static long receivedAtMillis;
    private static List<LostTalesMapMarkerData> goHereMarkers =
            Collections.emptyList();
    private static List<LostTalesMapMarkerData> markMarkers =
            Collections.emptyList();
    /** The two together, one list kept while nothing changes, as the map's index asks. */
    private static List<LostTalesMapMarkerData> mapMarkers =
            Collections.emptyList();
    /** Whether {@link #mapMarkers} was made while the tracking had gone stale. */
    private static boolean mapMarkersStale;

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

    /**
     * The fellowships' markers on the map: the go-here markers while the
     * tracking is fresh, every mark always. The same list while nothing
     * changes.
     */
    public static synchronized List<LostTalesMapMarkerData> getMapMarkers() {
        boolean stale = isCurrentSnapshotStale();
        if (stale != mapMarkersStale) {
            mapMarkersStale = stale;
            mapMarkers = combined(stale
                    ? Collections.<LostTalesMapMarkerData>emptyList()
                    : goHereMarkers, markMarkers);
        }
        return mapMarkers;
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
        goHereMarkers = matchesFellowshipState(snapshot, fellowshipState)
                ? buildMapMarkers(snapshot)
                : Collections.<LostTalesMapMarkerData>emptyList();
        markMarkers = buildMarkMarkers(fellowshipState);
        mapMarkersStale = isCurrentSnapshotStale();
        mapMarkers = combined(mapMarkersStale
                ? Collections.<LostTalesMapMarkerData>emptyList()
                : goHereMarkers, markMarkers);
    }

    public static synchronized void clear() {
        snapshot = null;
        receivedAtMillis = 0L;
        goHereMarkers = Collections.emptyList();
        markMarkers = Collections.emptyList();
        mapMarkers = Collections.emptyList();
        mapMarkersStale = false;
    }

    private static List<LostTalesMapMarkerData> combined(
            List<LostTalesMapMarkerData> first, List<LostTalesMapMarkerData> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        List<LostTalesMapMarkerData> both = new ArrayList<LostTalesMapMarkerData>(first);
        both.addAll(second);
        return Collections.unmodifiableList(both);
    }

    /** A mark's marker on the map by its id; null while no fellowship holds it. */
    public static synchronized LostTalesMapMarkerData markMarker(String markerId) {
        if (markerId == null) {
            return null;
        }
        for (LostTalesMapMarkerData marker : markMarkers) {
            if (markerId.equals(marker.getId())) {
                return marker;
            }
        }
        return null;
    }

    /**
     * The colour this player wears in a fellowship, which its marks wear
     * on the map and in its conversation's links; null for a fellowship
     * the character played is not in.
     */
    public static String markTint(UUID fellowshipId) {
        FellowshipStateSnapshot state = ClientFellowshipStateCache.getSnapshot();
        FellowshipSnapshot fellowship = state == null || !state.isAvailable()
                || fellowshipId == null ? null : state.getFellowship(fellowshipId);
        return fellowship == null ? null : markColor(state, fellowship).getTint();
    }

    /**
     * The colour a fellowship's marks wear for this player: the colour they
     * wear in it, as the Fellowships page does.
     */
    public static FellowshipColor markColor(FellowshipStateSnapshot state,
                                            FellowshipSnapshot fellowship) {
        FellowshipMemberSnapshot own = state == null || fellowship == null
                ? null : fellowship.getMember(state.getActiveIdentityId());
        return own == null ? FellowshipColor.BLUE : own.getColor();
    }

    /**
     * Every mark of every fellowship of the character played, as the map
     * shows it: its name, a camp, the colour this player wears in its
     * fellowship, and whose it is and who placed it under the pointer.
     */
    private static List<LostTalesMapMarkerData> buildMarkMarkers(
            FellowshipStateSnapshot state) {
        if (state == null || !state.isAvailable()) {
            return Collections.emptyList();
        }
        List<LostTalesMapMarkerData> markers = new ArrayList<LostTalesMapMarkerData>();
        for (FellowshipSnapshot fellowship : state.getFellowships()) {
            String tint = markColor(state, fellowship).getTint();
            for (FellowshipMark mark : fellowship.getMarks()) {
                FellowshipMemberSnapshot placer = fellowship.getMember(mark.getPlacedBy());
                markers.add(new LostTalesMapMarkerData(
                        mark.getMarkerId(),
                        mark.getName(),
                        LostTalesCompassMarkerIcon.CAMP.name(),
                        tint,
                        "Fellowship Mark",
                        placer == null
                                ? StatCollector.translateToLocalFormatted(
                                        "gui.losttales.map.fellowship_mark.of",
                                        fellowship.getName())
                                : StatCollector.translateToLocalFormatted(
                                        "gui.losttales.map.fellowship_mark.placed",
                                        fellowship.getName(),
                                        placer.getCharacterName()),
                        false,
                        mark.getDimensionId(),
                        mark.getX(), LostTalesMapMarkerHeightResolver.AUTOMATIC_Y,
                        mark.getZ(),
                        0.0D,
                        1.0D,
                        false,
                        false));
            }
        }
        return markers.isEmpty() ? Collections.<LostTalesMapMarkerData>emptyList()
                : Collections.unmodifiableList(markers);
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
                    StatCollector.translateToLocal(
                            "gui.losttales.map.go_here.description"),
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
