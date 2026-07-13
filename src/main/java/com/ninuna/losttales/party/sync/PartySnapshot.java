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
    private final UUID leaderCharacterId;
    private final long createdAt;
    private final long revision;
    private final int dataVersion;
    private final List<PartyMemberSnapshot> members;
    private final Map<UUID, PartyMemberSnapshot> membersByCharacterId;

    public PartySnapshot(UUID partyId,
                         UUID leaderCharacterId,
                         long createdAt,
                         long revision,
                         int dataVersion,
                         List<PartyMemberSnapshot> members) {
        if (partyId == null || leaderCharacterId == null) {
            throw new IllegalArgumentException("party and leader identifiers must not be null");
        }
        this.partyId = partyId;
        this.createdAt = Math.max(0L, createdAt);
        this.revision = Math.max(0L, revision);
        this.dataVersion = Math.max(1, dataVersion);

        ArrayList<PartyMemberSnapshot> accepted = new ArrayList<PartyMemberSnapshot>();
        HashMap<UUID, PartyMemberSnapshot> byId = new HashMap<UUID, PartyMemberSnapshot>();
        Set<PartyColor> colors = EnumSet.noneOf(PartyColor.class);
        if (members != null) {
            for (PartyMemberSnapshot member : members) {
                if (member == null || accepted.size() >= Party.MAX_MEMBERS
                        || byId.containsKey(member.getCharacterId())
                        || !colors.add(member.getColor())) {
                    continue;
                }
                accepted.add(member);
                byId.put(member.getCharacterId(), member);
            }
        }
        if (accepted.isEmpty() || !byId.containsKey(leaderCharacterId)) {
            throw new IllegalArgumentException("party snapshot requires a valid leader and member list");
        }
        this.leaderCharacterId = leaderCharacterId;
        this.members = Collections.unmodifiableList(accepted);
        this.membersByCharacterId = Collections.unmodifiableMap(byId);
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
                party.getLeaderCharacterId(),
                party.getCreatedAt(),
                party.getRevision(),
                party.getDataVersion(),
                members);
    }

    public UUID getPartyId() {
        return this.partyId;
    }

    public UUID getLeaderCharacterId() {
        return this.leaderCharacterId;
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

    public boolean isFull() {
        return this.members.size() >= Party.MAX_MEMBERS;
    }

    public PartyMemberSnapshot getMember(UUID characterId) {
        return characterId == null ? null : this.membersByCharacterId.get(characterId);
    }

    public boolean containsMember(UUID characterId) {
        return getMember(characterId) != null;
    }

    public boolean isLeader(UUID characterId) {
        return characterId != null && characterId.equals(this.leaderCharacterId);
    }
}
