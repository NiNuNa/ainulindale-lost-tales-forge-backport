package com.ninuna.losttales.entity;

import com.ninuna.losttales.entity.combat.LostTalesCombatEngagement;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/** Player-specific, common-side combat-state helpers. */
public final class LostTalesHostilityHelper {
    private LostTalesHostilityHelper() {}

    /**
     * Returns direct, currently observable combat evidence for this exact player.
     * Recent attack and disengagement state is maintained by the server tracker.
     */
    public static LostTalesCombatEngagement getDirectEngagement(EntityLivingBase living, EntityPlayer player) {
        if (living == null || player == null || living == player || !living.isEntityAlive()) {
            return LostTalesCombatEngagement.NONE;
        }
        if (living instanceof EntityLiving && ((EntityLiving) living).getAttackTarget() == player) {
            return LostTalesCombatEngagement.TARGETING;
        }
        return LostTalesCombatEngagement.NONE;
    }

    public static boolean isActivelyHostileTo(EntityLivingBase living, EntityPlayer player) {
        return getDirectEngagement(living, player) != LostTalesCombatEngagement.NONE;
    }

    /** Kept for source compatibility; broad class/faction hostility is no longer marker evidence. */
    @Deprecated
    public static boolean isPassiveFallbackHostile(EntityLivingBase living, EntityPlayer player) {
        return false;
    }
}
