package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.fellowship.model.FellowshipIcon;
import com.ninuna.losttales.util.LostTalesLog;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fellowship.LOTRFellowship;
import lotr.common.fellowship.LOTRFellowshipData;
import lotr.common.network.LOTRPacketFellowshipRemove;
import lotr.common.network.LOTRPacketHandler;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRCustomWaypoint;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * LOTR's fellowship behind one of ours. LOTR's own fellowship carries out
 * the two rules on harm and shares waypoints and banners, so each of ours
 * keeps one in step with it. Its own *show map locations* stays off: it
 * would only show members who hide on LOTR's map. The fellowship is
 * changed through its own methods; of a player's LOTR data only the list of
 * fellowships and the waypoints shared through them are touched, never the
 * methods that send LOTR's notices, so nobody is told anything in LOTR's
 * words. An owner holding it for the record only stays off its own list
 * (FEL-02).
 * Nothing here fails the game: where LOTR cannot be reached the mirror
 * stands as it was, and that is said once.
 */
public final class LotrFellowshipMirror {
    private static boolean warned;

    private LotrFellowshipMirror() {}

    /** What LOTR's fellowship should hold. */
    public static final class Shape {
        final String name;
        final FellowshipIcon icon;
        final boolean noFighting;
        final boolean noHiredHarm;
        final UUID owner;
        final boolean ownerPlays;
        final List<UUID> members;
        final Set<UUID> admins;

        /**
         * {@code owner} holds it, and plays one of our fellowship's members
         * when {@code ownerPlays}; else it holds it for the record only.
         * {@code members} are the other accounts in it, {@code admins} those
         * of them who are guides.
         */
        public Shape(String name, FellowshipIcon icon, boolean noFighting,
                     boolean noHiredHarm, UUID owner, boolean ownerPlays,
                     List<UUID> members, Set<UUID> admins) {
            if (name == null || owner == null) {
                throw new IllegalArgumentException("a mirror needs a name and an owner");
            }
            this.name = name;
            this.icon = icon;
            this.noFighting = noFighting;
            this.noHiredHarm = noHiredHarm;
            this.owner = owner;
            this.ownerPlays = ownerPlays;
            List<UUID> others = new ArrayList<UUID>();
            if (members != null) {
                for (UUID member : members) {
                    if (member != null && !member.equals(owner) && !others.contains(member)) {
                        others.add(member);
                    }
                }
            }
            this.members = Collections.unmodifiableList(others);
            Set<UUID> guides = new LinkedHashSet<UUID>();
            if (admins != null) {
                for (UUID admin : admins) {
                    if (others.contains(admin)) {
                        guides.add(admin);
                    }
                }
            }
            this.admins = Collections.unmodifiableSet(guides);
        }

        public UUID owner() {
            return this.owner;
        }

        public boolean ownerPlays() {
            return this.ownerPlays;
        }

        public List<UUID> members() {
            return this.members;
        }

        public Set<UUID> admins() {
            return this.admins;
        }
    }

    /** Whether LOTR has read its fellowships and players for this world, so a mirror may be touched. */
    public static boolean isReady() {
        try {
            return !LOTRFellowshipData.needsLoad && !LOTRLevelData.needsLoad;
        } catch (LinkageError error) {
            warn(error);
            return false;
        }
    }

    /**
     * Makes LOTR's fellowship hold the shape, making a new one where
     * {@code mirrorId} names none, or one that ended. Its id; null while
     * LOTR cannot be reached.
     */
    public static UUID apply(UUID mirrorId, Shape shape) {
        try {
            LOTRFellowship fellowship = mirrorId == null ? null
                    : LOTRFellowshipData.getActiveFellowship(mirrorId);
            if (fellowship == null) {
                fellowship = new LOTRFellowship(shape.owner, shape.name);
                fellowship.createAndRegister();
            }
            if (!shape.owner.equals(fellowship.getOwner())) {
                // The account that held it stays a member until the next
                // step lets it go, which also takes it off its list.
                fellowship.setOwner(shape.owner);
            }
            for (UUID member : new ArrayList<UUID>(fellowship.getMemberUUIDs())) {
                if (!shape.members.contains(member)) {
                    fellowship.removeMember(member);
                }
            }
            for (UUID member : shape.members) {
                if (!fellowship.hasMember(member)) {
                    fellowship.addMember(member);
                }
            }
            for (UUID member : new ArrayList<UUID>(fellowship.getMemberUUIDs())) {
                boolean admin = shape.admins.contains(member);
                if (fellowship.isAdmin(member) != admin) {
                    fellowship.setAdmin(member, admin);
                }
            }
            if (!shape.name.equals(fellowship.getName())) {
                fellowship.setName(shape.name);
            }
            if (!isIcon(fellowship.getIcon(), shape.icon)) {
                fellowship.setIcon(stackOf(shape.icon));
            }
            if (fellowship.getPreventPVP() != shape.noFighting) {
                fellowship.setPreventPVP(shape.noFighting);
            }
            if (fellowship.getPreventHiredFriendlyFire() != shape.noHiredHarm) {
                fellowship.setPreventHiredFriendlyFire(shape.noHiredHarm);
            }
            if (fellowship.getShowMapLocations()) {
                fellowship.setShowMapLocations(false);
            }
            keepOnOwnersList(fellowship, shape.ownerPlays);
            return fellowship.getFellowshipID();
        } catch (RuntimeException failure) {
            warn(failure);
            return null;
        } catch (LinkageError error) {
            warn(error);
            return null;
        }
    }

    /**
     * Ends LOTR's fellowship behind one of ours that ended; true once it
     * has, or was gone already.
     */
    public static boolean end(UUID mirrorId) {
        try {
            LOTRFellowship fellowship = mirrorId == null ? null
                    : LOTRFellowshipData.getActiveFellowship(mirrorId);
            if (fellowship != null) {
                UUID owner = fellowship.getOwner();
                fellowship.setDisbandedAndRemoveAllMembers();
                LOTRLevelData.getData(owner).removeFellowship(fellowship);
            }
            return true;
        } catch (RuntimeException failure) {
            warn(failure);
            return false;
        } catch (LinkageError error) {
            warn(error);
            return false;
        }
    }

    /**
     * Keeps the fellowship on its owner's own list in LOTR's data while the
     * owner plays a member, and off it while the owner holds it for the
     * record only (FEL-02). From that list LOTR tells the player's game its
     * fellowships, pulls in the copies of their shared waypoints, and finds
     * the fellowship a waypoint is shared into by name, so an owner off it
     * gets neither copies nor a row to share into it. As it goes off the
     * list, the owner's own waypoints shared into it are taken out, and so
     * are the copies only it brings. The owner stays its owner. A failure
     * here is said once and leaves the rest of the mirror standing.
     */
    private static void keepOnOwnersList(LOTRFellowship fellowship, boolean ownerPlays) {
        try {
            LOTRPlayerData owner = LOTRLevelData.getData(fellowship.getOwner());
            if (ownerPlays) {
                // LOTR's own step, which does nothing while the fellowship is listed.
                owner.addFellowship(fellowship);
            } else {
                leaveOwnersList(fellowship, owner);
            }
        } catch (RuntimeException failure) {
            warn(failure);
        } catch (LinkageError error) {
            warn(error);
        }
    }

    private static void leaveOwnersList(LOTRFellowship fellowship, LOTRPlayerData owner) {
        UUID fellowshipId = fellowship.getFellowshipID();
        if (owner.getFellowshipIDs().remove(fellowshipId)) {
            owner.markDirty();
            EntityPlayerMP online = LostTalesServerPlayers.findOnline(fellowship.getOwner());
            if (online != null) {
                LOTRPacketHandler.networkWrapper.sendTo(
                        new LOTRPacketFellowshipRemove(fellowship, false), online);
            }
        }
        for (LOTRCustomWaypoint waypoint
                : new ArrayList<LOTRCustomWaypoint>(owner.getCustomWaypoints())) {
            if (waypoint.hasSharedFellowship(fellowshipId)) {
                owner.customWaypointRemoveSharedFellowship(waypoint, fellowship);
            }
        }
        for (LOTRAbstractWaypoint waypoint : owner.getAllAvailableWaypoints()) {
            if (waypoint instanceof LOTRCustomWaypoint
                    && onlyThrough((LOTRCustomWaypoint)waypoint, fellowshipId,
                            owner.getFellowshipIDs())) {
                owner.removeSharedCustomWaypoint((LOTRCustomWaypoint)waypoint);
            }
        }
    }

    /**
     * Whether a waypoint a player holds is a copy shared into this
     * fellowship and into none of {@code listed}, the fellowships still on
     * the player's own list. The player's own waypoints are no copies.
     */
    static boolean onlyThrough(LOTRCustomWaypoint waypoint, UUID fellowshipId,
                               Collection<UUID> listed) {
        if (!waypoint.isShared() || !waypoint.hasSharedFellowship(fellowshipId)) {
            return false;
        }
        for (UUID other : waypoint.getSharedFellowshipIDs()) {
            if (!other.equals(fellowshipId) && listed.contains(other)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIcon(ItemStack stack, FellowshipIcon icon) {
        if (stack == null || stack.getItem() == null) {
            return icon == null;
        }
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        return icon != null && icon.getItemName().equals(String.valueOf(name))
                && icon.getDamage() == stack.getItemDamage();
    }

    private static ItemStack stackOf(FellowshipIcon icon) {
        Object item = icon == null ? null : Item.itemRegistry.getObject(icon.getItemName());
        return item instanceof Item ? new ItemStack((Item)item, 1, icon.getDamage()) : null;
    }

    private static void warn(Throwable failure) {
        if (!warned) {
            warned = true;
            LostTalesLog.warning("LOTR's fellowship behind ours could not be kept in step: %s",
                    failure.toString());
        }
    }
}
