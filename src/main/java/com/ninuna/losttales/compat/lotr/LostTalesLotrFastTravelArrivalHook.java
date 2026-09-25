package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesFastTravelArrivalPacket;
import com.ninuna.losttales.world.map.waypoint.LostTalesWaypointFastTravelPolicy;
import cpw.mods.fml.common.FMLLog;
import lotr.common.LOTRPlayerData;
import lotr.common.world.map.LOTRAbstractWaypoint;
import net.minecraft.entity.player.EntityPlayerMP;
import com.ninuna.losttales.util.LostTalesServerPlayers;

/**
 * Called by the coremod right after {@code LOTRPlayerData.fastTravelTo}
 * inside {@code receiveFTBouncePacket}, LOTR's own completion step: the
 * client has bounced the countdown back and the teleport has just run.
 * The destination is resolved to the marker it stands for and the player
 * is told they arrived, which feeds the chat marker picker's "recently
 * travelled to" list. Purely informational; any failure is logged once
 * and never touches the teleport.
 *
 * <p>Assumes LOTR v36.15's bounce-packet completion path. If LOTR moves
 * the teleport elsewhere the transformer logs a warning and arrivals are
 * simply not reported.</p>
 */
public final class LostTalesLotrFastTravelArrivalHook {
    private static volatile boolean failureLogged;

    private LostTalesLotrFastTravelArrivalHook() {}

    public static void onArrived(LOTRPlayerData data,
                                 LOTRAbstractWaypoint waypoint) {
        try {
            if (data == null || waypoint == null) {
                return;
            }
            EntityPlayerMP player = LostTalesServerPlayers.findOnline(data.getPlayerUUID());
            if (player == null || player.worldObj == null
                    || player.worldObj.isRemote) {
                return;
            }
            String markerId = LostTalesWaypointFastTravelPolicy
                    .resolveMarkerId(player, waypoint);
            if (markerId.length() == 0 || markerId.length()
                    > LostTalesFastTravelArrivalPacket.MAX_MARKER_ID_BYTES) {
                return;
            }
            LostTalesNetworkHandler.CHANNEL.sendTo(
                    new LostTalesFastTravelArrivalPacket(markerId), player);
        } catch (Throwable throwable) {
            if (!failureLogged) {
                failureLogged = true;
                FMLLog.warning("[LostTales] Could not report a fast-travel "
                        + "arrival: %s", throwable);
            }
        }
    }

}
