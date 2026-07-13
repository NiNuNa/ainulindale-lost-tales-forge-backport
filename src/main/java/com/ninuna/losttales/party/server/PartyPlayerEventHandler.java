package com.ninuna.losttales.party.server;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

/** Loads, repairs, and periodically expires server-owned party state. */
public final class PartyPlayerEventHandler {

    private static final int INVITATION_CLEANUP_INTERVAL_TICKS = 200;

    private int cleanupTicks;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        ensureIntegrity(event == null ? null : event.player);
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
        PartyMemberStatusSyncManager.tick();
        PartyTrackingSyncManager.tick();
        this.cleanupTicks++;
        if (this.cleanupTicks < INVITATION_CLEANUP_INTERVAL_TICKS) {
            return;
        }
        this.cleanupTicks = 0;

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        WorldServer overworld = server.worldServerForDimension(0);
        if (overworld != null) {
            PartySyncManager.AudienceSnapshot affectedAudience =
                    PartySyncManager.captureAllInvitationAudience(overworld);
            int removed = PartyService.getInstance()
                    .pruneInvalidInvitations(overworld);
            if (removed > 0) {
                PartySyncManager.sendStateToAudience(affectedAudience, null);
            }
        }
    }

    private void ensureIntegrity(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)
                || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        if (!PartyService.getInstance().ensureIntegrity(player.worldObj)) {
            FMLLog.warning("[%s] Party reference validation was deferred for player %s because a data store is read-only or unavailable",
                    LostTalesMetaData.MOD_ID, player.getUniqueID());
        }
        PartySyncManager.sendState(
                (EntityPlayerMP) player,
                PartySyncManager.UNSOLICITED_REQUEST_ID);
    }
}
