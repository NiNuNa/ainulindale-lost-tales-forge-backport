package com.ninuna.losttales.compat.lotr;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import com.ninuna.losttales.util.LostTalesLog;
import lotr.common.network.LOTRPacketUpdatePlayerLocations;

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

    private static void logOnce(Throwable failure) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        LostTalesLog.warning("Concealment failed on LOTR's map locations; "
                + "concealed players may show there: %s", failure.toString());
    }
}
