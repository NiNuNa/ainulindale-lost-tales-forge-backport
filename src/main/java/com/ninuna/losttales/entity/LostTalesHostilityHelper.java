package com.ninuna.losttales.entity;

import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;

/** Compatibility helpers for compass hostile markers. */
public final class LostTalesHostilityHelper {
    private LostTalesHostilityHelper() {}

    public static boolean isActivelyTargetingPlayer(EntityLivingBase living, EntityPlayer player) {
        if (living == null || player == null || !living.isEntityAlive()) {
            return false;
        }
        return living instanceof EntityLiving && ((EntityLiving) living).getAttackTarget() == player;
    }

    public static boolean isPassiveFallbackHostile(EntityLivingBase living, EntityPlayer player) {
        if (living == null || player == null || !living.isEntityAlive()) {
            return false;
        }
        if (living instanceof IMob) {
            return true;
        }
        return isUnfriendlyLotrNpc(living, player);
    }

    private static boolean isUnfriendlyLotrNpc(EntityLivingBase living, EntityPlayer player) {
        if (!(living instanceof LOTREntityNPC)) {
            return false;
        }
        LOTREntityNPC npc = (LOTREntityNPC) living;
        if (npc.isPassive) {
            return false;
        }
        try {
            return !npc.isFriendly(player);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
