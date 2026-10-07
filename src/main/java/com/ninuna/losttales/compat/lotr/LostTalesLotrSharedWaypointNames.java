package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import lotr.common.world.map.LOTRCustomWaypoint;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import java.util.UUID;

/**
 * Who shared a waypoint, as its copies name them: the character the
 * sharing account plays, never the account, since others see the
 * character. Called, through the coremod, as LOTR's
 * {@code LOTRCustomWaypoint.setSharingPlayerID} ends, after LOTR has
 * written the account's name; an account playing no character keeps it.
 * Copies of a character nobody plays are named by
 * {@link LotrSharedWaypoints}. Only the server's copies are named here;
 * nothing here fails the game.
 */
public final class LostTalesLotrSharedWaypointNames {
    private static volatile boolean failureLogged;

    private LostTalesLotrSharedWaypointNames() {}

    /** Names the waypoint's sharer after the character their account plays. */
    public static void name(LOTRCustomWaypoint waypoint) {
        try {
            UUID sharer = waypoint == null ? null : waypoint.getSharingPlayerID();
            MinecraftServer server = MinecraftServer.getServer();
            if (sharer == null || server == null
                    || !FMLCommonHandler.instance().getEffectiveSide().isServer()) {
                return;
            }
            WorldServer overworld = server.worldServerForDimension(0);
            CharacterRoster roster = overworld == null ? null
                    : CharacterStorage.get(overworld).getRoster(sharer);
            RoleplayCharacter played = roster == null ? null : roster.getActiveCharacter();
            if (played != null && played.getName().trim().length() > 0) {
                waypoint.setSharingPlayerName(played.getName().trim());
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
        FMLLog.warning("[%s] A shared waypoint keeps its account's name: %s",
                LostTalesMetaData.MOD_ID, failure.toString());
    }
}
