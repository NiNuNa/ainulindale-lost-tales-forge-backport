package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
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
 * waystones and markers. A waystone belongs to the character that placed
 * it (WAY-08): only while a player plays that character may they edit or
 * break it, and the account's other characters are anyone else. Who may
 * manage every marker is {@link LostTalesPermissions}' answer for
 * {@code mapmarker.manage}, the one capability for every waystone power
 * (WAY-13): an operator by level, or a role the server's config grants it
 * to.
 */
public final class LostTalesWaystonePermissionPolicy {
    private static boolean warnedIncompatibleBannerApi;

    private LostTalesWaystonePermissionPolicy() {}

    /**
     * Whether the player manages markers: sees, edits, breaks, makes
     * public and hands over any waystone.
     */
    public static boolean managesMarkers(EntityPlayerMP player) {
        return LostTalesPermissions.has(player, LostTalesCapability.MAPMARKER_MANAGE);
    }

    /** Whether {@code characterId} owns the record, or a manager asks. */
    public static boolean isOwnerOrManager(
            LostTalesMapMarkerRecord record,
            UUID characterId, boolean manages) {
        if (manages) {
            return true;
        }
        return record != null && characterId != null
                && characterId.equals(record.getOwnerCharacterId());
    }

    public static boolean canBreakOrEdit(
            EntityPlayerMP player, LostTalesMapMarkerRecord record,
            World world, int x, int y, int z, boolean warnPlayer) {
        boolean manages = managesMarkers(player);
        RoleplayCharacter played = manages || player == null ? null
                : CharacterActiveResolver.get(player);
        if (!isOwnerOrManager(record,
                played == null ? null : played.getCharacterId(), manages)) {
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

    /** Turning a waystone public, and handing one over, are a manager's alone. */
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
