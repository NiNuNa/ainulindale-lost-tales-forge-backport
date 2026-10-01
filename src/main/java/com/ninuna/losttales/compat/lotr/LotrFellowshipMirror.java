package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.fellowship.model.FellowshipIcon;
import com.ninuna.losttales.util.LostTalesLog;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fellowship.LOTRFellowship;
import lotr.common.fellowship.LOTRFellowshipData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * LOTR's fellowship behind one of ours. LOTR's own fellowship carries out
 * the three rules, shows members on its map, and shares waypoints and
 * banners, so each of ours keeps one in step with it. Only the fellowship's
 * own methods are called, never LOTR's player-data ones, so nobody is told
 * anything in LOTR's words. Nothing here fails the game: where LOTR cannot
 * be reached the mirror stands as it was, and that is said once.
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
        final boolean shownOnMap;
        final UUID owner;
        final List<UUID> members;
        final Set<UUID> admins;

        /**
         * {@code owner} holds it; {@code members} are the other accounts in
         * it, {@code admins} those of them who are guides.
         */
        public Shape(String name, FellowshipIcon icon, boolean noFighting,
                     boolean noHiredHarm, boolean shownOnMap, UUID owner,
                     List<UUID> members, Set<UUID> admins) {
            if (name == null || owner == null) {
                throw new IllegalArgumentException("a mirror needs a name and an owner");
            }
            this.name = name;
            this.icon = icon;
            this.noFighting = noFighting;
            this.noHiredHarm = noHiredHarm;
            this.shownOnMap = shownOnMap;
            this.owner = owner;
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
            if (fellowship.getShowMapLocations() != shape.shownOnMap) {
                fellowship.setShowMapLocations(shape.shownOnMap);
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
            return true;
        } catch (RuntimeException failure) {
            warn(failure);
            return false;
        } catch (LinkageError error) {
            warn(error);
            return false;
        }
    }

    /** The LOTR fellowship a player's {@code /fmsg} is bound to; null for none. */
    public static UUID boundTo(EntityPlayer player) {
        try {
            return LOTRLevelData.getData(player).getChatBoundFellowshipID();
        } catch (RuntimeException failure) {
            warn(failure);
            return null;
        } catch (LinkageError error) {
            warn(error);
            return null;
        }
    }

    /** Binds a player's plain {@code /fmsg} to a LOTR fellowship, or unbinds it with null. */
    public static void bind(EntityPlayer player, UUID mirrorId) {
        try {
            LOTRPlayerData data = LOTRLevelData.getData(player);
            data.setChatBoundFellowshipID(mirrorId);
        } catch (RuntimeException failure) {
            warn(failure);
        } catch (LinkageError error) {
            warn(error);
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
