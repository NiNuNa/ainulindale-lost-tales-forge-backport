package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.state.CharacterPlayerStateAccount;
import com.ninuna.losttales.character.state.CharacterPlayerStateRecord;
import com.ninuna.losttales.character.state.CharacterPlayerStateStorage;
import com.ninuna.losttales.character.state.CharacterPlayerStateWorldData;
import com.ninuna.losttales.character.state.component.LotrCustomWaypointStateComponent;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.compat.lotr.LotrFellowshipMirror;
import com.ninuna.losttales.compat.lotr.LotrSharedWaypoints;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.storage.FellowshipStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberPresence;
import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps LOTR's fellowship behind each of ours in step with it
 * ({@link LotrFellowshipMirror}): after every change to a fellowship, when
 * one of its members comes, goes or changes character, and once LOTR has
 * read its data after the server starts. LOTR's fellowship holds the
 * accounts playing a member of ours, now or as they logged out: a
 * character not played is away, not gone, and LOTR keeps the waypoints it
 * shares while its account stays. It is held by the leader's account
 * while that plays the leader, else by the first such account, and while
 * nobody plays one by the leader's account alone. The shared waypoints of
 * members whose account plays another character are handed out after
 * each change ({@link LotrSharedWaypoints}).
 */
public final class FellowshipMirrors {
    /** Whether every mirror was set right since the server started. */
    private static boolean settled;

    /** Whether a member's account is offline and logged out playing that member. */
    interface LastPlayed {
        boolean of(FellowshipMember member);
    }

    private FellowshipMirrors() {}

    /** Forgets that the mirrors were set right, as the server starts or stops. */
    public static synchronized void clear() {
        settled = false;
    }

    /** Sets every mirror right once LOTR has read its data after the server starts. */
    static void settleOnce(World world) {
        synchronized (FellowshipMirrors.class) {
            if (settled || !LotrFellowshipMirror.isReady()) {
                return;
            }
            settled = true;
        }
        reconcileAll(world);
    }

    /** Sets every mirror right, and ends those of fellowships gone. */
    static void reconcileAll(World world) {
        FellowshipWorldData data = dataOf(world);
        if (data != null) {
            reconcile(world, data, data.getFellowships());
        }
    }

    /** Sets right the mirrors of the fellowships one of these accounts has a character in, and ends those of fellowships gone. */
    static void reconcileAccounts(World world, Collection<UUID> ownerIds) {
        FellowshipWorldData data = dataOf(world);
        if (data == null || ownerIds.isEmpty()) {
            return;
        }
        List<Fellowship> touched = new ArrayList<Fellowship>();
        for (Fellowship fellowship : data.getFellowships()) {
            for (UUID ownerId : ownerIds) {
                if (fellowship.hasMemberOwnedBy(ownerId)) {
                    touched.add(fellowship);
                    break;
                }
            }
        }
        reconcile(world, data, touched);
    }

    /** Sets right the mirror of one fellowship, and ends those of fellowships gone. */
    static void reconcile(World world, UUID fellowshipId) {
        FellowshipWorldData data = dataOf(world);
        if (data == null) {
            return;
        }
        Fellowship fellowship = data.getFellowship(fellowshipId);
        List<Fellowship> touched = new ArrayList<Fellowship>();
        if (fellowship != null) {
            touched.add(fellowship);
        }
        reconcile(world, data, touched);
    }

    private static void reconcile(World world, FellowshipWorldData data,
                                  Collection<Fellowship> fellowships) {
        if (!LotrFellowshipMirror.isReady() || data.isReadOnlyForNewerVersion()) {
            return;
        }
        for (Map.Entry<UUID, UUID> ended : data.getEndedMirrors().entrySet()) {
            if (LotrFellowshipMirror.end(ended.getValue())) {
                LotrSharedWaypoints.forget(ended.getValue());
                data.forgetMirror(ended.getKey());
            }
        }
        FellowshipOnlineView online = onlineView(world);
        CharacterWorldData characters = charactersOf(world);
        LastPlayed lastPlayed = lastPlayed(characters);
        for (Fellowship fellowship : fellowships) {
            UUID mirrorId = LotrFellowshipMirror.apply(
                    data.getMirrorId(fellowship.getFellowshipId()),
                    shapeOf(fellowship, online, lastPlayed));
            if (mirrorId != null) {
                data.setMirrorId(fellowship.getFellowshipId(), mirrorId);
                LotrSharedWaypoints.update(mirrorId,
                        awayOf(world, fellowship, online, lastPlayed));
            }
        }
    }

    /** Whether the member's account stands in LOTR's fellowship: playing it now, or logged out playing it. */
    static boolean inMirror(FellowshipMember member, FellowshipSnapshot.Presence presence,
                            LastPlayed lastPlayed) {
        FellowshipMemberPresence now = presence.of(member);
        return now == FellowshipMemberPresence.HERE
                || now == FellowshipMemberPresence.AWAY && lastPlayed.of(member);
    }

    /** The members no account in LOTR's fellowship plays, with the waypoints each saved as it was last played. */
    private static List<LotrSharedWaypoints.Away> awayOf(World world, Fellowship fellowship,
            FellowshipSnapshot.Presence presence, LastPlayed lastPlayed) {
        List<LotrSharedWaypoints.Away> away = new ArrayList<LotrSharedWaypoints.Away>();
        for (FellowshipMember member : fellowship.getMembers()) {
            if (inMirror(member, presence, lastPlayed)) {
                continue;
            }
            NBTTagCompound waypoints = savedWaypointsOf(world, member);
            if (waypoints != null) {
                away.add(new LotrSharedWaypoints.Away(member.getOwnerId(),
                        member.getCharacterName(), waypoints));
            }
        }
        return away;
    }

    /** The custom waypoints a member saved as it was last played; null where none can be read. */
    private static NBTTagCompound savedWaypointsOf(World world, FellowshipMember member) {
        try {
            CharacterPlayerStateWorldData states = CharacterPlayerStateStorage.get(world,
                    member.getOwnerId());
            if (states.isReadOnlyForNewerVersion() || states.isOwnerBlocked(member.getOwnerId())) {
                return null;
            }
            CharacterPlayerStateAccount account = states.getAccount(member.getOwnerId());
            CharacterPlayerStateRecord record = account == null ? null
                    : account.getRecord(member.getIdentityId());
            return record == null || record.getCurrent() == null ? null
                    : LotrCustomWaypointStateComponent.waypointsOf(record.getCurrent()
                            .getComponent(LotrCustomWaypointStateComponent.ID));
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** Whether each member's account is offline and logged out playing it, as its roster says. */
    private static LastPlayed lastPlayed(final CharacterWorldData characters) {
        return new LastPlayed() {
            @Override
            public boolean of(FellowshipMember member) {
                CharacterRoster roster = characters == null ? null
                        : characters.getRoster(member.getOwnerId());
                RoleplayCharacter active = roster == null ? null : roster.getActiveCharacter();
                return active != null && member.getIdentityId().equals(active.getCharacterId());
            }
        };
    }

    /**
     * What LOTR's fellowship behind {@code fellowship} should hold, as its
     * members stand now: the accounts playing one, those online first,
     * then those that logged out playing one.
     */
    static LotrFellowshipMirror.Shape shapeOf(Fellowship fellowship,
                                              FellowshipSnapshot.Presence presence,
                                              LastPlayed lastPlayed) {
        UUID leaderAccount = fellowship.getLeader().getOwnerId();
        List<UUID> present = new ArrayList<UUID>();
        List<UUID> loggedOut = new ArrayList<UUID>();
        Set<UUID> guides = new LinkedHashSet<UUID>();
        for (FellowshipMember member : fellowship.getMembers()) {
            if (!inMirror(member, presence, lastPlayed)) {
                continue;
            }
            (presence.of(member) == FellowshipMemberPresence.HERE ? present : loggedOut)
                    .add(member.getOwnerId());
            if (fellowship.isGuide(member.getIdentityId())) {
                guides.add(member.getOwnerId());
            }
        }
        present.addAll(loggedOut);
        UUID owner = present.isEmpty() || present.contains(leaderAccount)
                ? leaderAccount : present.get(0);
        return new LotrFellowshipMirror.Shape(fellowship.getName(), fellowship.getIcon(),
                fellowship.isOn(FellowshipSwitch.NO_FIGHTING),
                fellowship.isOn(FellowshipSwitch.NO_HIRED_HARM),
                fellowship.isOn(FellowshipSwitch.SHOWN_ON_MAP),
                owner, present, guides);
    }

    private static FellowshipOnlineView onlineView(World world) {
        try {
            return FellowshipOnlineView.collect(CharacterStorage.get(world));
        } catch (RuntimeException failure) {
            LostTalesLog.warning("Who plays which character could not be read for the fellowships: %s",
                    failure.toString());
            return FellowshipOnlineView.none();
        }
    }

    private static CharacterWorldData charactersOf(World world) {
        try {
            return CharacterStorage.get(world);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static FellowshipWorldData dataOf(World world) {
        try {
            return world == null || world.isRemote ? null : FellowshipStorage.get(world);
        } catch (RuntimeException failure) {
            LostTalesLog.warning("The fellowships could not be read to set LOTR's right: %s",
                    failure.toString());
            return null;
        }
    }
}
