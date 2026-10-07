package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.util.LostTalesLog;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fellowship.LOTRFellowship;
import lotr.common.fellowship.LOTRFellowshipData;
import lotr.common.world.map.LOTRCustomWaypoint;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A character's shared waypoints stay with its fellowships while it is not
 * played: a character not played is away, not gone.
 *
 * <p>LOTR builds the copies its members see from the waypoints of the
 * character an account plays now, so the copies of a character played by
 * nobody are handed out here, from the waypoints it saved as it was last
 * played, to every member online: named after the character, as LOTR's
 * own are ({@link LostTalesLotrSharedWaypointNames}). Waypoint numbers
 * are one count per account, so no two characters' copies are ever taken
 * for one.</p>
 *
 * <p>A character played again finds its waypoints loaded before its
 * account is back in LOTR's fellowship, and LOTR drops every share to a
 * fellowship the account is not in as it loads them; the shares it had
 * are shared again once the account is back ({@link #expectReshares}).</p>
 *
 * <p>Only copies this class handed out are ever taken back here. Static,
 * cleared with the server's other state.</p>
 */
public final class LotrSharedWaypoints {

    /** A character no account plays now, as one of a fellowship's members. */
    public static final class Away {
        final UUID ownerId;
        final String characterName;
        final NBTTagCompound waypoints;

        /** {@code waypoints} is the character's saved custom-waypoint state. */
        public Away(UUID ownerId, String characterName, NBTTagCompound waypoints) {
            this.ownerId = ownerId;
            this.characterName = characterName == null ? "" : characterName;
            this.waypoints = waypoints;
        }
    }

    /** One copy: whose waypoint, and its number. */
    private static final class Key {
        final UUID ownerId;
        final int id;

        Key(UUID ownerId, int id) {
            this.ownerId = ownerId;
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key && ((Key) other).ownerId.equals(this.ownerId)
                    && ((Key) other).id == this.id;
        }

        @Override
        public int hashCode() {
            return 31 * this.ownerId.hashCode() + this.id;
        }
    }

    /** What was handed out for one of LOTR's fellowships, and to whom. */
    private static final class Handed {
        final Set<UUID> recipients;
        final Set<Key> copies;

        Handed(Set<UUID> recipients, Set<Key> copies) {
            this.recipients = recipients;
            this.copies = copies;
        }
    }

    private static final Map<UUID, Handed> HANDED = new HashMap<UUID, Handed>();
    /** Per account: each waypoint's number and the fellowships it was shared with as it was loaded. */
    private static final Map<UUID, Map<Integer, Set<UUID>>> RESHARES =
            new HashMap<UUID, Map<Integer, Set<UUID>>>();

    private LotrSharedWaypoints() {}

    /** Forgets everything handed out and every share waiting, as the server starts or stops. */
    public static synchronized void clear() {
        HANDED.clear();
        RESHARES.clear();
    }

    /**
     * The shares of the waypoints just loaded for {@code ownerId}, to be
     * made again once the account is back in each fellowship.
     */
    static synchronized void expectReshares(UUID ownerId, Map<Integer, Set<UUID>> shares) {
        if (ownerId == null) {
            return;
        }
        if (shares == null || shares.isEmpty()) {
            RESHARES.remove(ownerId);
        } else {
            RESHARES.put(ownerId, new LinkedHashMap<Integer, Set<UUID>>(shares));
        }
    }

    /**
     * Brings the copies of LOTR's fellowship {@code mirrorId} up to date:
     * the shares of characters played again are made again, the members
     * online are handed the copies of every character in {@code away},
     * and copies handed out before that no longer stand are taken back.
     */
    public static synchronized void update(UUID mirrorId, List<Away> away) {
        if (mirrorId == null) {
            return;
        }
        try {
            LOTRFellowship fellowship = LOTRFellowshipData.getActiveFellowship(mirrorId);
            if (fellowship == null) {
                forget(mirrorId);
                return;
            }
            Set<UUID> recipients = new LinkedHashSet<UUID>();
            for (UUID account : fellowship.getAllPlayerUUIDs()) {
                if (LostTalesServerPlayers.findOnline(account) != null) {
                    recipients.add(account);
                }
            }
            for (UUID account : recipients) {
                reshare(account, fellowship);
            }
            Map<Key, LOTRCustomWaypoint> wanted = new LinkedHashMap<Key, LOTRCustomWaypoint>();
            Map<Key, String> names = new HashMap<Key, String>();
            for (Away character : away == null ? Collections.<Away>emptyList() : away) {
                if (character == null || character.ownerId == null) {
                    continue;
                }
                for (LOTRCustomWaypoint waypoint : LotrCustomWaypointStateAdapter
                        .sharedWith(character.waypoints, mirrorId)) {
                    Key key = new Key(character.ownerId, waypoint.getID());
                    // A waypoint its owner's account shares live is LOTR's own.
                    if (!sharedLive(key, mirrorId)) {
                        wanted.put(key, waypoint);
                        names.put(key, character.characterName);
                    }
                }
            }
            for (UUID account : recipients) {
                LOTRPlayerData data = LOTRLevelData.getData(account);
                for (Map.Entry<Key, LOTRCustomWaypoint> entry : wanted.entrySet()) {
                    if (!account.equals(entry.getKey().ownerId)) {
                        hand(data, entry.getKey(), entry.getValue(),
                                names.get(entry.getKey()), mirrorId);
                    }
                }
            }
            Handed before = HANDED.get(mirrorId);
            if (before != null) {
                for (Key key : before.copies) {
                    if (!wanted.containsKey(key) && !sharedLive(key, mirrorId)) {
                        takeBack(before.recipients, key);
                        takeBack(recipients, key);
                    }
                }
                Set<UUID> gone = new HashSet<UUID>(before.recipients);
                gone.removeAll(recipients);
                for (Key key : before.copies) {
                    takeBack(gone, key);
                }
            }
            HANDED.put(mirrorId, new Handed(recipients, new HashSet<Key>(wanted.keySet())));
        } catch (RuntimeException failure) {
            LostTalesLog.warning("A fellowship's shared waypoints could not be handed out: %s",
                    failure.toString());
        } catch (LinkageError incompatible) {
            LostTalesLog.warning("LOTR's shared waypoints could not be reached: %s",
                    incompatible.toString());
        }
    }

    /** Takes back every copy handed out for LOTR's fellowship {@code mirrorId}, which ended. */
    public static synchronized void forget(UUID mirrorId) {
        Handed handed = mirrorId == null ? null : HANDED.remove(mirrorId);
        if (handed == null) {
            return;
        }
        try {
            for (Key key : handed.copies) {
                if (!sharedLive(key, mirrorId)) {
                    takeBack(handed.recipients, key);
                }
            }
        } catch (RuntimeException failure) {
            LostTalesLog.warning("A fellowship's shared waypoints could not be taken back: %s",
                    failure.toString());
        } catch (LinkageError incompatible) {
            LostTalesLog.warning("LOTR's shared waypoints could not be reached: %s",
                    incompatible.toString());
        }
    }

    /** Shares again, with LOTR's own method, what {@code account}'s waypoints shared with this fellowship before they were loaded. */
    private static void reshare(UUID account, LOTRFellowship fellowship) {
        Map<Integer, Set<UUID>> waiting = RESHARES.get(account);
        if (waiting == null) {
            return;
        }
        UUID mirrorId = fellowship.getFellowshipID();
        LOTRPlayerData data = LOTRLevelData.getData(account);
        for (Map.Entry<Integer, Set<UUID>> entry : new ArrayList<Map.Entry<Integer, Set<UUID>>>(
                waiting.entrySet())) {
            if (!entry.getValue().remove(mirrorId)) {
                continue;
            }
            LOTRCustomWaypoint waypoint = data.getCustomWaypointByID(entry.getKey().intValue());
            if (waypoint != null && !waypoint.hasSharedFellowship(mirrorId)) {
                data.customWaypointAddSharedFellowship(waypoint, fellowship);
            }
            if (entry.getValue().isEmpty()) {
                waiting.remove(entry.getKey());
            }
        }
        if (waiting.isEmpty()) {
            RESHARES.remove(account);
        }
    }

    /** Hands one copy to one member, unless the copy they hold is the same already. */
    private static void hand(LOTRPlayerData data, Key key, LOTRCustomWaypoint waypoint,
                             String characterName, UUID mirrorId) {
        LOTRCustomWaypoint held = data.getSharedCustomWaypointByID(key.ownerId, key.id);
        if (held != null && held.getCodeName().equals(waypoint.getCodeName())
                && held.getXCoord() == waypoint.getXCoord()
                && held.getZCoord() == waypoint.getZCoord()
                && characterName.equals(held.getSharingPlayerName())) {
            return;
        }
        LOTRCustomWaypoint copy = new LOTRCustomWaypoint(waypoint.getCodeName(),
                waypoint.getX(), waypoint.getY(), waypoint.getXCoord(),
                waypoint.getYCoordSaved(), waypoint.getZCoord(), waypoint.getID());
        copy.setSharingPlayerID(key.ownerId);
        if (characterName.length() > 0) {
            copy.setSharingPlayerName(characterName);
        }
        List<UUID> fellowships = new ArrayList<UUID>();
        fellowships.add(mirrorId);
        copy.setSharedFellowshipIDs(fellowships);
        data.addOrUpdateSharedCustomWaypoint(copy);
    }

    /** Whether the owner's own waypoint of that number is shared with this fellowship now, LOTR's to look after. */
    private static boolean sharedLive(Key key, UUID mirrorId) {
        LOTRCustomWaypoint live = LOTRLevelData.getData(key.ownerId).getCustomWaypointByID(key.id);
        return live != null && live.hasSharedFellowship(mirrorId);
    }

    /**
     * Takes one copy back from each of these accounts, online or not: one
     * handed a copy and since logged out may still hold it, and LOTR would
     * send it again as they come back.
     */
    private static void takeBack(Set<UUID> accounts, Key key) {
        for (UUID account : accounts) {
            if (account.equals(key.ownerId)) {
                continue;
            }
            LOTRPlayerData data = LOTRLevelData.getData(account);
            LOTRCustomWaypoint held = data.getSharedCustomWaypointByID(key.ownerId, key.id);
            if (held != null) {
                data.removeSharedCustomWaypoint(held);
            }
        }
    }
}
