package com.ninuna.losttales.party.sync;

import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyMember;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable authorized party snapshot sent only to current party members. */
public final class PartySnapshot {

    private final UUID partyId;
    private final UUID leaderIdentityId;
    private final String name;
    private final long createdAt;
    private final long revision;
    private final int dataVersion;
    private final List<PartyMemberSnapshot> members;
    private final Map<UUID, PartyMemberSnapshot> membersByIdentityId;

    public PartySnapshot(UUID partyId,
                         UUID leaderIdentityId,
                         String name,
                         long createdAt,
                         long revision,
                         int dataVersion,
                         List<PartyMemberSnapshot> members) {
        if (partyId == null || leaderIdentityId == null) {
            throw new IllegalArgumentException("party and leader identifiers must not be null");
        }
        if (!Party.isWellFormedName(name)) {
            throw new IllegalArgumentException("party name is not well formed");
        }
        this.partyId = partyId;
        this.name = name;
        this.createdAt = Math.max(0L, createdAt);
        this.revision = Math.max(0L, revision);
        this.dataVersion = Math.max(1, dataVersion);

        ArrayList<PartyMemberSnapshot> accepted = new ArrayList<PartyMemberSnapshot>();
        HashMap<UUID, PartyMemberSnapshot> byId = new HashMap<UUID, PartyMemberSnapshot>();
        Set<PartyColor> colors = EnumSet.noneOf(PartyColor.class);
        if (members != null) {
            for (PartyMemberSnapshot member : members) {
                if (member == null || accepted.size() >= Party.MAX_MEMBERS
                        || byId.containsKey(member.getIdentityId())
                        || !colors.add(member.getColor())) {
                    continue;
                }
                accepted.add(member);
                byId.put(member.getIdentityId(), member);
            }
        }
        if (accepted.isEmpty() || !byId.containsKey(leaderIdentityId)) {
            throw new IllegalArgumentException("party snapshot requires a valid leader and member list");
        }
        this.leaderIdentityId = leaderIdentityId;
        this.members = Collections.unmodifiableList(accepted);
        this.membersByIdentityId = Collections.unmodifiableMap(byId);
    }

    public static PartySnapshot fromParty(Party party) {
        if (party == null) {
            throw new IllegalArgumentException("party must not be null");
        }
        ArrayList<PartyMemberSnapshot> members = new ArrayList<PartyMemberSnapshot>();
        for (PartyMember member : party.getMembers()) {
            members.add(PartyMemberSnapshot.fromMember(member));
        }
        return new PartySnapshot(
                party.getPartyId(),
                party.getLeaderIdentityId(),
                party.getName(),
                party.getCreatedAt(),
                party.getRevision(),
                party.getDataVersion(),
                members);
    }

    public UUID getPartyId() {
        return this.partyId;
    }

    public UUID getLeaderIdentityId() {
        return this.leaderIdentityId;
    }

    /** What the leader named the party; empty while it has no name. */
    public String getName() {
        return this.name;
    }

    public long getCreatedAt() {
        return this.createdAt;
    }

    public long getRevision() {
        return this.revision;
    }

    public int getDataVersion() {
        return this.dataVersion;
    }

    public List<PartyMemberSnapshot> getMembers() {
        return this.members;
    }

    public int getMemberCount() {
        return this.members.size();
    }

    public PartyMemberSnapshot getMember(UUID identityId) {
        return identityId == null ? null : this.membersByIdentityId.get(identityId);
    }

    public boolean containsMember(UUID identityId) {
        return getMember(identityId) != null;
    }

    /** Whether one of the account's identities is a member. */
    public boolean hasMemberOwnedBy(UUID ownerId) {
        if (ownerId == null) {
            return false;
        }
        for (PartyMemberSnapshot member : this.members) {
            if (ownerId.equals(member.getOwnerId())) {
                return true;
            }
        }
        return false;
    }

    public boolean isLeader(UUID identityId) {
        return identityId != null && identityId.equals(this.leaderIdentityId);
    }
}
