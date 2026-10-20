package com.ninuna.losttales.accessory.effect;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Whether a player is concealed, asked by the coremod inside vanilla's
 * {@code Entity.isInvisible}, sight checks and AI targeting. It never
 * fails the tick: a failure is logged once and answers "not concealed",
 * as vanilla would.
 */
public final class AccessoryConcealmentHooks {
    private static volatile boolean failureLogged;

    private AccessoryConcealmentHooks() {}

    public static boolean isConcealed(Entity entity) {
        try {
            return concealed(entity);
        } catch (RuntimeException failure) {
            logOnce(failure);
        } catch (LinkageError incompatible) {
            logOnce(incompatible);
        }
        return false;
    }

    private static boolean concealed(Entity entity) {
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

    private static void logOnce(Throwable failure) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        LostTalesLog.warning("Concealment could not be read; players stay visible: %s",
                failure.toString());
    }
}
