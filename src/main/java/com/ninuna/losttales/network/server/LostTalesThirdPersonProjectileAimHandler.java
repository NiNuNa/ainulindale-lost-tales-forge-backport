package com.ninuna.losttales.network.server;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import lotr.common.entity.projectile.LOTREntityProjectileBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/** Applies validated aim only to newly spawned player projectiles. */
public final class LostTalesThirdPersonProjectileAimHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event == null || event.entity == null || event.world == null
                || event.world.isRemote) {
            return;
        }
        EntityPlayerMP shooter = findShooter(event.entity);
        if (shooter != null) {
            LostTalesThirdPersonAimService.applyAim(
                    event.entity, shooter);
        }
    }

    private static EntityPlayerMP findShooter(Entity projectile) {
        Entity shooter = null;
        if (projectile instanceof EntityArrow) {
            shooter = ((EntityArrow)projectile).shootingEntity;
        } else if (projectile instanceof EntityThrowable) {
            shooter = ((EntityThrowable)projectile).getThrower();
        } else if (projectile instanceof LOTREntityProjectileBase) {
            shooter = ((LOTREntityProjectileBase)projectile).shootingEntity;
        }
        return shooter instanceof EntityPlayerMP
                ? (EntityPlayerMP)shooter : null;
    }
}
