package com.ninuna.losttales.network.server;

import com.ninuna.losttales.party.server.PartyMemberStatusSyncManager;
import com.ninuna.losttales.party.server.PartySyncManager;
import com.ninuna.losttales.party.server.PartyTrackingSyncManager;

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
        PartySyncManager.clearPlayer(event.player.getUniqueID());
        PartyMemberStatusSyncManager.clearPlayer(event.player.getUniqueID());
        PartyTrackingSyncManager.clearPlayer(event.player.getUniqueID());
    }
}
