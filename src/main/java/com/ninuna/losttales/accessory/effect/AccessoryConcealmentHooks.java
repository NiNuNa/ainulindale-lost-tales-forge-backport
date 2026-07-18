package com.ninuna.losttales.accessory.effect;

import com.ninuna.losttales.LostTalesMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/** Common-safe predicates called by vanilla and LOTR compatibility hooks. */
public final class AccessoryConcealmentHooks {

    private AccessoryConcealmentHooks() {}

    public static boolean isConcealed(Entity entity) {
        if (!(entity instanceof EntityPlayer) || entity.worldObj == null) {
            return false;
        }
        EntityPlayer player = (EntityPlayer)entity;
        if (!entity.worldObj.isRemote) {
            return AccessoryEffectService.isConcealed(player);
        }
        return LostTalesMod.proxy != null
                && LostTalesMod.proxy.isAccessoryConcealed(player);
    }
}
