package com.ninuna.losttales.party.sync;

import com.ninuna.losttales.party.model.Party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Private runtime status snapshot for one account's active party context. */
public final class PartyStatusSnapshot {

    private final UUID ownerId;
    private final long synchronizationSequence;
    private final UUID activeCharacterId;
    private final UUID partyId;
    private final long partyRevision;
    private final List<PartyMemberStatusSnapshot> memberStatuses;

    public PartyStatusSnapshot(UUID ownerId,
                               long synchronizationSequence,
                               UUID activeCharacterId,
                               UUID partyId,
                               long partyRevision,
                               List<PartyMemberStatusSnapshot> memberStatuses) {
        if (ownerId == null || synchronizationSequence <= 0L
                || activeCharacterId == null) {
            throw new IllegalArgumentException(
                    "owner, active character, and sequence are required");
        }
        this.ownerId = ownerId;
        this.synchronizationSequence = synchronizationSequence;
        this.activeCharacterId = activeCharacterId;

        if (partyId == null) {
            if (partyRevision != -1L
                    || (memberStatuses != null && !memberStatuses.isEmpty())) {
                throw new IllegalArgumentException(
                        "party-less status snapshots must be empty");
            }
            this.partyId = null;
            this.partyRevision = -1L;
            this.memberStatuses = Collections.emptyList();
            return;
        }
        if (partyRevision < 0L) {
            throw new IllegalArgumentException("party revision must be valid");
        }

        ArrayList<PartyMemberStatusSnapshot> accepted =
                new ArrayList<PartyMemberStatusSnapshot>();
        Set<UUID> characterIds = new HashSet<UUID>();
        if (memberStatuses != null) {
            for (PartyMemberStatusSnapshot status : memberStatuses) {
                if (status == null || accepted.size() >= Party.MAX_MEMBERS
                        || !characterIds.add(status.getCharacterId())) {
                    continue;
                }
                accepted.add(status);
            }
        }
        if (accepted.isEmpty()
                || !characterIds.contains(activeCharacterId)) {
            throw new IllegalArgumentException(
                    "party status must include the active character");
        }
        this.partyId = partyId;
        this.partyRevision = partyRevision;
        this.memberStatuses = Collections.unmodifiableList(accepted);
    }

    public static PartyStatusSnapshot noParty(UUID ownerId,
                                              long sequence,
                                              UUID activeCharacterId) {
        return new PartyStatusSnapshot(ownerId, sequence, activeCharacterId,
                null, -1L,
                Collections.<PartyMemberStatusSnapshot>emptyList());
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

    public UUID getPartyId() {
        return this.partyId;
    }

    public long getPartyRevision() {
        return this.partyRevision;
    }

    public boolean hasParty() {
        return this.partyId != null;
    }

    public List<PartyMemberStatusSnapshot> getMemberStatuses() {
        return this.memberStatuses;
    }

    public PartyMemberStatusSnapshot getMemberStatus(UUID characterId) {
        if (characterId == null) {
            return null;
        }
        for (PartyMemberStatusSnapshot status : this.memberStatuses) {
            if (characterId.equals(status.getCharacterId())) {
                return status;
            }
        }
        return null;
    }

    /** Compares authoritative content while intentionally ignoring sequence. */
    public boolean hasSameContent(PartyStatusSnapshot other) {
        if (other == null
                || !this.ownerId.equals(other.ownerId)
                || !this.activeCharacterId.equals(other.activeCharacterId)
                || !equal(this.partyId, other.partyId)
                || this.partyRevision != other.partyRevision
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
