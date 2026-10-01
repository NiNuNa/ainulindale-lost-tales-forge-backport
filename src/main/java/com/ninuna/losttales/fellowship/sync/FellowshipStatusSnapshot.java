package com.ninuna.losttales.fellowship.sync;

import com.ninuna.losttales.fellowship.model.Fellowship;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Private runtime status snapshot for one account's active fellowship context. */
public final class FellowshipStatusSnapshot {

    private final UUID ownerId;
    private final long synchronizationSequence;
    private final UUID activeIdentityId;
    private final UUID fellowshipId;
    private final long fellowshipRevision;
    private final List<FellowshipMemberStatusSnapshot> memberStatuses;

    public FellowshipStatusSnapshot(UUID ownerId,
                               long synchronizationSequence,
                               UUID activeIdentityId,
                               UUID fellowshipId,
                               long fellowshipRevision,
                               List<FellowshipMemberStatusSnapshot> memberStatuses) {
        if (ownerId == null || synchronizationSequence <= 0L
                || activeIdentityId == null) {
            throw new IllegalArgumentException(
                    "owner, active character, and sequence are required");
        }
        this.ownerId = ownerId;
        this.synchronizationSequence = synchronizationSequence;
        this.activeIdentityId = activeIdentityId;

        if (fellowshipId == null) {
            if (fellowshipRevision != -1L
                    || (memberStatuses != null && !memberStatuses.isEmpty())) {
                throw new IllegalArgumentException(
                        "fellowship-less status snapshots must be empty");
            }
            this.fellowshipId = null;
            this.fellowshipRevision = -1L;
            this.memberStatuses = Collections.emptyList();
            return;
        }
        if (fellowshipRevision < 0L) {
            throw new IllegalArgumentException("fellowship revision must be valid");
        }

        ArrayList<FellowshipMemberStatusSnapshot> accepted =
                new ArrayList<FellowshipMemberStatusSnapshot>();
        Set<UUID> identityIds = new HashSet<UUID>();
        if (memberStatuses != null) {
            for (FellowshipMemberStatusSnapshot status : memberStatuses) {
                if (status == null || accepted.size() >= Fellowship.MAX_MEMBERS
                        || !identityIds.add(status.getIdentityId())) {
                    continue;
                }
                accepted.add(status);
            }
        }
        if (accepted.isEmpty()
                || !identityIds.contains(activeIdentityId)) {
            throw new IllegalArgumentException(
                    "fellowship status must include the active character");
        }
        this.fellowshipId = fellowshipId;
        this.fellowshipRevision = fellowshipRevision;
        this.memberStatuses = Collections.unmodifiableList(accepted);
    }

    public static FellowshipStatusSnapshot noFellowship(UUID ownerId,
                                              long sequence,
                                              UUID activeIdentityId) {
        return new FellowshipStatusSnapshot(ownerId, sequence, activeIdentityId,
                null, -1L,
                Collections.<FellowshipMemberStatusSnapshot>emptyList());
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

    public UUID getFellowshipId() {
        return this.fellowshipId;
    }

    public long getFellowshipRevision() {
        return this.fellowshipRevision;
    }

    public boolean hasFellowship() {
        return this.fellowshipId != null;
    }

    public List<FellowshipMemberStatusSnapshot> getMemberStatuses() {
        return this.memberStatuses;
    }

    public FellowshipMemberStatusSnapshot getMemberStatus(UUID identityId) {
        if (identityId == null) {
            return null;
        }
        for (FellowshipMemberStatusSnapshot status : this.memberStatuses) {
            if (identityId.equals(status.getIdentityId())) {
                return status;
            }
        }
        return null;
    }

    /** Compares authoritative content while intentionally ignoring sequence. */
    public boolean hasSameContent(FellowshipStatusSnapshot other) {
        if (other == null
                || !this.ownerId.equals(other.ownerId)
                || !this.activeIdentityId.equals(other.activeIdentityId)
                || !equal(this.fellowshipId, other.fellowshipId)
                || this.fellowshipRevision != other.fellowshipRevision
                || this.memberStatuses.size()
                != other.memberStatuses.size()) {
            return false;
        }
        for (int index = 0; index < this.memberStatuses.size(); index++) {
            if (!this.memberStatuses.get(index).equals(
                    other.memberStatuses.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean equal(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
