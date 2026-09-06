package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
import cpw.mods.fml.common.FMLLog;
import java.util.UUID;
import lotr.common.LOTRBannerProtection;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

/**
 * Central server-side ownership and LOTR banner-protection policy for
 * waystones and markers. Who may manage them is
 * {@link LostTalesPermissions}' answer, asked here so the marker code
 * has one word for it: an operator by level, or a role the server's
 * config grants the capability to.
 */
public final class LostTalesWaystonePermissionPolicy {
    private static boolean warnedIncompatibleBannerApi;

    private LostTalesWaystonePermissionPolicy() {}

    /** Whether the player may manage the markers everyone sees. */
    public static boolean managesMarkers(EntityPlayerMP player) {
        return LostTalesPermissions.has(player, LostTalesCapability.MAPMARKER_MANAGE);
    }

    /** Whether the player may place a public waystone and change its settings. */
    public static boolean managesWaystones(EntityPlayerMP player) {
        return LostTalesPermissions.has(player, LostTalesCapability.WAYSTONE_MANAGE);
    }

    public static boolean isOwnerOrOperator(
            LostTalesMapMarkerRecord record,
            UUID playerId, boolean manages) {
        if (manages) {
            return true;
        }
        return record != null
                && record.getOwnerPlayerId() != null
                && record.getOwnerPlayerId().equals(playerId);
    }

    public static boolean canBreakOrEdit(
            EntityPlayerMP player, LostTalesMapMarkerRecord record,
            World world, int x, int y, int z, boolean warnPlayer) {
        boolean manages = managesMarkers(player);
        if (!isOwnerOrOperator(record,
                player == null ? null : player.getUniqueID(), manages)) {
            return false;
        }
        if (manages) {
            return true;
        }
        if (world == null || world.isRemote) {
            return false;
        }
        try {
            return !LOTRBannerProtection.isProtected(
                    world, x, y, z,
                    LOTRBannerProtection.forPlayer(
                            player,
                            LOTRBannerProtection.Permission.FULL),
                    warnPlayer);
        } catch (LinkageError error) {
            warnBannerApi(error);
            return false;
        } catch (RuntimeException exception) {
            warnBannerApi(exception);
            return false;
        }
    }

    public static boolean canMakePublic(EntityPlayerMP player) {
        return managesMarkers(player);
    }

    /** Automated preset generation never overwrites a protected banner area. */
    public static boolean isProtectedFromWorldGeneration(
            World world, int x, int y, int z) {
        if (world == null || world.isRemote) {
            return true;
        }
        try {
            return LOTRBannerProtection.isProtected(
                    world, x, y, z,
                    LOTRBannerProtection.anyBanner(), false);
        } catch (LinkageError error) {
            warnBannerApi(error);
            return true;
        } catch (RuntimeException exception) {
            warnBannerApi(exception);
            return true;
        }
    }

    private static synchronized void warnBannerApi(Throwable throwable) {
        if (warnedIncompatibleBannerApi) {
            return;
        }
        warnedIncompatibleBannerApi = true;
        try {
            FMLLog.warning(
                    "[%s] LOTR banner protection is unavailable; non-op waystone mutations will fail closed: %s",
                    LostTalesMetaData.MOD_ID,
                    throwable == null
                            ? "unknown error"
                            : throwable.getClass().getSimpleName());
        } catch (RuntimeException ignored) {}
    }
}
