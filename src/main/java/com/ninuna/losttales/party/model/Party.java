package com.ninuna.losttales.party.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-owned persistent party. All mutations are performed through PartyService. */
public final class Party {

    public static final int CURRENT_DATA_VERSION = 1;
    public static final int MAX_MEMBERS = 4;

    private static final Comparator<PartyMember> MEMBER_ORDER = new Comparator<PartyMember>() {
        @Override
        public int compare(PartyMember left, PartyMember right) {
            if (left.getJoinedAt() < right.getJoinedAt()) {
                return -1;
            }
            if (left.getJoinedAt() > right.getJoinedAt()) {
                return 1;
            }
            return left.getCharacterId().toString().compareTo(right.getCharacterId().toString());
        }
    };

    private final UUID partyId;
    private final long createdAt;
    private final int dataVersion;
    private final Map<UUID, PartyMember> membersByCharacterId =
            new LinkedHashMap<UUID, PartyMember>();

    private UUID leaderCharacterId;
    private long revision;

    public static Party createNew(UUID partyId, PartyMember leader, long createdAt) {
        if (leader == null) {
            throw new IllegalArgumentException("leader must not be null");
        }
        ArrayList<PartyMember> members = new ArrayList<PartyMember>();
        members.add(leader);
        return new Party(partyId, leader.getCharacterId(), members,
                createdAt, 0L, CURRENT_DATA_VERSION);
    }

    public Party(UUID partyId, UUID leaderCharacterId, Iterable<PartyMember> members,
                 long createdAt, long revision, int dataVersion) {
        if (partyId == null) {
            throw new IllegalArgumentException("partyId must not be null");
        }
        this.partyId = partyId;
        this.createdAt = Math.max(0L, createdAt);
        this.revision = Math.max(0L, revision);
        this.dataVersion = dataVersion <= 0 ? CURRENT_DATA_VERSION : dataVersion;

        if (members != null) {
            for (PartyMember member : members) {
                if (member == null || this.membersByCharacterId.size() >= MAX_MEMBERS) {
                    continue;
                }
                if (!this.membersByCharacterId.containsKey(member.getCharacterId())) {
                    this.membersByCharacterId.put(member.getCharacterId(), member);
                }
            }
        }
        if (this.membersByCharacterId.isEmpty()) {
            throw new IllegalArgumentException("party must contain at least one member");
        }
        if (leaderCharacterId == null || !this.membersByCharacterId.containsKey(leaderCharacterId)) {
            throw new IllegalArgumentException("leader must be a party member");
        }
        if (!hasUniqueColors()) {
            throw new IllegalArgumentException("party member colors must be unique");
        }
        this.leaderCharacterId = leaderCharacterId;
    }

    public UUID getPartyId() {
        return this.partyId;
    }

    public UUID getLeaderCharacterId() {
        return this.leaderCharacterId;
    }

    public PartyMember getLeader() {
        return this.membersByCharacterId.get(this.leaderCharacterId);
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

    public int getMemberCount() {
        return this.membersByCharacterId.size();
    }

    public boolean isFull() {
        return getMemberCount() >= MAX_MEMBERS;
    }

    public boolean containsMember(UUID characterId) {
        return characterId != null && this.membersByCharacterId.containsKey(characterId);
    }

    public PartyMember getMember(UUID characterId) {
        return characterId == null ? null : this.membersByCharacterId.get(characterId);
    }

    public List<PartyMember> getMembers() {
        ArrayList<PartyMember> members = new ArrayList<PartyMember>(this.membersByCharacterId.values());
        Collections.sort(members, MEMBER_ORDER);
        return Collections.unmodifiableList(members);
    }

    public PartyColor getFirstAvailableColor() {
        Set<PartyColor> used = EnumSet.noneOf(PartyColor.class);
        for (PartyMember member : this.membersByCharacterId.values()) {
            used.add(member.getColor());
        }
        for (PartyColor color : PartyColor.values()) {
            if (!used.contains(color)) {
                return color;
            }
        }
        return null;
    }

    public boolean isColorAvailable(PartyColor color, UUID exceptCharacterId) {
        if (color == null) {
            return false;
        }
        for (PartyMember member : this.membersByCharacterId.values()) {
            if (exceptCharacterId != null && exceptCharacterId.equals(member.getCharacterId())) {
                continue;
            }
            if (color == member.getColor()) {
                return false;
            }
        }
        return true;
    }

    public boolean addMember(PartyMember member) {
        if (member == null || isFull()
                || this.membersByCharacterId.containsKey(member.getCharacterId())
                || !isColorAvailable(member.getColor(), null)) {
            return false;
        }
        this.membersByCharacterId.put(member.getCharacterId(), member);
        incrementRevision();
        return true;
    }

    public PartyMember removeMember(UUID characterId) {
        PartyMember removed = characterId == null
                ? null : this.membersByCharacterId.remove(characterId);
        if (removed == null) {
            return null;
        }
        if (characterId.equals(this.leaderCharacterId)) {
            this.leaderCharacterId = selectSuccessorCharacterId();
        }
        incrementRevision();
        return removed;
    }

    public boolean transferLeadership(UUID targetCharacterId) {
        if (targetCharacterId == null
                || targetCharacterId.equals(this.leaderCharacterId)
                || !this.membersByCharacterId.containsKey(targetCharacterId)) {
            return false;
        }
        this.leaderCharacterId = targetCharacterId;
        incrementRevision();
        return true;
    }

    public boolean changeMemberColor(UUID characterId, PartyColor color) {
        PartyMember member = getMember(characterId);
        if (member == null || color == null) {
            return false;
        }
        if (member.getColor() == color) {
            return false;
        }
        if (!isColorAvailable(color, characterId)) {
            return false;
        }
        this.membersByCharacterId.put(characterId, member.withColor(color));
        incrementRevision();
        return true;
    }

    public boolean refreshMemberIdentity(UUID characterId, UUID ownerId, String characterName) {
        PartyMember member = getMember(characterId);
        if (member == null || ownerId == null) {
            return false;
        }
        String safeName = characterName == null ? "Unknown" : characterName.trim();
        if (safeName.length() == 0) {
            safeName = "Unknown";
        }
        if (ownerId.equals(member.getOwnerId()) && safeName.equals(member.getCharacterName())) {
            return false;
        }
        this.membersByCharacterId.put(characterId, member.withIdentity(ownerId, safeName));
        incrementRevision();
        return true;
    }

    public boolean hasValidLeader() {
        return this.leaderCharacterId != null
                && this.membersByCharacterId.containsKey(this.leaderCharacterId);
    }

    public boolean repairLeaderIfNecessary() {
        if (hasValidLeader()) {
            return false;
        }
        this.leaderCharacterId = selectSuccessorCharacterId();
        incrementRevision();
        return true;
    }

    private UUID selectSuccessorCharacterId() {
        List<PartyMember> ordered = getMembers();
        return ordered.isEmpty() ? null : ordered.get(0).getCharacterId();
    }

    private boolean hasUniqueColors() {
        Set<PartyColor> colors = EnumSet.noneOf(PartyColor.class);
        for (PartyMember member : this.membersByCharacterId.values()) {
            if (!colors.add(member.getColor())) {
                return false;
            }
        }
        return true;
    }

    private void incrementRevision() {
        if (this.revision < Long.MAX_VALUE) {
            this.revision++;
        }
    }
}
