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
    /**
     * The most members a party can hold: the bound of storage, the wire and
     * every snapshot. The server's setting decides when a party is full,
     * never above this.
     */
    public static final int MAX_MEMBERS = 8;
    /** The fewest members the server's setting may make a party hold. */
    public static final int MIN_MEMBER_LIMIT = 2;
    /** The longest name a leader may give the party, in characters. */
    public static final int MAX_NAME_LENGTH = 24;

    private static final Comparator<PartyMember> MEMBER_ORDER = new Comparator<PartyMember>() {
        @Override
        public int compare(PartyMember left, PartyMember right) {
            if (left.getJoinedAt() < right.getJoinedAt()) {
                return -1;
            }
            if (left.getJoinedAt() > right.getJoinedAt()) {
                return 1;
            }
            return left.getIdentityId().toString().compareTo(right.getIdentityId().toString());
        }
    };

    private final UUID partyId;
    private final long createdAt;
    private final int dataVersion;
    private final Map<UUID, PartyMember> membersByIdentityId =
            new LinkedHashMap<UUID, PartyMember>();

    private UUID leaderIdentityId;
    private long revision;
    /** What the leader named the party; empty while it has no name. */
    private String name;

    public static Party createNew(UUID partyId, PartyMember leader, long createdAt) {
        if (leader == null) {
            throw new IllegalArgumentException("leader must not be null");
        }
        ArrayList<PartyMember> members = new ArrayList<PartyMember>();
        members.add(leader);
        return new Party(partyId, leader.getIdentityId(), members,
                createdAt, 0L, CURRENT_DATA_VERSION);
    }

    /** A party with no name. */
    public Party(UUID partyId, UUID leaderIdentityId, Iterable<PartyMember> members,
                 long createdAt, long revision, int dataVersion) {
        this(partyId, leaderIdentityId, members, "", createdAt, revision,
                dataVersion);
    }

    public Party(UUID partyId, UUID leaderIdentityId, Iterable<PartyMember> members,
                 String name, long createdAt, long revision, int dataVersion) {
        if (partyId == null) {
            throw new IllegalArgumentException("partyId must not be null");
        }
        if (!isWellFormedName(name)) {
            throw new IllegalArgumentException("party name is not well formed");
        }
        this.partyId = partyId;
        this.name = name;
        this.createdAt = Math.max(0L, createdAt);
        this.revision = Math.max(0L, revision);
        this.dataVersion = dataVersion <= 0 ? CURRENT_DATA_VERSION : dataVersion;

        if (members != null) {
            for (PartyMember member : members) {
                if (member == null || this.membersByIdentityId.size() >= MAX_MEMBERS) {
                    continue;
                }
                if (!this.membersByIdentityId.containsKey(member.getIdentityId())) {
                    this.membersByIdentityId.put(member.getIdentityId(), member);
                }
            }
        }
        if (this.membersByIdentityId.isEmpty()) {
            throw new IllegalArgumentException("party must contain at least one member");
        }
        if (leaderIdentityId == null || !this.membersByIdentityId.containsKey(leaderIdentityId)) {
            throw new IllegalArgumentException("leader must be a party member");
        }
        if (!hasUniqueColors()) {
            throw new IllegalArgumentException("party member colors must be unique");
        }
        this.leaderIdentityId = leaderIdentityId;
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

    public PartyMember getLeader() {
        return this.membersByIdentityId.get(this.leaderIdentityId);
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
        return this.membersByIdentityId.size();
    }

    /**
     * Whether the party has as many members as {@code memberLimit} allows,
     * or more: a party above a lowered limit keeps its members and is full.
     */
    public boolean isFull(int memberLimit) {
        return getMemberCount() >= clampMemberLimit(memberLimit);
    }

    /** A member limit held between {@link #MIN_MEMBER_LIMIT} and {@link #MAX_MEMBERS}. */
    public static int clampMemberLimit(int memberLimit) {
        return Math.max(MIN_MEMBER_LIMIT, Math.min(MAX_MEMBERS, memberLimit));
    }

    /**
     * Whether a name can be stored as a party's: empty, or trimmed and at
     * most {@link #MAX_NAME_LENGTH} characters, with no formatting codes,
     * control characters or invisible marks. The profanity list is the
     * server's to apply when the name is given.
     */
    public static boolean isWellFormedName(String name) {
        if (name == null || !name.equals(name.trim())
                || name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            return false;
        }
        for (int index = 0; index < name.length(); ) {
            int codePoint = name.codePointAt(index);
            int type = Character.getType(codePoint);
            if (codePoint == 0xA7 || Character.isISOControl(codePoint)
                    || type == Character.FORMAT
                    || type == Character.SURROGATE
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR
                    || type == Character.UNASSIGNED) {
                return false;
            }
            index += Character.charCount(codePoint);
        }
        return true;
    }

    public boolean containsMember(UUID identityId) {
        return identityId != null && this.membersByIdentityId.containsKey(identityId);
    }

    public PartyMember getMember(UUID identityId) {
        return identityId == null ? null : this.membersByIdentityId.get(identityId);
    }

    /** Whether one of that account's identities is in the party. An account has one identity in a party at most. */
    public boolean hasMemberOwnedBy(UUID ownerId) {
        if (ownerId == null) {
            return false;
        }
        for (PartyMember member : this.membersByIdentityId.values()) {
            if (ownerId.equals(member.getOwnerId())) {
                return true;
            }
        }
        return false;
    }

    public List<PartyMember> getMembers() {
        ArrayList<PartyMember> members = new ArrayList<PartyMember>(this.membersByIdentityId.values());
        Collections.sort(members, MEMBER_ORDER);
        return Collections.unmodifiableList(members);
    }

    public PartyColor getFirstAvailableColor() {
        Set<PartyColor> used = EnumSet.noneOf(PartyColor.class);
        for (PartyMember member : this.membersByIdentityId.values()) {
            used.add(member.getColor());
        }
        for (PartyColor color : PartyColor.values()) {
            if (!used.contains(color)) {
                return color;
            }
        }
        return null;
    }

    public boolean isColorAvailable(PartyColor color, UUID exceptIdentityId) {
        if (color == null) {
            return false;
        }
        for (PartyMember member : this.membersByIdentityId.values()) {
            if (exceptIdentityId != null && exceptIdentityId.equals(member.getIdentityId())) {
                continue;
            }
            if (color == member.getColor()) {
                return false;
            }
        }
        return true;
    }

    public PartyMember removeMember(UUID identityId) {
        PartyMember removed = identityId == null
                ? null : this.membersByIdentityId.remove(identityId);
        if (removed == null) {
            return null;
        }
        if (identityId.equals(this.leaderIdentityId)) {
            this.leaderIdentityId = selectSuccessorIdentityId();
        }
        incrementRevision();
        return removed;
    }

    public boolean transferLeadership(UUID targetIdentityId) {
        if (targetIdentityId == null
                || targetIdentityId.equals(this.leaderIdentityId)
                || !this.membersByIdentityId.containsKey(targetIdentityId)) {
            return false;
        }
        this.leaderIdentityId = targetIdentityId;
        incrementRevision();
        return true;
    }

    /**
     * Gives the party a name, or takes its name away with an empty one;
     * false when the party already has it. The name must be well formed.
     */
    public boolean rename(String newName) {
        if (!isWellFormedName(newName)) {
            throw new IllegalArgumentException("party name is not well formed");
        }
        if (newName.equals(this.name)) {
            return false;
        }
        this.name = newName;
        incrementRevision();
        return true;
    }

    public boolean changeMemberColor(UUID identityId, PartyColor color) {
        PartyMember member = getMember(identityId);
        if (member == null || color == null) {
            return false;
        }
        if (member.getColor() == color) {
            return false;
        }
        if (!isColorAvailable(color, identityId)) {
            return false;
        }
        this.membersByIdentityId.put(identityId, member.withColor(color));
        incrementRevision();
        return true;
    }

    public boolean refreshMemberIdentity(UUID identityId, UUID ownerId, String characterName) {
        PartyMember member = getMember(identityId);
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
        this.membersByIdentityId.put(identityId, member.withIdentity(ownerId, safeName));
        incrementRevision();
        return true;
    }

    public boolean hasValidLeader() {
        return this.leaderIdentityId != null
                && this.membersByIdentityId.containsKey(this.leaderIdentityId);
    }

    public boolean repairLeaderIfNecessary() {
        if (hasValidLeader()) {
            return false;
        }
        this.leaderIdentityId = selectSuccessorIdentityId();
        incrementRevision();
        return true;
    }

    private UUID selectSuccessorIdentityId() {
        List<PartyMember> ordered = getMembers();
        return ordered.isEmpty() ? null : ordered.get(0).getIdentityId();
    }

    private boolean hasUniqueColors() {
        Set<PartyColor> colors = EnumSet.noneOf(PartyColor.class);
        for (PartyMember member : this.membersByIdentityId.values()) {
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
