package com.ninuna.losttales.compat.lotr.hired;

import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import cpw.mods.fml.common.FMLLog;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Called by the coremod at the end of {@code LOTRHiredNPCInfo.hireUnit}:
 * the unit has just been hired by the player, and is tagged with the
 * identity that player is playing as, so a later switch knows whose it
 * is. Purely a bookkeeping mark; any failure is logged once and never
 * touches the hire. Without the patch a unit is tagged instead when its
 * owner first switches away.
 */
public final class LostTalesLotrHiredUnitHook {
    private static volatile boolean failureLogged;

    private LostTalesLotrHiredUnitHook() {}

    public static void onHired(LOTRHiredNPCInfo info, EntityPlayer player) {
        try {
            if (!(player instanceof EntityPlayerMP) || player.worldObj == null
                    || player.worldObj.isRemote) {
                return;
            }
            LOTREntityNPC unit = LotrHiredUnitInfoAccess.entityOf(info);
            if (unit == null) {
                return;
            }
            PlayableIdentityResolver.Resolution resolution =
                    PlayableIdentityResolver.resolve((EntityPlayerMP)player);
            if (resolution.isAvailable()) {
                LotrHiredUnitTag.of(resolution.getIdentity()).write(unit);
            }
        } catch (Throwable throwable) {
            if (!failureLogged) {
                failureLogged = true;
                FMLLog.warning("[LostTales] Could not tag a hired unit with its "
                        + "hiring identity: %s", throwable);
            }
        }
    }
}
