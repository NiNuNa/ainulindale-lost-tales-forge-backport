package com.ninuna.losttales.fellowship.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A fellowship, the server's own: a company of characters with a name, an
 * icon, a leader and guides, each member wearing a colour, and three
 * switches ({@link FellowshipSwitch}) that LOTR's fellowship behind it
 * carries out. Membership belongs to the character, never the account, and
 * an account has one character in a fellowship at most. Every change goes
 * through the fellowship service.
 */
public final class Fellowship {

    public static final int CURRENT_DATA_VERSION = 2;
    /**
     * The most members a fellowship can hold: the bound of storage, the wire
     * and every snapshot. LOTR's size setting decides when a fellowship is
     * full, never above this.
     */
    public static final int MAX_MEMBERS = 50;
    /** The fewest members a size setting may make a fellowship hold. */
    public static final int MIN_MEMBER_LIMIT = 2;
    /** The longest name a fellowship may have, in characters. */
    public static final int MAX_NAME_LENGTH = 32;
    /** The most fellowships one character may be in: the bound every list of them is held to. */
    public static final int MAX_FELLOWSHIPS_PER_IDENTITY = 8;

    private static final Comparator<FellowshipMember> MEMBER_ORDER = new Comparator<FellowshipMember>() {
        @Override
        public int compare(FellowshipMember left, FellowshipMember right) {
            if (left.getJoinedAt() < right.getJoinedAt()) {
                return -1;
            }
            if (left.getJoinedAt() > right.getJoinedAt()) {
                return 1;
            }
            return left.getIdentityId().toString().compareTo(right.getIdentityId().toString());
        }
    };

    private final UUID fellowshipId;
    private final long createdAt;
    private final int dataVersion;
    private final Map<UUID, FellowshipMember> membersByIdentityId =
            new LinkedHashMap<UUID, FellowshipMember>();
    private final Set<UUID> guides = new LinkedHashSet<UUID>();
    private final Set<FellowshipSwitch> switchesOn = EnumSet.noneOf(FellowshipSwitch.class);

    private UUID leaderIdentityId;
    private long revision;
    private String name;
    /** The item the fellowship wears; null for none. */
    private FellowshipIcon icon;

    /** A new fellowship of one, its leader, every switch on. */
    public static Fellowship createNew(UUID fellowshipId, String name,
                                       FellowshipMember leader, long createdAt) {
        if (leader == null) {
            throw new IllegalArgumentException("leader must not be null");
        }
        return new Fellowship(fellowshipId, leader.getIdentityId(),
                Collections.singletonList(leader), Collections.<UUID>emptyList(),
                name, null, EnumSet.allOf(FellowshipSwitch.class), createdAt, 0L,
                CURRENT_DATA_VERSION);
    }

    public Fellowship(UUID fellowshipId, UUID leaderIdentityId,
                      Iterable<FellowshipMember> members, Collection<UUID> guides,
                      String name, FellowshipIcon icon,
                      Set<FellowshipSwitch> switchesOn, long createdAt,
                      long revision, int dataVersion) {
        if (fellowshipId == null) {
            throw new IllegalArgumentException("fellowshipId must not be null");
        }
        if (!isWellFormedName(name)) {
            throw new IllegalArgumentException("fellowship name is not well formed");
        }
        this.fellowshipId = fellowshipId;
        this.name = name;
        this.icon = icon;
        this.createdAt = Math.max(0L, createdAt);
        this.revision = Math.max(0L, revision);
        this.dataVersion = dataVersion <= 0 ? CURRENT_DATA_VERSION : dataVersion;
        if (members != null) {
            for (FellowshipMember member : members) {
                if (member == null || this.membersByIdentityId.size() >= MAX_MEMBERS) {
                    continue;
                }
                if (!this.membersByIdentityId.containsKey(member.getIdentityId())) {
                    this.membersByIdentityId.put(member.getIdentityId(), member);
                }
            }
        }
        if (this.membersByIdentityId.isEmpty()) {
            throw new IllegalArgumentException("fellowship must contain at least one member");
        }
        if (leaderIdentityId == null || !this.membersByIdentityId.containsKey(leaderIdentityId)) {
            throw new IllegalArgumentException("leader must be a fellowship member");
        }
        this.leaderIdentityId = leaderIdentityId;
        if (guides != null) {
            for (UUID guide : guides) {
                if (guide != null && !guide.equals(leaderIdentityId)
                        && this.membersByIdentityId.containsKey(guide)) {
                    this.guides.add(guide);
                }
            }
        }
        if (switchesOn != null) {
            this.switchesOn.addAll(switchesOn);
        }
    }

    public UUID getFellowshipId() {
        return this.fellowshipId;
    }

    public UUID getLeaderIdentityId() {
        return this.leaderIdentityId;
    }

    public String getName() {
        return this.name;
    }

    /** The item the fellowship wears; null for none. */
    public FellowshipIcon getIcon() {
        return this.icon;
    }

    public FellowshipMember getLeader() {
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

    /** The guides, in the order they were made guides. */
    public Set<UUID> getGuides() {
        return Collections.unmodifiableSet(new LinkedHashSet<UUID>(this.guides));
    }

    public boolean isLeader(UUID identityId) {
        return identityId != null && identityId.equals(this.leaderIdentityId);
    }

    public boolean isGuide(UUID identityId) {
        return identityId != null && this.guides.contains(identityId);
    }

    /** Whether the identity leads the fellowship or guides it: who may invite, remove members and set the icon. */
    public boolean canManage(UUID identityId) {
        return isLeader(identityId) || isGuide(identityId);
    }

    public boolean isOn(FellowshipSwitch fellowshipSwitch) {
        return fellowshipSwitch != null && this.switchesOn.contains(fellowshipSwitch);
    }

    /** The switches that are on. */
    public Set<FellowshipSwitch> getSwitchesOn() {
        return this.switchesOn.isEmpty() ? EnumSet.noneOf(FellowshipSwitch.class)
                : EnumSet.copyOf(this.switchesOn);
    }

    /**
     * Whether the fellowship has as many members as {@code memberLimit} allows,
     * or more: a fellowship above a lowered limit keeps its members and is full.
     */
    public boolean isFull(int memberLimit) {
        return getMemberCount() >= clampMemberLimit(memberLimit);
    }

    /** A member limit held between {@link #MIN_MEMBER_LIMIT} and {@link #MAX_MEMBERS}. */
    public static int clampMemberLimit(int memberLimit) {
        return Math.max(MIN_MEMBER_LIMIT, Math.min(MAX_MEMBERS, memberLimit));
    }

    /**
     * Whether a name can be stored as a fellowship's: trimmed, not empty, at
     * most {@link #MAX_NAME_LENGTH} characters, with no formatting codes,
     * control characters or invisible marks. The profanity list is the
     * server's to apply when the name is given.
     */
    public static boolean isWellFormedName(String name) {
        if (name == null || name.length() == 0 || !name.equals(name.trim())
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

    public FellowshipMember getMember(UUID identityId) {
        return identityId == null ? null : this.membersByIdentityId.get(identityId);
    }

    /** Whether one of that account's identities is in the fellowship. An account has one identity in a fellowship at most. */
    public boolean hasMemberOwnedBy(UUID ownerId) {
        return memberOwnedBy(ownerId) != null;
    }

    /** The member one of that account's identities is; null for none. */
    public FellowshipMember memberOwnedBy(UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        for (FellowshipMember member : this.membersByIdentityId.values()) {
            if (ownerId.equals(member.getOwnerId())) {
                return member;
            }
        }
        return null;
    }

    /** The members, first joined first. */
    public List<FellowshipMember> getMembers() {
        ArrayList<FellowshipMember> members = new ArrayList<FellowshipMember>(this.membersByIdentityId.values());
        Collections.sort(members, MEMBER_ORDER);
        return Collections.unmodifiableList(members);
    }

    /**
     * The colour a new member wears: one nobody wears yet, else, once every
     * colour is worn, the one fewest wear.
     */
    public FellowshipColor nextColor() {
        Map<FellowshipColor, Integer> worn =
                new EnumMap<FellowshipColor, Integer>(FellowshipColor.class);
        for (FellowshipMember member : this.membersByIdentityId.values()) {
            Integer count = worn.get(member.getColor());
            worn.put(member.getColor(), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }
        FellowshipColor fewest = FellowshipColor.values()[0];
        int least = Integer.MAX_VALUE;
        for (FellowshipColor color : FellowshipColor.values()) {
            Integer count = worn.get(color);
            int each = count == null ? 0 : count.intValue();
            if (each < least) {
                least = each;
                fewest = color;
            }
        }
        return fewest;
    }

    /**
     * Whether a member may wear a colour: one no other member wears, or any
     * colour once every colour is worn by another, as in a fellowship larger
     * than the colours.
     */
    public boolean isColorAvailable(FellowshipColor color, UUID exceptIdentityId) {
        if (color == null) {
            return false;
        }
        Set<FellowshipColor> wornByOthers = EnumSet.noneOf(FellowshipColor.class);
        for (FellowshipMember member : this.membersByIdentityId.values()) {
            if (exceptIdentityId == null || !exceptIdentityId.equals(member.getIdentityId())) {
                wornByOthers.add(member.getColor());
            }
        }
        return !wornByOthers.contains(color)
                || wornByOthers.size() >= FellowshipColor.values().length;
    }

    /** Takes a new member in; false when they are a member already or the fellowship holds its most. */
    public boolean addMember(FellowshipMember member) {
        if (member == null || this.membersByIdentityId.containsKey(member.getIdentityId())
                || this.membersByIdentityId.size() >= MAX_MEMBERS) {
            return false;
        }
        this.membersByIdentityId.put(member.getIdentityId(), member);
        incrementRevision();
        return true;
    }

    /** Lets a member go; a leader going is followed by the member who joined first. */
    public FellowshipMember removeMember(UUID identityId) {
        FellowshipMember removed = identityId == null
                ? null : this.membersByIdentityId.remove(identityId);
        if (removed == null) {
            return null;
        }
        this.guides.remove(identityId);
        if (identityId.equals(this.leaderIdentityId)) {
            this.leaderIdentityId = selectSuccessorIdentityId();
            this.guides.remove(this.leaderIdentityId);
        }
        incrementRevision();
        return removed;
    }

    /** Makes another member the leader; the new leader is no guide any more. */
    public boolean transferLeadership(UUID targetIdentityId) {
        if (targetIdentityId == null
                || targetIdentityId.equals(this.leaderIdentityId)
                || !this.membersByIdentityId.containsKey(targetIdentityId)) {
            return false;
        }
        this.leaderIdentityId = targetIdentityId;
        this.guides.remove(targetIdentityId);
        incrementRevision();
        return true;
    }

    /** Makes a member other than the leader a guide, or no guide; false when nothing changes. */
    public boolean setGuide(UUID identityId, boolean guide) {
        if (identityId == null || isLeader(identityId)
                || !this.membersByIdentityId.containsKey(identityId)) {
            return false;
        }
        boolean changed = guide ? this.guides.add(identityId)
                : this.guides.remove(identityId);
        if (changed) {
            incrementRevision();
        }
        return changed;
    }

    /** Gives the fellowship a new name; false when it has it already. The name must be well formed. */
    public boolean rename(String newName) {
        if (!isWellFormedName(newName)) {
            throw new IllegalArgumentException("fellowship name is not well formed");
        }
        if (newName.equals(this.name)) {
            return false;
        }
        this.name = newName;
        incrementRevision();
        return true;
    }

    /** Gives the fellowship an icon, or takes it away with null; false when nothing changes. */
    public boolean setIcon(FellowshipIcon newIcon) {
        if (newIcon == null ? this.icon == null : newIcon.equals(this.icon)) {
            return false;
        }
        this.icon = newIcon;
        incrementRevision();
        return true;
    }

    /** Turns a switch on or off; false when it stands so already. */
    public boolean setSwitch(FellowshipSwitch fellowshipSwitch, boolean on) {
        if (fellowshipSwitch == null) {
            return false;
        }
        boolean changed = on ? this.switchesOn.add(fellowshipSwitch)
                : this.switchesOn.remove(fellowshipSwitch);
        if (changed) {
            incrementRevision();
        }
        return changed;
    }

    public boolean changeMemberColor(UUID identityId, FellowshipColor color) {
        FellowshipMember member = getMember(identityId);
        if (member == null || color == null || member.getColor() == color
                || !isColorAvailable(color, identityId)) {
            return false;
        }
        this.membersByIdentityId.put(identityId, member.withColor(color));
        incrementRevision();
        return true;
    }

    public boolean refreshMemberIdentity(UUID identityId, UUID ownerId, String characterName) {
        FellowshipMember member = getMember(identityId);
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
        this.guides.remove(this.leaderIdentityId);
        incrementRevision();
        return true;
    }

    private UUID selectSuccessorIdentityId() {
        List<FellowshipMember> ordered = getMembers();
        return ordered.isEmpty() ? null : ordered.get(0).getIdentityId();
    }

    private void incrementRevision() {
        if (this.revision < Long.MAX_VALUE) {
            this.revision++;
        }
    }
}
