package com.ninuna.losttales.compat.lotr;

import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.entity.npc.LOTREntityNPCRideable;
import lotr.common.entity.projectile.LOTREntityProjectileBase;
import lotr.common.inventory.LOTRContainerBarrel;
import lotr.common.tileentity.LOTRTileEntityBarrel;
import lotr.common.world.map.LOTRWaypoint;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

/**
 * What the progression reads of LOTR's own things: a projectile's item, a
 * mount being tamed, a keg being filled and the travel regions a player
 * has opened (PROG-01).
 */
public final class LotrCountHooks {
    /** A keg's state, as LOTR keeps it. */
    public static final int KEG_EMPTY = LOTRTileEntityBarrel.EMPTY;
    public static final int KEG_BREWING = LOTRTileEntityBarrel.BREWING;

    private LotrCountHooks() {}

    /** The item a LOTR projectile (a spear, a bolt, an axe) carries; null for any other. */
    public static ItemStack projectileItem(Entity projectile) {
        return projectile instanceof LOTREntityProjectileBase
                ? ((LOTREntityProjectileBase) projectile).getProjectileItem() : null;
    }

    /** Whether the entity is a mount: a horse of any kind, or one of LOTR's ridden beasts. */
    public static boolean isMount(Entity entity) {
        return entity instanceof EntityHorse || entity instanceof LOTREntityNPCRideable;
    }

    /** Whether a mount is tamed: a horse's own taming, or a LOTR beast's. */
    public static boolean isTamed(Entity mount) {
        if (mount instanceof EntityHorse) {
            return ((EntityHorse) mount).isTame();
        }
        return mount instanceof LOTREntityNPCRideable
                && ((LOTREntityNPCRideable) mount).isNPCTamed();
    }

    /** The state of the keg a player has open; -1 for none. */
    public static int kegMode(Container container) {
        LOTRTileEntityBarrel keg = openKeg(container);
        return keg == null ? -1 : keg.barrelMode;
    }

    /** The mugs the keg a player has open holds or brews; null for none. */
    public static ItemStack kegDrink(Container container) {
        LOTRTileEntityBarrel keg = openKeg(container);
        return keg == null ? null : keg.getStackInSlot(LOTRTileEntityBarrel.BARREL_SLOT);
    }

    private static LOTRTileEntityBarrel openKeg(Container container) {
        return container instanceof LOTRContainerBarrel
                ? ((LOTRContainerBarrel) container).theBarrel : null;
    }

    /** The fast-travel regions the player's played character has opened, by name. */
    public static Set<String> openedRegions(EntityPlayer player) {
        Set<String> opened = new HashSet<String>();
        LOTRPlayerData data = LOTRLevelData.getData(player);
        if (data == null) {
            return opened;
        }
        for (LOTRWaypoint.Region region : LOTRWaypoint.Region.values()) {
            if (data.isFTRegionUnlocked(region)) {
                opened.add(region.name());
            }
        }
        return opened;
    }
}
