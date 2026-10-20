package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.fellowship.model.FellowshipIcon;
import com.ninuna.losttales.util.LostTalesLog;
import lotr.common.LOTRLevelData;
import lotr.common.fellowship.LOTRFellowship;
import lotr.common.fellowship.LOTRFellowshipData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LOTR's fellowship behind one of ours. LOTR's own fellowship carries out
 * the two rules on harm and shares waypoints and banners, so each of ours
 * keeps one in step with it. Its own *show map locations* stays off: it
 * would only show members who hide on LOTR's map. The fellowship is
 * changed through its own methods, never the player-data methods that send
 * LOTR's notices, so nobody is told anything in LOTR's words, and every
 * player's list of fellowships stays as LOTR keeps it.
 *
 * <p>An owner holding it for the record only, while nobody plays a member,
 * takes no part in its waypoints (FEL-02): LOTR's request to share into it
 * finds it no fellowship to share into
 * ({@link LostTalesLotrFellowshipRequestHook#shareInto}), and no copy is
 * handed to it ({@link LotrSharedWaypoints}). Which fellowships are so
 * held is known from the last shape each was set to.</p>
 *
 * <p>Nothing here fails the game: where LOTR cannot be reached the mirror
 * stands as it was, and that is said once.</p>
 */
public final class LotrFellowshipMirror {
    private static boolean warned;
    /**
     * LOTR's fellowships whose owner holds them for the record only, by id.
     * Read by LOTR's share request on the network thread; cleared with the
     * server's other state ({@link #clear}).
     */
    private static final Set<UUID> HELD_FOR_THE_RECORD =
            Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    private LotrFellowshipMirror() {}

    /** Forgets which fellowships are held for the record only, as the server starts or stops. */
    public static void clear() {
        HELD_FOR_THE_RECORD.clear();
    }

    /**
     * LOTR's fellowships, by id, each held by its owner for the record
     * only while nobody plays a member of ours: the owner is then the one
     * account in it, and takes no part in its waypoints (FEL-02). A view
     * that follows every change.
     */
    static Set<UUID> heldForTheRecordIds() {
        return Collections.unmodifiableSet(HELD_FOR_THE_RECORD);
    }

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
                // step lets it go.
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
            if (shape.ownerPlays) {
                HELD_FOR_THE_RECORD.remove(fellowship.getFellowshipID());
            } else {
                HELD_FOR_THE_RECORD.add(fellowship.getFellowshipID());
            }
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
            HELD_FOR_THE_RECORD.remove(mirrorId);
            return true;
        } catch (RuntimeException failure) {
            warn(failure);
            return false;
        } catch (LinkageError error) {
            warn(error);
            return false;
        }
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
