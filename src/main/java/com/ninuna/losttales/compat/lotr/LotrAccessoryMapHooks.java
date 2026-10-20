package com.ninuna.losttales.compat.lotr;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import com.ninuna.losttales.util.LostTalesLog;
import lotr.common.LOTRLevelData;
import lotr.common.network.LOTRPacketUpdatePlayerLocations;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Keeps concealed players out of LOTR's server-authored map-location
 * packet. The coremod calls {@link #addPlayerLocationIfVisible} in place
 * of LOTR's own {@code addPlayerLocation}. Nothing here fails the game: a
 * failure is logged once, and a player whose concealment cannot be read
 * is shown, as LOTR would show them.
 */
public final class LotrAccessoryMapHooks {
    private static volatile boolean failureLogged;

    private LotrAccessoryMapHooks() {}

    public static void addPlayerLocationIfVisible(
            LOTRPacketUpdatePlayerLocations packet,
            GameProfile profile, double x, double z) {
        if (packet != null && profile != null && !hidden(profile)) {
            packet.addPlayerLocation(profile, x, z);
        }
    }

    /** Whether the profile names no player or a concealed one. */
    private static boolean hidden(GameProfile profile) {
        try {
            UUID id = profile.getId();
            return id == null || AccessoryEffectService.isConcealed(id);
        } catch (RuntimeException failure) {
            logOnce(failure);
        } catch (LinkageError incompatible) {
            logOnce(incompatible);
        }
        return false;
    }

    /** Sends every player in the world LOTR's map locations again. */
    public static void refreshPlayerLocations(World world) {
        if (world == null || world.isRemote || world.playerEntities == null) {
            return;
        }
        try {
            List<?> recipients = new ArrayList<Object>(world.playerEntities);
            for (Object value : recipients) {
                if (value instanceof EntityPlayer) {
                    LOTRLevelData.sendPlayerLocationsToPlayer(
                            (EntityPlayer)value, world);
                }
            }
        } catch (RuntimeException failure) {
            logOnce(failure);
        } catch (LinkageError incompatible) {
            logOnce(incompatible);
        }
    }

    private static void logOnce(Throwable failure) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        LostTalesLog.warning("Concealment failed on LOTR's map locations; "
                + "concealed players may show there: %s", failure.toString());
    }
}
