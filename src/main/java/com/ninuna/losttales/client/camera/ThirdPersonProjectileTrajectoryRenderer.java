package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/** Draws a depth-tested prediction arc without moving the HUD crosshair. */
@SideOnly(Side.CLIENT)
public final class ThirdPersonProjectileTrajectoryRenderer {
    private static final int MAXIMUM_PREDICTION_TICKS = 200;
    private static final double PROJECTILE_COLLISION_PADDING = 0.15D;

    private ThirdPersonProjectileTrajectoryRenderer() {}

    public static void render(Minecraft minecraft, float partialTicks) {
        if (!shouldRender(minecraft)) {
            return;
        }
        EntityPlayerSP player = minecraft.thePlayer;
        ProjectileBallisticsProfile ballistics =
                ThirdPersonProjectileBallistics.resolve(
                        player, player.inventory.getCurrentItem());
        if (ballistics == null) {
            return;
        }
        ballistics = ballistics.scaleLaunchSpeed(
                ThirdPersonChargeFeedbackController
                        .getVelocityMultiplier());
        MovingObjectPosition target = ThirdPersonProjectileAimController
                .resolveTarget(minecraft, partialTicks);
        float partial = Math.max(0.0F, Math.min(1.0F, partialTicks));
        Vec3 eye = player.getPosition(partial);
        if (eye == null || target == null || target.hitVec == null) {
            return;
        }
        TargetingVector eyeOrigin = vector(eye);
        TargetingVector direction = vector(target.hitVec).subtract(
                eyeOrigin);
        if (direction.lengthSquared() <= 0.000000000001D) {
            return;
        }
        double shooterYaw = ProjectileLaunchGeometry.interpolateYaw(
                player.prevRotationYaw, player.rotationYaw, partial);
        TargetingVector physicalOrigin = ProjectileLaunchGeometry
                .resolvePhysicalOrigin(eyeOrigin, shooterYaw);

        final World world = minecraft.theWorld;
        final EntityPlayerSP shooter = player;
        List<TargetingVector> points = ProjectileTrajectorySolver.predict(
                physicalOrigin, direction, ballistics,
                LostTalesThirdPersonConfig.projectileAimDistance,
                MAXIMUM_PREDICTION_TICKS,
                new ProjectileTrajectorySolver.CollisionResolver() {
                    @Override
                    public TargetingVector resolve(
                            TargetingVector start, TargetingVector end) {
                        return resolveCollision(
                                world, shooter, start, end);
                    }
                });
        double modelScale = Math.max(
                0.5D, Math.min(1.5D, player.height / 1.8D));
        TargetingVector visualOrigin = ProjectileLaunchGeometry
                .resolveVisualOrigin(
                player.inventory.getCurrentItem(), physicalOrigin,
                direction, shooterYaw, modelScale);
        points = ProjectileLaunchGeometry.useVisualOrigin(
                points, visualOrigin,
                LostTalesThirdPersonConfig
                        .projectileTrajectoryOriginBlendDistance);
        if (points.size() < 2) {
            return;
        }
        points = ProjectileTrajectorySmoother.resample(
                points,
                LostTalesThirdPersonConfig
                        .projectileTrajectorySamplesPerTick,
                LostTalesThirdPersonConfig
                        .projectileTrajectorySmoothing);
        draw(points);
    }

    private static boolean shouldRender(Minecraft minecraft) {
        return LostTalesThirdPersonConfig.enableProjectilePrediction
                && minecraft != null
                && minecraft.thePlayer != null
                && minecraft.theWorld != null
                && minecraft.gameSettings != null
                && !minecraft.gameSettings.hideGUI
                && ThirdPersonProjectileAimController
                .shouldUseProjectileAim(minecraft);
    }

    @SuppressWarnings("unchecked")
    private static TargetingVector resolveCollision(
            World world, EntityPlayerSP shooter,
            TargetingVector start, TargetingVector end) {
        Vec3 startVector = vec(start);
        Vec3 endVector = vec(end);
        MovingObjectPosition blockHit = world.func_147447_a(
                startVector, endVector, false, true, false);
        TargetingVector nearest = blockHit == null
                || blockHit.hitVec == null
                ? null : vector(blockHit.hitVec);
        double nearestDistance = nearest == null
                ? Double.POSITIVE_INFINITY
                : start.distanceSquared(nearest);

        AxisAlignedBB sweep = AxisAlignedBB.getBoundingBox(
                Math.min(start.getX(), end.getX()),
                Math.min(start.getY(), end.getY()),
                Math.min(start.getZ(), end.getZ()),
                Math.max(start.getX(), end.getX()),
                Math.max(start.getY(), end.getY()),
                Math.max(start.getZ(), end.getZ())).expand(
                1.0D, 1.0D, 1.0D);
        List<Entity> entities = world
                .getEntitiesWithinAABBExcludingEntity(shooter, sweep);
        for (Entity entity : entities) {
            if (entity == null || !entity.canBeCollidedWith()
                    || entity == shooter.ridingEntity
                    && !entity.canRiderInteract()) {
                continue;
            }
            double padding = entity.getCollisionBorderSize()
                    + PROJECTILE_COLLISION_PADDING;
            AxisAlignedBB bounds = entity.boundingBox.expand(
                    padding, padding, padding);
            MovingObjectPosition intercept = bounds.calculateIntercept(
                    startVector, endVector);
            TargetingVector point;
            if (bounds.isVecInside(startVector)) {
                point = start;
            } else if (intercept != null && intercept.hitVec != null) {
                point = vector(intercept.hitVec);
            } else {
                continue;
            }
            double distance = start.distanceSquared(point);
            if (distance < nearestDistance) {
                nearest = point;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static void draw(List<TargetingVector> points) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LINE_BIT
                | GL11.GL_HINT_BIT
                | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glLineWidth((float)LostTalesThirdPersonConfig
                .projectileTrajectoryLineWidth);
        GL11.glColor4f(0.95F, 0.97F, 1.0F,
                (float)LostTalesThirdPersonConfig
                        .projectileTrajectoryOpacity);
        try {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawing(GL11.GL_LINE_STRIP);
            for (TargetingVector point : points) {
                tessellator.addVertex(
                        point.getX() - RenderManager.renderPosX,
                        point.getY() - RenderManager.renderPosY,
                        point.getZ() - RenderManager.renderPosZ);
            }
            tessellator.draw();
        } finally {
            GL11.glDepthMask(true);
            GL11.glPopAttrib();
        }
    }

    private static TargetingVector vector(Vec3 value) {
        return new TargetingVector(
                value.xCoord, value.yCoord, value.zCoord);
    }

    private static Vec3 vec(TargetingVector value) {
        return Vec3.createVectorHelper(
                value.getX(), value.getY(), value.getZ());
    }
}
