package com.ninuna.losttales.client.camera;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * Resolves a camera-center intent, then repeats the ray from the player's eye.
 * Only the eye ray can become the interaction target, preserving reach and LOS.
 */
public final class ThirdPersonCameraTargetingSolver {
    private static final double REACH_TOLERANCE = 0.25D;
    private static final double FORWARD_EPSILON = 0.01D;
    private static final double ENTITY_SEARCH_PADDING = 1.0D;

    private ThirdPersonCameraTargetingSolver() {}

    public static MovingObjectPosition resolve(
            Minecraft minecraft, CameraRenderFrame frame,
            float partialTicks) {
        if (minecraft == null || frame == null
                || minecraft.theWorld == null
                || minecraft.renderViewEntity == null
                || minecraft.playerController == null) {
            return null;
        }
        double blockReach = ThirdPersonTargetingSolver.sanitizeReach(
                minecraft.playerController.getBlockReachDistance());
        double entityReach = ThirdPersonTargetingSolver.resolveEntityReach(
                blockReach, minecraft.playerController.extendedReach());

        return resolveWithReach(
                minecraft, frame, partialTicks,
                blockReach, entityReach);
    }

    public static MovingObjectPosition resolveProjectileAim(
            Minecraft minecraft, CameraRenderFrame frame,
            float partialTicks, double reach) {
        CameraMath.requireNonNegativeFinite("reach", reach);
        return resolveWithReach(
                minecraft, frame, partialTicks, reach, reach);
    }

    private static MovingObjectPosition resolveWithReach(
            Minecraft minecraft, CameraRenderFrame frame,
            float partialTicks, double blockReach,
            double entityReach) {
        if (minecraft == null || frame == null
                || minecraft.theWorld == null
                || minecraft.renderViewEntity == null) {
            return null;
        }
        EntityLivingBase viewEntity = minecraft.renderViewEntity;
        float partial = Math.max(0.0F, Math.min(1.0F, partialTicks));
        TargetingVector eye = vector(viewEntity.getPosition(partial));
        TargetingVector camera = new TargetingVector(
                frame.getCameraX(), frame.getCameraY(),
                frame.getCameraZ());
        TargetingVector forward = new TargetingVector(
                frame.getForwardX(), frame.getForwardY(),
                frame.getForwardZ()).normalizeOr(
                new TargetingVector(0.0D, 0.0D, 1.0D));

        double cameraRayLength = Math.sqrt(
                camera.distanceSquared(eye)) + blockReach;
        TargetingVector cameraEnd = camera.add(
                forward.scale(cameraRayLength));
        Candidate intent = resolveNearest(
                viewEntity, camera, cameraEnd,
                eye, forward, blockReach, entityReach, true);
        TargetingVector desired = intent == null
                ? cameraEnd : intent.aimPoint;
        TargetingVector eyeEnd = resolveEyeEndpoint(
                eye, desired, forward, blockReach);
        Candidate validated = resolveNearest(
                viewEntity, eye, eyeEnd,
                eye, forward, blockReach, entityReach, false);
        if (validated != null) {
            return validated.hit;
        }
        return miss(eyeEnd);
    }

    static TargetingVector resolveEyeEndpoint(
            TargetingVector eye, TargetingVector desired,
            TargetingVector fallbackDirection, double reach) {
        if (eye == null || desired == null || fallbackDirection == null) {
            throw new IllegalArgumentException(
                    "eye, desired, and fallback direction are required");
        }
        CameraMath.requireNonNegativeFinite("reach", reach);
        TargetingVector direction = desired.subtract(eye)
                .normalizeOr(fallbackDirection);
        return eye.add(direction.scale(reach));
    }

    static boolean isInFront(
            TargetingVector eye, TargetingVector point,
            TargetingVector forward) {
        if (eye == null || point == null || forward == null) {
            return false;
        }
        return point.subtract(eye).dot(forward) > FORWARD_EPSILON;
    }

    private static Candidate resolveNearest(
            EntityLivingBase viewEntity,
            TargetingVector start, TargetingVector end,
            TargetingVector eye, TargetingVector forward,
            double blockReach, double entityReach,
            boolean filterFromEye) {
        MovingObjectPosition blockHit = traceBlocks(
                viewEntity, start, end);
        Candidate block = candidateForBlock(
                blockHit, start, eye, forward,
                blockReach, filterFromEye);
        Candidate entity = traceEntity(
                viewEntity, start, end, eye, forward,
                entityReach, filterFromEye);
        if (block == null) {
            return entity;
        }
        if (entity == null) {
            return block;
        }
        return entity.distanceFromStart < block.distanceFromStart
                ? entity : block;
    }

    private static MovingObjectPosition traceBlocks(
            EntityLivingBase viewEntity,
            TargetingVector start, TargetingVector end) {
        return viewEntity.worldObj.func_147447_a(
                vec(start), vec(end), false, false, true);
    }

    private static Candidate candidateForBlock(
            MovingObjectPosition hit, TargetingVector start,
            TargetingVector eye, TargetingVector forward,
            double blockReach, boolean filterFromEye) {
        if (hit == null || hit.hitVec == null
                || hit.typeOfHit
                != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }
        TargetingVector point = vector(hit.hitVec);
        if (filterFromEye && (!isInFront(eye, point, forward)
                || eye.distanceSquared(point) > square(
                blockReach + REACH_TOLERANCE))) {
            return null;
        }
        return new Candidate(
                hit, point, start.distanceSquared(point));
    }

    @SuppressWarnings("unchecked")
    private static Candidate traceEntity(
            EntityLivingBase viewEntity,
            TargetingVector start, TargetingVector end,
            TargetingVector eye, TargetingVector forward,
            double entityReach, boolean filterFromEye) {
        AxisAlignedBB sweep = AxisAlignedBB.getBoundingBox(
                Math.min(start.getX(), end.getX()),
                Math.min(start.getY(), end.getY()),
                Math.min(start.getZ(), end.getZ()),
                Math.max(start.getX(), end.getX()),
                Math.max(start.getY(), end.getY()),
                Math.max(start.getZ(), end.getZ())).expand(
                ENTITY_SEARCH_PADDING, ENTITY_SEARCH_PADDING,
                ENTITY_SEARCH_PADDING);
        List<Entity> entities = viewEntity.worldObj
                .getEntitiesWithinAABBExcludingEntity(
                viewEntity, sweep);
        Candidate nearest = null;
        Vec3 startVector = vec(start);
        Vec3 endVector = vec(end);
        for (Entity entity : entities) {
            if (entity == null || !entity.canBeCollidedWith()) {
                continue;
            }
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB bounds = entity.boundingBox.expand(
                    border, border, border);
            MovingObjectPosition intercept = bounds.calculateIntercept(
                    startVector, endVector);
            TargetingVector hitPoint;
            if (bounds.isVecInside(startVector)) {
                hitPoint = start;
            } else if (intercept != null && intercept.hitVec != null) {
                hitPoint = vector(intercept.hitVec);
            } else {
                continue;
            }
            if (entity == viewEntity.ridingEntity
                    && !entity.canRiderInteract()) {
                continue;
            }
            if (filterFromEye && (!isInFront(
                    eye, hitPoint, forward)
                    || eye.distanceSquared(hitPoint) > square(
                    entityReach + REACH_TOLERANCE))) {
                continue;
            }
            if (!filterFromEye
                    && eye.distanceSquared(hitPoint) > square(
                    entityReach + REACH_TOLERANCE)) {
                continue;
            }
            double distance = start.distanceSquared(hitPoint);
            if (nearest == null || distance < nearest.distanceFromStart) {
                TargetingVector aimPoint = new TargetingVector(
                        (bounds.minX + bounds.maxX) * 0.5D,
                        (bounds.minY + bounds.maxY) * 0.5D,
                        (bounds.minZ + bounds.maxZ) * 0.5D);
                nearest = new Candidate(
                        new MovingObjectPosition(entity, vec(hitPoint)),
                        aimPoint, distance);
            }
        }
        return nearest;
    }

    private static MovingObjectPosition miss(TargetingVector point) {
        return new MovingObjectPosition(
                MathHelper.floor_double(point.getX()),
                MathHelper.floor_double(point.getY()),
                MathHelper.floor_double(point.getZ()),
                -1, vec(point), false);
    }

    private static TargetingVector vector(Vec3 value) {
        if (value == null) {
            throw new IllegalArgumentException("vector is required");
        }
        return new TargetingVector(
                value.xCoord, value.yCoord, value.zCoord);
    }

    private static Vec3 vec(TargetingVector value) {
        return Vec3.createVectorHelper(
                value.getX(), value.getY(), value.getZ());
    }

    private static double square(double value) {
        return value * value;
    }

    private static final class Candidate {
        private final MovingObjectPosition hit;
        private final TargetingVector aimPoint;
        private final double distanceFromStart;

        private Candidate(
                MovingObjectPosition hit, TargetingVector aimPoint,
                double distanceFromStart) {
            this.hit = hit;
            this.aimPoint = aimPoint;
            this.distanceFromStart = distanceFromStart;
        }
    }
}
