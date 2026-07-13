package com.ninuna.losttales.client.party;

import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.sync.PartyGoHereMarkerSnapshot;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import com.ninuna.losttales.party.sync.PartyTrackingSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Client-only, non-authoritative cache for private party tracking data. */
public final class ClientPartyTrackingCache {

    public static final long STALE_AFTER_MILLIS = 30000L;

    private static PartyTrackingSnapshot snapshot;
    private static long receivedAtMillis;
    private static List<LostTalesMapMarkerData> renderedMarkers =
            Collections.emptyList();

    private ClientPartyTrackingCache() {}

    public static synchronized void accept(PartyTrackingSnapshot incoming) {
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

    public static synchronized PartyTrackingSnapshot getMatching(
            PartyStateSnapshot partyState) {
        return matchesPartyState(snapshot, partyState)
                && !isCurrentSnapshotStale() ? snapshot : null;
    }

    public static synchronized List<LostTalesMapMarkerData> getMapMarkers() {
        return isCurrentSnapshotStale()
                ? Collections.<LostTalesMapMarkerData>emptyList()
                : renderedMarkers;
    }

    public static synchronized boolean hasLocalGoHereMarker(
            PartyStateSnapshot partyState) {
        PartyTrackingSnapshot matching = getMatching(partyState);
        if (matching == null) {
            return false;
        }
        UUID localCharacterId = matching.getActiveCharacterId();
        for (PartyGoHereMarkerSnapshot marker : matching.getGoHereMarkers()) {
            if (localCharacterId.equals(marker.getOwnerCharacterId())) {
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean isStale(
            PartyStateSnapshot partyState) {
        return matchesPartyState(snapshot, partyState)
                && isCurrentSnapshotStale();
    }

    /**
     * Keeps a future/out-of-order packet in memory, but exposes no coordinates
     * until the independently synchronized party context matches exactly.
     */
    public static synchronized void validatePartyState(
            PartyStateSnapshot partyState) {
        renderedMarkers = matchesPartyState(snapshot, partyState)
                ? buildMapMarkers(snapshot)
                : Collections.<LostTalesMapMarkerData>emptyList();
    }

    public static synchronized PartyTrackingSnapshot getSnapshot() {
        return snapshot;
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
            PartyTrackingSnapshot tracking) {
        if (tracking == null || !tracking.hasParty()) {
            return Collections.emptyList();
        }
        ArrayList<LostTalesMapMarkerData> markers =
                new ArrayList<LostTalesMapMarkerData>();
        for (PartyGoHereMarkerSnapshot marker
                : tracking.getGoHereMarkers()) {
            markers.add(new LostTalesMapMarkerData(
                    "party_go_here:" + marker.getOwnerCharacterId(),
                    marker.getOwnerCharacterName(),
                    LostTalesCompassMarkerIcon.QUEST.name(),
                    marker.getOwnerColor().getId(),
                    "Party Marker",
                    "A personal map marker shared with this party.",
                    false,
                    "",
                    marker.getDimensionId(),
                    marker.getX(), marker.getY(), marker.getZ(),
                    2048.0D,
                    1.0D,
                    false,
                    false));
        }
        return Collections.unmodifiableList(markers);
    }

    public static LostTalesCompassMarkerIcon partyIcon(PartyColor color) {
        if (color == PartyColor.GREEN) {
            return LostTalesCompassMarkerIcon.PARTY_GREEN;
        }
        if (color == PartyColor.YELLOW) {
            return LostTalesCompassMarkerIcon.PARTY_YELLOW;
        }
        if (color == PartyColor.PURPLE) {
            return LostTalesCompassMarkerIcon.PARTY_PURPLE;
        }
        return LostTalesCompassMarkerIcon.PARTY_BLUE;
    }

    private static boolean matchesPartyState(
            PartyTrackingSnapshot tracking,
            PartyStateSnapshot partyState) {
        if (tracking == null || partyState == null
                || !partyState.isAvailable()
                || partyState.getActiveCharacterId() == null
                || !tracking.getActiveCharacterId().equals(
                partyState.getActiveCharacterId())) {
            return false;
        }
        if (partyState.getParty() == null) {
            return !tracking.hasParty();
        }
        return tracking.hasParty()
                && partyState.getParty().getPartyId().equals(
                tracking.getPartyId())
                && partyState.getParty().getRevision()
                == tracking.getPartyRevision();
    }
}
