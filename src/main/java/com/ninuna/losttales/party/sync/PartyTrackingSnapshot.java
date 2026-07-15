package com.ninuna.losttales.party.sync;

import com.ninuna.losttales.party.model.Party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Private runtime positions and persistent markers for one authorized party. */
public final class PartyTrackingSnapshot {

    private final UUID ownerId;
    private final long synchronizationSequence;
    private final UUID activeCharacterId;
    private final UUID partyId;
    private final long partyRevision;
    private final List<PartyTrackedMemberSnapshot> trackedMembers;
    private final List<PartyGoHereMarkerSnapshot> goHereMarkers;

    public static PartyTrackingSnapshot noParty(UUID ownerId,
                                                long sequence,
                                                UUID activeCharacterId) {
        return noParty(ownerId, sequence, activeCharacterId,
                Collections.<PartyGoHereMarkerSnapshot>emptyList());
    }

    public static PartyTrackingSnapshot noParty(
            UUID ownerId, long sequence, UUID activeCharacterId,
            List<PartyGoHereMarkerSnapshot> goHereMarkers) {
        return new PartyTrackingSnapshot(ownerId, sequence,
                activeCharacterId, null, -1L,
                Collections.<PartyTrackedMemberSnapshot>emptyList(),
                goHereMarkers);
    }

    public PartyTrackingSnapshot(UUID ownerId,
                                 long synchronizationSequence,
                                 UUID activeCharacterId,
                                 UUID partyId,
                                 long partyRevision,
                                 List<PartyTrackedMemberSnapshot> trackedMembers,
                                 List<PartyGoHereMarkerSnapshot> goHereMarkers) {
        if (ownerId == null || activeCharacterId == null) {
            throw new IllegalArgumentException(
                    "owner and active character identities are required");
        }
        if (synchronizationSequence <= 0L) {
            throw new IllegalArgumentException(
                    "synchronization sequence must be positive");
        }
        boolean hasParty = partyId != null;
        if (hasParty != (partyRevision >= 0L)) {
            throw new IllegalArgumentException("invalid party context");
        }
        List<PartyTrackedMemberSnapshot> safeMembers = copyMembers(
                trackedMembers);
        List<PartyGoHereMarkerSnapshot> safeMarkers = copyMarkers(
                goHereMarkers);
        if (!hasParty && !safeMembers.isEmpty()) {
            throw new IllegalArgumentException(
                    "partyless snapshot cannot contain tracked members");
        }
        if (!hasParty && (safeMarkers.size() > 1
                || (!safeMarkers.isEmpty()
                && !activeCharacterId.equals(
                safeMarkers.get(0).getOwnerCharacterId())))) {
            throw new IllegalArgumentException(
                    "partyless snapshot may contain only its active character marker");
        }
        this.ownerId = ownerId;
        this.synchronizationSequence = synchronizationSequence;
        this.activeCharacterId = activeCharacterId;
        this.partyId = partyId;
        this.partyRevision = partyRevision;
        this.trackedMembers = safeMembers;
        this.goHereMarkers = safeMarkers;
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public long getSynchronizationSequence() {
        return this.synchronizationSequence;
    }

    public UUID getActiveCharacterId() {
        return this.activeCharacterId;
    }

    public boolean hasParty() {
        return this.partyId != null;
    }

    public UUID getPartyId() {
        return this.partyId;
    }

    public long getPartyRevision() {
        return this.partyRevision;
    }

    public List<PartyTrackedMemberSnapshot> getTrackedMembers() {
        return this.trackedMembers;
    }

    public List<PartyGoHereMarkerSnapshot> getGoHereMarkers() {
        return this.goHereMarkers;
    }

    public boolean hasSameContent(PartyTrackingSnapshot other) {
        return other != null
                && this.ownerId.equals(other.ownerId)
                && this.activeCharacterId.equals(other.activeCharacterId)
                && equalNullable(this.partyId, other.partyId)
                && this.partyRevision == other.partyRevision
                && this.trackedMembers.equals(other.trackedMembers)
                && this.goHereMarkers.equals(other.goHereMarkers);
    }

    private static List<PartyTrackedMemberSnapshot> copyMembers(
            List<PartyTrackedMemberSnapshot> source) {
        ArrayList<PartyTrackedMemberSnapshot> result =
                new ArrayList<PartyTrackedMemberSnapshot>();
        Set<UUID> identities = new HashSet<UUID>();
        if (source != null) {
            for (PartyTrackedMemberSnapshot member : source) {
                if (member == null || !identities.add(member.getCharacterId())) {
                    throw new IllegalArgumentException(
                            "tracked member identities must be unique");
                }
                result.add(member);
            }
        }
        if (result.size() > Party.MAX_MEMBERS) {
            throw new IllegalArgumentException("too many tracked members");
        }
        return Collections.unmodifiableList(result);
    }

    private static List<PartyGoHereMarkerSnapshot> copyMarkers(
            List<PartyGoHereMarkerSnapshot> source) {
        ArrayList<PartyGoHereMarkerSnapshot> result =
                new ArrayList<PartyGoHereMarkerSnapshot>();
        Set<UUID> identities = new HashSet<UUID>();
        if (source != null) {
            for (PartyGoHereMarkerSnapshot marker : source) {
                if (marker == null
                        || !identities.add(marker.getOwnerCharacterId())) {
                    throw new IllegalArgumentException(
                            "marker owner identities must be unique");
                }
                result.add(marker);
            }
        }
        if (result.size() > Party.MAX_MEMBERS) {
            throw new IllegalArgumentException("too many party markers");
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean equalNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
