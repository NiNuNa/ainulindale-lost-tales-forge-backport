package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Persistent client target selection and bounded camera steering. */
@SideOnly(Side.CLIENT)
public final class ThirdPersonTargetLockController {
    private static final float TICK_SECONDS = 1.0F / 20.0F;
    private static final double ANGLE_TIE_EPSILON = 0.001D;

    private static EntityLivingBase target;
    private static String contextKey;
    private static int blockedTicks;

    private ThirdPersonTargetLockController() {}

    public static void toggle(Minecraft minecraft) {
        if (!canOperate(minecraft)) {
            reset();
            return;
        }
        EntityPlayerSP player = minecraft.thePlayer;
        if (hasTarget(player)) {
            reset();
            return;
        }
        setTarget(player, selectBest(player));
    }

    public static void cycle(Minecraft minecraft, final int direction) {
        if (direction == 0 || !canOperate(minecraft)) {
            return;
        }
        final EntityPlayerSP player = minecraft.thePlayer;
        if (!hasTarget(player)) {
            setTarget(player, selectBest(player));
            return;
        }
        List<EntityLivingBase> candidates = collectCandidates(player);
        if (candidates.isEmpty()) {
            reset();
            return;
        }
        final TargetingVector origin = cameraOrigin(player);
        Collections.sort(candidates, new Comparator<EntityLivingBase>() {
            @Override
            public int compare(
                    EntityLivingBase left, EntityLivingBase right) {
                float leftYaw = ThirdPersonTargetLockMath.resolveYaw(
                        origin, aimPoint(left));
                float rightYaw = ThirdPersonTargetLockMath.resolveYaw(
                        origin, aimPoint(right));
                float leftDelta = DirectionalMovementMath.wrapDegrees(
                        leftYaw - player.rotationYaw);
                float rightDelta = DirectionalMovementMath.wrapDegrees(
                        rightYaw - player.rotationYaw);
                int angleOrder = Float.compare(leftDelta, rightDelta);
                return angleOrder != 0 ? angleOrder : Integer.compare(
                        left.getEntityId(), right.getEntityId());
            }
        });

        int currentIndex = indexOf(candidates, target);
        int nextIndex = currentIndex < 0
                ? direction > 0 ? 0 : candidates.size() - 1
                : wrapIndex(currentIndex + (direction > 0 ? 1 : -1),
                candidates.size());
        setTarget(player, candidates.get(nextIndex));
    }

    public static void update(Minecraft minecraft) {
        if (!canOperate(minecraft)) {
            reset();
            return;
        }
        EntityPlayerSP player = minecraft.thePlayer;
        if (!isValidLockedTarget(player, target)) {
            reset();
            return;
        }
        double releaseRange = LostTalesThirdPersonConfig
                .targetLockReleaseRange;
        if (player.getDistanceSqToEntity(target)
                > releaseRange * releaseRange) {
            reset();
            return;
        }

        if (hasLineOfSight(player, target)) {
            blockedTicks = 0;
        } else {
            blockedTicks++;
            int graceTicks = ThirdPersonGameplayStateTracker.secondsToTicks(
                    LostTalesThirdPersonConfig
                            .targetLockLineOfSightGraceSeconds);
            if (blockedTicks > graceTicks) {
                reset();
                return;
            }
        }
        steerTowardTarget(player, target);
    }

    public static boolean hasTarget(EntityPlayerSP player) {
        return player != null && target != null
                && contextKey(player).equals(contextKey)
                && isValidLockedTarget(player, target);
    }

    public static EntityLivingBase getTarget(EntityPlayerSP player) {
        return hasTarget(player) ? target : null;
    }

    public static void reset() {
        target = null;
        contextKey = null;
        blockedTicks = 0;
    }

    private static EntityLivingBase selectBest(EntityPlayerSP player) {
        TargetingVector origin = cameraOrigin(player);
        TargetingVector forward = cameraForward(player);
        EntityLivingBase best = null;
        double bestAngle = Double.POSITIVE_INFINITY;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (EntityLivingBase candidate : collectCandidates(player)) {
            TargetingVector point = aimPoint(candidate);
            double angle = ThirdPersonTargetLockMath.angleDegrees(
                    origin, forward, point);
            double distance = origin.distanceSquared(point);
            if (angle + ANGLE_TIE_EPSILON < bestAngle
                    || Math.abs(angle - bestAngle) <= ANGLE_TIE_EPSILON
                    && (distance < bestDistance
                    || distance == bestDistance
                    && candidate.getEntityId()
                    < (best == null ? Integer.MAX_VALUE
                    : best.getEntityId()))) {
                best = candidate;
                bestAngle = angle;
                bestDistance = distance;
            }
        }
        return best;
    }

    @SuppressWarnings("unchecked")
    private static List<EntityLivingBase> collectCandidates(
            EntityPlayerSP player) {
        List<EntityLivingBase> candidates =
                new ArrayList<EntityLivingBase>();
        if (player == null || player.worldObj == null) {
            return candidates;
        }
        TargetingVector origin = cameraOrigin(player);
        TargetingVector forward = cameraForward(player);
        double range = LostTalesThirdPersonConfig
                .targetLockSelectionRange;
        double maximumAngle = LostTalesThirdPersonConfig
                .targetLockSelectionAngle;
        for (Object value : player.worldObj.loadedEntityList) {
            if (!(value instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase candidate = (EntityLivingBase)value;
            if (!isEligibleCandidate(player, candidate)
                    || player.getDistanceSqToEntity(candidate)
                    > range * range
                    || !hasLineOfSight(player, candidate)
                    || ThirdPersonTargetLockMath.angleDegrees(
                    origin, forward, aimPoint(candidate)) > maximumAngle) {
                continue;
            }
            candidates.add(candidate);
        }
        return candidates;
    }

    private static boolean isEligibleCandidate(
            EntityPlayerSP player, EntityLivingBase candidate) {
        return candidate != null && candidate != player
                && candidate.worldObj == player.worldObj
                && candidate.isEntityAlive() && !candidate.isDead
                && candidate.boundingBox != null
                && candidate.canBeCollidedWith()
                && candidate != player.ridingEntity
                && !candidate.isInvisibleToPlayer(player)
                && LostTalesClientMobAggroCache.isAggro(
                candidate.getEntityId());
    }

    private static boolean isValidLockedTarget(
            EntityPlayerSP player, EntityLivingBase candidate) {
        return player != null && contextKey(player).equals(contextKey)
                && isEligibleCandidate(player, candidate);
    }

    private static void setTarget(
            EntityPlayerSP player, EntityLivingBase newTarget) {
        target = newTarget;
        contextKey = newTarget == null ? null : contextKey(player);
        blockedTicks = 0;
    }

    private static void steerTowardTarget(
            EntityPlayerSP player, EntityLivingBase lockedTarget) {
        TargetingVector origin = cameraOrigin(player);
        TargetingVector point = aimPoint(lockedTarget);
        float targetYaw = ThirdPersonTargetLockMath.resolveYaw(
                origin, point);
        float targetPitch = Math.max(-85.0F, Math.min(85.0F,
                ThirdPersonTargetLockMath.resolvePitch(origin, point)));
        player.rotationYaw = DirectionalMovementMath.approachDegrees(
                player.rotationYaw, targetYaw,
                (float)(LostTalesThirdPersonConfig
                        .targetLockYawSpeed * TICK_SECONDS));
        player.rotationPitch = DirectionalMovementMath.approachValue(
                player.rotationPitch, targetPitch,
                (float)(LostTalesThirdPersonConfig
                        .targetLockPitchSpeed * TICK_SECONDS));
    }

    private static boolean hasLineOfSight(
            EntityPlayerSP player, EntityLivingBase candidate) {
        TargetingVector origin = eye(player);
        TargetingVector point = aimPoint(candidate);
        MovingObjectPosition obstruction = player.worldObj.func_147447_a(
                vec(origin), vec(point), false, true, false);
        return obstruction == null || obstruction.hitVec == null;
    }

    private static TargetingVector eye(EntityPlayerSP player) {
        return new TargetingVector(
                player.posX,
                player.posY + player.getEyeHeight(),
                player.posZ);
    }

    private static TargetingVector cameraOrigin(EntityPlayerSP player) {
        CameraRenderFrame frame = currentFrame(player);
        return frame == null ? eye(player) : new TargetingVector(
                frame.getCameraX(), frame.getCameraY(),
                frame.getCameraZ());
    }

    private static TargetingVector cameraForward(EntityPlayerSP player) {
        CameraRenderFrame frame = currentFrame(player);
        return frame == null
                ? ThirdPersonTargetLockMath.directionFromRotation(
                player.rotationYaw, player.rotationPitch)
                : new TargetingVector(
                frame.getForwardX(), frame.getForwardY(),
                frame.getForwardZ());
    }

    private static CameraRenderFrame currentFrame(EntityPlayerSP player) {
        CameraRenderFrame frame = ThirdPersonCameraController
                .getRenderFrame();
        if (player == null || frame == null) {
            return null;
        }
        double dx = frame.getPivotX() - player.posX;
        double dy = frame.getPivotY() - player.posY;
        double dz = frame.getPivotZ() - player.posZ;
        return dx * dx + dy * dy + dz * dz <= 16.0D
                ? frame : null;
    }

    private static TargetingVector aimPoint(EntityLivingBase entity) {
        double factor = LostTalesThirdPersonConfig.targetLockHeightFactor;
        return new TargetingVector(
                entity.posX,
                entity.posY + entity.height * factor,
                entity.posZ);
    }

    private static Vec3 vec(TargetingVector value) {
        return Vec3.createVectorHelper(
                value.getX(), value.getY(), value.getZ());
    }

    private static int indexOf(
            List<EntityLivingBase> candidates,
            EntityLivingBase current) {
        if (current == null) {
            return -1;
        }
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index) == current) {
                return index;
            }
        }
        return -1;
    }

    private static int wrapIndex(int index, int size) {
        int wrapped = index % size;
        return wrapped < 0 ? wrapped + size : wrapped;
    }

    private static boolean canOperate(Minecraft minecraft) {
        return LostTalesThirdPersonConfig.enableTargetLock
                && minecraft != null && minecraft.currentScreen == null
                && minecraft.thePlayer != null
                && ThirdPersonCameraRuntime.shouldUseCamera(
                minecraft, minecraft.renderViewEntity);
    }

    private static String contextKey(EntityPlayerSP player) {
        int dimension = player == null || player.worldObj == null
                || player.worldObj.provider == null
                ? 0 : player.worldObj.provider.dimensionId;
        return player == null ? "none"
                : player.getEntityId() + "@" + dimension;
    }
}
