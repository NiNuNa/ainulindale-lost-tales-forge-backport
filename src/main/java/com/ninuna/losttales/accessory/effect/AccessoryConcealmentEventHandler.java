package com.ninuna.losttales.accessory.effect;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

/** Event-level fallback for target systems that bypass vanilla AI predicates. */
public final class AccessoryConcealmentEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSetAttackTarget(LivingSetAttackTargetEvent event) {
        if (event != null && event.target instanceof EntityPlayer
                && event.entityLiving instanceof EntityLiving
                && AccessoryEffectService.isConcealed(
                (EntityPlayer)event.target)) {
            ((EntityLiving)event.entityLiving).setAttackTarget(null);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttackEntity(AttackEntityEvent event) {
        if (event != null && event.target instanceof EntityPlayer
                && event.entityPlayer != event.target
                && AccessoryEffectService.isConcealed(
                (EntityPlayer)event.target)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteractEntity(EntityInteractEvent event) {
        if (event != null && event.target instanceof EntityPlayer
                && event.entityPlayer != event.target
                && AccessoryEffectService.isConcealed(
                (EntityPlayer)event.target)) {
            event.setCanceled(true);
        }
    }
}
