package com.ninuna.losttales.fellowship.sync;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipIcon;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One fellowship as its members' clients see it: its name, icon, leader,
 * guides, switches and members, each with where they stand now. Sent only
 * to its members.
 */
public final class FellowshipSnapshot {

    /** Where a member stands, as the server sees it when it builds a snapshot. */
    public interface Presence {
        FellowshipMemberPresence of(FellowshipMember member);

        /** The other character the member's account plays now; empty for none. */
        String elsewhereName(FellowshipMember member);
    }

    private final UUID fellowshipId;
    private final UUID leaderIdentityId;
    private final String name;
    private final FellowshipIcon icon;
    private final Set<FellowshipSwitch> switchesOn;
    private final Set<UUID> guides;
    private final long createdAt;
    private final long revision;
    private final int dataVersion;
    private final List<FellowshipMemberSnapshot> members;
    private final Map<UUID, FellowshipMemberSnapshot> membersByIdentityId;

    public FellowshipSnapshot(UUID fellowshipId, UUID leaderIdentityId, String name,
                              FellowshipIcon icon, Set<FellowshipSwitch> switchesOn,
                              Collection<UUID> guides, long createdAt, long revision,
                              int dataVersion, List<FellowshipMemberSnapshot> members) {
        if (fellowshipId == null || leaderIdentityId == null) {
            throw new IllegalArgumentException("fellowship and leader identifiers must not be null");
        }
        if (!Fellowship.isWellFormedName(name)) {
            throw new IllegalArgumentException("fellowship name is not well formed");
        }
        this.fellowshipId = fellowshipId;
        this.name = name;
        this.icon = icon;
        this.switchesOn = switchesOn == null || switchesOn.isEmpty()
                ? EnumSet.noneOf(FellowshipSwitch.class) : EnumSet.copyOf(switchesOn);
        this.createdAt = Math.max(0L, createdAt);
        this.revision = Math.max(0L, revision);
        this.dataVersion = Math.max(1, dataVersion);
        ArrayList<FellowshipMemberSnapshot> accepted = new ArrayList<FellowshipMemberSnapshot>();
        HashMap<UUID, FellowshipMemberSnapshot> byId = new HashMap<UUID, FellowshipMemberSnapshot>();
        if (members != null) {
            for (FellowshipMemberSnapshot member : members) {
                if (member == null || accepted.size() >= Fellowship.MAX_MEMBERS
                        || byId.containsKey(member.getIdentityId())) {
                    continue;
                }
                accepted.add(member);
                byId.put(member.getIdentityId(), member);
            }
        }
        if (accepted.isEmpty() || !byId.containsKey(leaderIdentityId)) {
            throw new IllegalArgumentException("fellowship snapshot requires a valid leader and member list");
        }
        this.leaderIdentityId = leaderIdentityId;
        Set<UUID> acceptedGuides = new LinkedHashSet<UUID>();
        if (guides != null) {
            for (UUID guide : guides) {
                if (guide != null && byId.containsKey(guide) && !guide.equals(leaderIdentityId)) {
                    acceptedGuides.add(guide);
                }
            }
        }
        this.guides = Collections.unmodifiableSet(acceptedGuides);
        this.members = Collections.unmodifiableList(accepted);
        this.membersByIdentityId = Collections.unmodifiableMap(byId);
    }

    public static FellowshipSnapshot fromFellowship(Fellowship fellowship, Presence presence) {
        if (fellowship == null || presence == null) {
            throw new IllegalArgumentException("fellowship and presence must not be null");
        }
        ArrayList<FellowshipMemberSnapshot> members = new ArrayList<FellowshipMemberSnapshot>();
        for (FellowshipMember member : fellowship.getMembers()) {
            members.add(FellowshipMemberSnapshot.fromMember(member, presence.of(member),
                    presence.elsewhereName(member)));
        }
        return new FellowshipSnapshot(fellowship.getFellowshipId(),
                fellowship.getLeaderIdentityId(), fellowship.getName(),
                fellowship.getIcon(), fellowship.getSwitchesOn(), fellowship.getGuides(),
                fellowship.getCreatedAt(), fellowship.getRevision(),
                fellowship.getDataVersion(), members);
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

    public boolean isOn(FellowshipSwitch fellowshipSwitch) {
        return fellowshipSwitch != null && this.switchesOn.contains(fellowshipSwitch);
    }

    /** The switches that are on. */
    public Set<FellowshipSwitch> getSwitchesOn() {
        return this.switchesOn.isEmpty() ? EnumSet.noneOf(FellowshipSwitch.class)
                : EnumSet.copyOf(this.switchesOn);
    }

    public Set<UUID> getGuides() {
        return this.guides;
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

    public List<FellowshipMemberSnapshot> getMembers() {
        return this.members;
    }

    public int getMemberCount() {
        return this.members.size();
    }

    public FellowshipMemberSnapshot getMember(UUID identityId) {
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
        for (FellowshipMemberSnapshot member : this.members) {
            if (ownerId.equals(member.getOwnerId())) {
                return true;
            }
        }
        return false;
    }

    public boolean isLeader(UUID identityId) {
        return identityId != null && identityId.equals(this.leaderIdentityId);
    }

    public boolean isGuide(UUID identityId) {
        return identityId != null && this.guides.contains(identityId);
    }

    /** Whether the identity leads or guides the fellowship. */
    public boolean canManage(UUID identityId) {
        return isLeader(identityId) || isGuide(identityId);
    }
}
