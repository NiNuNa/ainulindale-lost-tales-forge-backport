package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import lotr.common.LOTRBannerProtection;
import lotr.common.block.LOTRBlockBarrel;
import lotr.common.block.LOTRBlockKebabStand;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * Asks LOTR's banner protection whether a player may open a container, with
 * the permission LOTR's own right-click check asks for that block: FOOD for a
 * barrel or a kebab stand, PERSONAL_CONTAINERS for an ender chest, CONTAINERS
 * for anything else. When LOTR cannot answer, the container stays closed.
 */
public final class LotrContainerProtection {
    private static boolean warnedUnavailable;

    private LotrContainerProtection() {}

    public static boolean mayOpen(EntityPlayer player, World world, int x, int y, int z) {
        if (player == null || world == null || world.isRemote) {
            return false;
        }
        try {
            return !LOTRBannerProtection.isProtected(
                    world, x, y, z,
                    LOTRBannerProtection.forPlayer(player, permissionFor(world.getBlock(x, y, z))),
                    false);
        } catch (LinkageError error) {
            warnUnavailable(error);
            return false;
        } catch (RuntimeException exception) {
            warnUnavailable(exception);
            return false;
        }
    }

    private static LOTRBannerProtection.Permission permissionFor(Block block) {
        if (block instanceof LOTRBlockBarrel || block instanceof LOTRBlockKebabStand) {
            return LOTRBannerProtection.Permission.FOOD;
        }
        if (block instanceof BlockEnderChest) {
            return LOTRBannerProtection.Permission.PERSONAL_CONTAINERS;
        }
        return LOTRBannerProtection.Permission.CONTAINERS;
    }

    private static synchronized void warnUnavailable(Throwable throwable) {
        if (warnedUnavailable) {
            return;
        }
        warnedUnavailable = true;
        FMLLog.warning(
                "[%s] LOTR banner protection could not be asked; quick loot stays closed: %s",
                LostTalesMetaData.MOD_ID, throwable.getClass().getSimpleName());
    }
}
