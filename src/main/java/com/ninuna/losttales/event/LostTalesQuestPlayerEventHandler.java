package com.ninuna.losttales.event;

import com.ninuna.losttales.quest.LostTalesQuestManager;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSyncManager;
import com.ninuna.losttales.compat.lotr.LostTalesLotrWaystoneTravelAdapter;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
/** Registers, copies, and syncs the Forge 1.7.10 player quest data container. */
public final class LostTalesQuestPlayerEventHandler {
    private static final int FELLOWSHIP_SYNC_INTERVAL_TICKS = 100;
    private final Map<UUID, Set<UUID>> fellowshipIdsByPlayer =
            new LinkedHashMap<UUID, Set<UUID>>();

    @SubscribeEvent
    public void onEntityConstructing(EntityEvent.EntityConstructing event) {
        if (event.entity instanceof EntityPlayer && event.entity.getExtendedProperties(LostTalesQuestPlayerData.PROPERTY_ID) == null) {
            event.entity.registerExtendedProperties(LostTalesQuestPlayerData.PROPERTY_ID, new LostTalesQuestPlayerData());
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        LostTalesQuestPlayerData oldData = LostTalesQuestPlayerData.get(event.original);
        LostTalesQuestPlayerData newData = LostTalesQuestPlayerData.get(event.entityPlayer);
        if (newData != null) {
            newData.copyFrom(oldData);
        }
        refreshAndSyncIfServerPlayer(event.entityPlayer);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        refreshAndSyncIfServerPlayer(event.player);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            this.fellowshipIdsByPlayer.remove(
                    event.player.getUniqueID());
            LostTalesLotrWaystoneTravelAdapter.clearPending(
                    (EntityPlayerMP)event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
        refreshAndSyncIfServerPlayer(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        refreshAndSyncIfServerPlayer(event.player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof EntityPlayerMP)
                || event.player.worldObj == null
                || event.player.worldObj.isRemote
                || event.player.ticksExisted
                        % FELLOWSHIP_SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP)event.player;
        Set<UUID> current = getFellowshipIds(player);
        Set<UUID> previous = this.fellowshipIdsByPlayer.put(
                player.getUniqueID(), current);
        if (previous != null && !previous.equals(current)) {
            LostTalesMapMarkerSyncManager.sync(player);
        }
    }

    private void refreshAndSyncIfServerPlayer(EntityPlayer player) {
        if (player instanceof EntityPlayerMP && player.worldObj != null && !player.worldObj.isRemote) {
            EntityPlayerMP serverPlayer = (EntityPlayerMP)player;
            LostTalesQuestManager.refreshPlayerState(serverPlayer);
            this.fellowshipIdsByPlayer.put(
                    serverPlayer.getUniqueID(),
                    getFellowshipIds(serverPlayer));
            LostTalesMapMarkerSyncManager.sync(serverPlayer);
        }
    }

    private static Set<UUID> getFellowshipIds(
            EntityPlayerMP player) {
        LOTRPlayerData data = LOTRLevelData.getData(player);
        if (data == null || data.getFellowshipIDs() == null
                || data.getFellowshipIDs().isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(
                new LinkedHashSet<UUID>(data.getFellowshipIDs()));
    }
}
