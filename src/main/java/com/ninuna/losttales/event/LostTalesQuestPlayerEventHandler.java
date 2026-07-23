package com.ninuna.losttales.event;

import com.ninuna.losttales.quest.LostTalesQuestManager;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSyncManager;
import com.ninuna.losttales.compat.lotr.LostTalesLotrWaystoneTravelAdapter;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
/** Registers, copies, and syncs the Forge 1.7.10 player quest data container. */
public final class LostTalesQuestPlayerEventHandler {

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

    private void refreshAndSyncIfServerPlayer(EntityPlayer player) {
        if (player instanceof EntityPlayerMP && player.worldObj != null && !player.worldObj.isRemote) {
            LostTalesQuestManager.refreshPlayerState((EntityPlayerMP) player);
            LostTalesMapMarkerSyncManager.sync((EntityPlayerMP)player);
        }
    }
}
