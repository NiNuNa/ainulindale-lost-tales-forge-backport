package com.ninuna.losttales.accessory.player;

import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Registers live accessory data during Forge player construction. */
public final class AccessoryPlayerEventHandler {

    @SubscribeEvent
    public void onEntityConstructing(EntityEvent.EntityConstructing event) {
        if (event.entity instanceof EntityPlayer
                && AccessoryPlayerData.get((EntityPlayer)event.entity) == null) {
            event.entity.registerExtendedProperties(
                    AccessoryPlayerData.PROPERTY_ID,
                    new AccessoryPlayerData());
        }
    }

    /** Forge replaces the player entity on death; copy only the post-death state. */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event == null || event.entityPlayer == null
                || event.original == null) {
            return;
        }
        AccessoryPlayerData target = AccessoryPlayerData.getOrCreate(
                event.entityPlayer);
        AccessoryPlayerData source = AccessoryPlayerData.get(event.original);
        if (target != null && source != null) {
            target.copyFrom(source);
        }
    }

    /** Retry quarantined-item recovery when normal inventory space becomes free. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END
                || event.player == null || event.player.worldObj == null
                || event.player.worldObj.isRemote) {
            return;
        }
        AccessoryEffectService.reconcile(event.player);
        if (event.player.ticksExisted % 20 == 0
                && AccessoryRecoveryService.recover(event.player)) {
            AccessoryInventorySyncManager.send(event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (event != null && event.player != null) {
            AccessoryEffectService.reconcile(event.player);
            if (event.player instanceof net.minecraft.entity.player.EntityPlayerMP) {
                AccessoryEffectService.sendFullSnapshot(
                        (net.minecraft.entity.player.EntityPlayerMP)event.player);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
        AccessoryEffectService.refresh(event == null ? null : event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        AccessoryEffectService.refresh(event == null ? null : event.player);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        AccessoryEffectService.deactivate(event == null ? null : event.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event != null && !event.isCanceled()
                && event.entityLiving instanceof EntityPlayer) {
            AccessoryEffectService.deactivate(
                    (EntityPlayer)event.entityLiving);
        }
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event != null
                && event.entityPlayer instanceof
                net.minecraft.entity.player.EntityPlayerMP
                && event.target instanceof EntityPlayer) {
            AccessoryEffectService.sendCurrentTo(
                    (net.minecraft.entity.player.EntityPlayerMP)
                            event.entityPlayer,
                    (EntityPlayer)event.target);
        }
    }
}
