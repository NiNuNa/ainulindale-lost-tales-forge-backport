package com.ninuna.losttales.network.server;

import com.ninuna.losttales.fellowship.server.FellowshipMemberStatusSyncManager;
import com.ninuna.losttales.fellowship.server.FellowshipSyncManager;
import com.ninuna.losttales.fellowship.server.FellowshipTrackingSyncManager;
import com.ninuna.losttales.quest.missive.MissiveBoardWatches;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;

/** Clears queued requests and rate windows when a server player disconnects. */
public final class LostTalesNetworkPlayerEventHandler {

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event == null || event.player == null || event.player.getUniqueID() == null) {
            return;
        }
        LostTalesServerTaskQueue.cancelPlayer(event.player.getUniqueID());
        LostTalesRequestRateLimiter.clearPlayer(event.player.getUniqueID());
        LostTalesThirdPersonAimService.clearPlayer(
                event.player.getUniqueID());
        FellowshipSyncManager.clearPlayer(event.player.getUniqueID());
        FellowshipMemberStatusSyncManager.clearPlayer(event.player.getUniqueID());
        FellowshipTrackingSyncManager.clearPlayer(event.player.getUniqueID());
        MissiveBoardWatches.forget(event.player.getUniqueID());
    }
}
