package com.ninuna.losttales.fellowship.sync;

import com.ninuna.losttales.fellowship.model.Fellowship;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Private runtime positions and persistent markers for one authorized fellowship. */
public final class FellowshipTrackingSnapshot {

    private final UUID ownerId;
    private final long synchronizationSequence;
    private final UUID activeIdentityId;
    private final UUID fellowshipId;
    private final long fellowshipRevision;
    private final List<FellowshipTrackedMemberSnapshot> trackedMembers;
    private final List<FellowshipGoHereMarkerSnapshot> goHereMarkers;

    public static FellowshipTrackingSnapshot noFellowship(
            UUID ownerId, long sequence, UUID activeIdentityId,
            List<FellowshipGoHereMarkerSnapshot> goHereMarkers) {
        return new FellowshipTrackingSnapshot(ownerId, sequence,
                activeIdentityId, null, -1L,
                Collections.<FellowshipTrackedMemberSnapshot>emptyList(),
                goHereMarkers);
    }

    public FellowshipTrackingSnapshot(UUID ownerId,
                                 long synchronizationSequence,
                                 UUID activeIdentityId,
                                 UUID fellowshipId,
                                 long fellowshipRevision,
                                 List<FellowshipTrackedMemberSnapshot> trackedMembers,
                                 List<FellowshipGoHereMarkerSnapshot> goHereMarkers) {
        if (ownerId == null || activeIdentityId == null) {
            throw new IllegalArgumentException(
                    "owner and active character identities are required");
        }
        if (synchronizationSequence <= 0L) {
            throw new IllegalArgumentException(
                    "synchronization sequence must be positive");
        }
        boolean hasFellowship = fellowshipId != null;
        if (hasFellowship != (fellowshipRevision >= 0L)) {
            throw new IllegalArgumentException("invalid fellowship context");
        }
        List<FellowshipTrackedMemberSnapshot> safeMembers = copyMembers(
                trackedMembers);
        List<FellowshipGoHereMarkerSnapshot> safeMarkers = copyMarkers(
                goHereMarkers);
        if (!hasFellowship && !safeMembers.isEmpty()) {
            throw new IllegalArgumentException(
                    "fellowshipless snapshot cannot contain tracked members");
        }
        if (!hasFellowship && (safeMarkers.size() > 1
                || (!safeMarkers.isEmpty()
                && !activeIdentityId.equals(
                safeMarkers.get(0).getOwnerIdentityId())))) {
            throw new IllegalArgumentException(
                    "fellowshipless snapshot may contain only its active character marker");
        }
        this.ownerId = ownerId;
        this.synchronizationSequence = synchronizationSequence;
        this.activeIdentityId = activeIdentityId;
        this.fellowshipId = fellowshipId;
        this.fellowshipRevision = fellowshipRevision;
        this.trackedMembers = safeMembers;
        this.goHereMarkers = safeMarkers;
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public long getSynchronizationSequence() {
        return this.synchronizationSequence;
    }

    public UUID getActiveIdentityId() {
        return this.activeIdentityId;
    }

    public boolean hasFellowship() {
        return this.fellowshipId != null;
    }

    public UUID getFellowshipId() {
        return this.fellowshipId;
    }

    public long getFellowshipRevision() {
        return this.fellowshipRevision;
    }

    public List<FellowshipTrackedMemberSnapshot> getTrackedMembers() {
        return this.trackedMembers;
    }

    public List<FellowshipGoHereMarkerSnapshot> getGoHereMarkers() {
        return this.goHereMarkers;
    }

    public boolean hasSameContent(FellowshipTrackingSnapshot other) {
        return other != null
                && this.ownerId.equals(other.ownerId)
                && this.activeIdentityId.equals(other.activeIdentityId)
                && equalNullable(this.fellowshipId, other.fellowshipId)
                && this.fellowshipRevision == other.fellowshipRevision
                && this.trackedMembers.equals(other.trackedMembers)
                && this.goHereMarkers.equals(other.goHereMarkers);
    }

    private static List<FellowshipTrackedMemberSnapshot> copyMembers(
            List<FellowshipTrackedMemberSnapshot> source) {
        ArrayList<FellowshipTrackedMemberSnapshot> result =
                new ArrayList<FellowshipTrackedMemberSnapshot>();
        Set<UUID> identities = new HashSet<UUID>();
        if (source != null) {
            for (FellowshipTrackedMemberSnapshot member : source) {
                if (member == null || !identities.add(member.getIdentityId())) {
                    throw new IllegalArgumentException(
                            "tracked member identities must be unique");
                }
                result.add(member);
            }
        }
        if (result.size() > Fellowship.MAX_MEMBERS) {
            throw new IllegalArgumentException("too many tracked members");
        }
        return Collections.unmodifiableList(result);
    }

    private static List<FellowshipGoHereMarkerSnapshot> copyMarkers(
            List<FellowshipGoHereMarkerSnapshot> source) {
        ArrayList<FellowshipGoHereMarkerSnapshot> result =
                new ArrayList<FellowshipGoHereMarkerSnapshot>();
        Set<UUID> identities = new HashSet<UUID>();
        if (source != null) {
            for (FellowshipGoHereMarkerSnapshot marker : source) {
                if (marker == null
                        || !identities.add(marker.getOwnerIdentityId())) {
                    throw new IllegalArgumentException(
                            "marker owner identities must be unique");
                }
                result.add(marker);
            }
        }
        if (result.size() > Fellowship.MAX_MEMBERS) {
            throw new IllegalArgumentException("too many fellowship markers");
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean equalNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
