package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

/**
 * The fellowships' server events. A login, a change of world and a respawn
 * check the stores and send the player their state; a login and a logout
 * are noted, so the members are told. Each server tick drives the health
 * and position updates, sets LOTR's fellowships right once after the
 * server starts, and tells the members who came or went. Every 200 ticks
 * the cleanup runs.
 */
public final class FellowshipPlayerEventHandler {

    private static final int INVITATION_CLEANUP_INTERVAL_TICKS = 200;

    private int cleanupTicks;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        ensureIntegrity(event == null ? null : event.player);
        if (event != null && event.player != null) {
            FellowshipSyncManager.presenceChanged(event.player.getUniqueID());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event != null && event.player instanceof EntityPlayerMP) {
            FellowshipSyncManager.presenceChanged(event.player.getUniqueID());
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
        ensureIntegrity(event == null ? null : event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        ensureIntegrity(event == null ? null : event.player);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        FellowshipMemberStatusSyncManager.tick();
        FellowshipTrackingSyncManager.tick();
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer overworld = server == null ? null : server.worldServerForDimension(0);
        if (overworld != null) {
            FellowshipMirrors.settleOnce(overworld);
            FellowshipSyncManager.sendPresenceChanges(overworld);
        }
        this.cleanupTicks++;
        if (this.cleanupTicks < INVITATION_CLEANUP_INTERVAL_TICKS) {
            return;
        }
        this.cleanupTicks = 0;
        if (overworld != null) {
            FellowshipSyncManager.AudienceSnapshot affectedAudience =
                    FellowshipSyncManager.captureAllInvitationAudience(overworld);
            FellowshipIntegrityReport removed = FellowshipService.getInstance()
                    .pruneStale(overworld);
            // A mark is seen by members of several fellowships, so a mark
            // taken away sends everyone their state.
            if (removed != null && removed.getMarks() > 0) {
                FellowshipSyncManager.sendStateToEveryone();
            } else if (removed != null && removed.getInvitations() > 0) {
                FellowshipSyncManager.sendStateToAudience(affectedAudience, null);
            }
        }
    }

    private void ensureIntegrity(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)
                || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        if (!FellowshipService.getInstance().ensureIntegrity(player.worldObj)) {
            FMLLog.warning("[%s] Fellowship reference validation was deferred for player %s because a data store is read-only or unavailable",
                    LostTalesMetaData.MOD_ID, player.getUniqueID());
        }
        FellowshipSyncManager.sendState(
                (EntityPlayerMP) player,
                FellowshipSyncManager.UNSOLICITED_REQUEST_ID);
    }
}
