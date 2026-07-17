package com.ninuna.losttales.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * Reads the current authoritative-hook target without changing world state.
 * Disabled-overhaul and first-person calls still observe the vanilla result.
 */
public final class ThirdPersonTargetingSolver {
    private static final double REACH_TOLERANCE = 0.25D;
    private static final double DEFAULT_REACH = 3.0D;

    private ThirdPersonTargetingSolver() {}

    public static ThirdPersonTargetingResult resolve(
            Minecraft minecraft, float partialTicks) {
        if (minecraft == null || minecraft.renderViewEntity == null
                || minecraft.playerController == null) {
            return null;
        }
        EntityLivingBase viewEntity = minecraft.renderViewEntity;
        float partial = Math.max(0.0F, Math.min(1.0F, partialTicks));
        Vec3 eye = viewEntity.getPosition(partial);
        Vec3 look = viewEntity.getLook(partial);
        if (eye == null || look == null) {
            return null;
        }

        double blockReach = sanitizeReach(
                minecraft.playerController.getBlockReachDistance());
        MovingObjectPosition target = minecraft.objectMouseOver;
        double permittedReach = target != null
                && target.typeOfHit
                == MovingObjectPosition.MovingObjectType.ENTITY
                ? resolveEntityReach(blockReach,
                minecraft.playerController.extendedReach())
                : blockReach;
        boolean withinReach = target != null && target.hitVec != null
                && isFinite(target.hitVec.xCoord)
                && isFinite(target.hitVec.yCoord)
                && isFinite(target.hitVec.zCoord)
                && eye.squareDistanceTo(target.hitVec)
                <= square(permittedReach + REACH_TOLERANCE);
        Vec3 aim = withinReach
                ? target.hitVec
                : eye.addVector(
                look.xCoord * blockReach,
                look.yCoord * blockReach,
                look.zCoord * blockReach);
        return new ThirdPersonTargetingResult(
                withinReach ? target : null,
                eye.xCoord, eye.yCoord, eye.zCoord,
                aim.xCoord, aim.yCoord, aim.zCoord,
                permittedReach, withinReach);
    }

    static double resolveEntityReach(
            double blockReach, boolean extendedReach) {
        double safeReach = sanitizeReach(blockReach);
        return extendedReach ? 6.0D : Math.min(safeReach, 3.0D);
    }

    static double sanitizeReach(double reach) {
        return !isFinite(reach) || reach <= 0.0D
                ? DEFAULT_REACH : reach;
    }

    private static double square(double value) {
        return value * value;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
