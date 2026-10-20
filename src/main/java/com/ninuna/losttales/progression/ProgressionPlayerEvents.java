package com.ninuna.losttales.progression;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Keeps each player's progression attached, carried across death
 * (PROG-10) and sent to its client. Registered on both buses.
 */
public final class ProgressionPlayerEvents {

    @SubscribeEvent
    public void onEntityConstructing(EntityEvent.EntityConstructing event) {
        if (event.entity instanceof EntityPlayer
                && event.entity.getExtendedProperties(ProgressionPlayerData.PROPERTY_ID) == null) {
            event.entity.registerExtendedProperties(ProgressionPlayerData.PROPERTY_ID,
                    new ProgressionPlayerData());
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        ProgressionPlayerData oldData = ProgressionPlayerData.get(event.original);
        ProgressionPlayerData newData = ProgressionPlayerData.get(event.entityPlayer);
        if (newData != null) {
            newData.copyFrom(oldData);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            ProgressionSync.sendCatalog((EntityPlayerMP) event.player);
            markDirty(event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
        markDirty(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        markDirty(event.player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP
                && event.player.worldObj != null && !event.player.worldObj.isRemote) {
            ProgressionService.tick((EntityPlayerMP) event.player);
        }
    }

    private static void markDirty(EntityPlayer player) {
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        if (data != null) {
            data.markDirty();
        }
    }
}
