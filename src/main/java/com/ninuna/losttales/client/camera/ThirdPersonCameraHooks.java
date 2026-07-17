package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.character.physics.CharacterCameraHook;
import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

/** Narrow render hooks injected into EntityRenderer by the core transformer. */
public final class ThirdPersonCameraHooks {
    public static final String ACTIVE_PROPERTY =
            "losttales.thirdPersonCameraTransformer.active";
    private static final double VANILLA_LIMB_SWING_RADIANS = 0.6662D;

    private ThirdPersonCameraHooks() {}

    public static double resolveDistance(
            EntityLivingBase viewEntity, double vanillaDistance,
            float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!isTransformerActive()
                || !ThirdPersonCameraRuntime.shouldUseCamera(
                minecraft, viewEntity)) {
            ThirdPersonCameraController.deactivate();
            return vanillaDistance;
        }

        ThirdPersonGameplayState gameplayState =
                viewEntity instanceof EntityPlayerSP
                ? ThirdPersonGameplayStateTracker.get(
                (EntityPlayerSP)viewEntity)
                : ThirdPersonGameplayState.INACTIVE;
        CameraProfileId profileId = CameraProfileResolver.resolve(
                viewEntity.ridingEntity != null,
                viewEntity.isInWater(), gameplayState.isAiming(),
                gameplayState.isAttacking(),
                gameplayState.isCombat(), viewEntity.isSneaking(),
                viewEntity.isSprinting(), isMoving(viewEntity));
        CameraPresetFileStore.ensureLoaded();
        CameraProfile profile = CameraPresetFileStore.getPreset(
                LostTalesThirdPersonConfig.cameraPreset).get(profileId);
        double pivotX = interpolate(
                viewEntity.prevPosX, viewEntity.posX, partialTicks);
        double pivotY = interpolate(
                viewEntity.prevPosY, viewEntity.posY, partialTicks)
                - CharacterCameraHook.resolveCameraOffset(
                viewEntity, viewEntity.yOffset - 1.62F);
        double pivotZ = interpolate(
                viewEntity.prevPosZ, viewEntity.posZ, partialTicks);
        double yaw = interpolateAngle(
                viewEntity.prevRotationYaw, viewEntity.rotationYaw,
                partialTicks);
        double pitch = interpolate(
                viewEntity.prevRotationPitch, viewEntity.rotationPitch,
                partialTicks);
        double shoulder = profile.getShoulderOffset()
                * LostTalesThirdPersonConfig.shoulderOffsetMultiplier
                * ThirdPersonCameraController.getShoulderSign();
        double profileDistance = profile.getDistance()
                * LostTalesThirdPersonConfig.distanceMultiplier;
        double targetDistance =
                ThirdPersonCameraController.resolveZoomDistance(
                profileDistance,
                LostTalesThirdPersonConfig.minimumZoomDistance,
                LostTalesThirdPersonConfig.maximumZoomDistance);
        CameraPose target = new CameraPose(
                pivotX, pivotY, pivotZ, yaw, pitch,
                targetDistance,
                shoulder,
                profile.getVerticalOffset()
                        * LostTalesThirdPersonConfig.verticalOffsetMultiplier,
                profile.getFovOffset());
        double stridePhase = Double.NaN;
        double strideIntensity = Double.NaN;
        if (viewEntity.ridingEntity == null) {
            stridePhase = (viewEntity.limbSwing
                    - viewEntity.limbSwingAmount
                    * (1.0D - Math.max(0.0D,
                    Math.min(1.0D, partialTicks))))
                    * VANILLA_LIMB_SWING_RADIANS;
            strideIntensity = interpolate(
                    viewEntity.prevLimbSwingAmount,
                    viewEntity.limbSwingAmount, partialTicks);
        }
        double motionMultiplier = LostTalesThirdPersonConfig
                .enableCameraMotion
                ? LostTalesThirdPersonConfig.cameraMotionMultiplier
                : 0.0D;
        CameraPose current = ThirdPersonCameraController.update(
                contextKey(viewEntity), target,
                profile.getSmoothing().scaled(
                LostTalesThirdPersonConfig.transitionSpeedMultiplier),
                profile.getMotion(),
                motionMultiplier,
                stridePhase, strideIntensity,
                createMotionEffectsSample(viewEntity),
                createMotionEffectsSettings(motionMultiplier),
                System.nanoTime());

        CameraRenderTransform.LocalOffsets desiredOffsets =
                CameraRenderTransform.resolveDesiredOffsets(
                pivotX, pivotY, pivotZ, yaw, pitch,
                current,
                ThirdPersonCameraController.getMotionOffset());
        double allowedDistance = CameraCollisionResolver.resolveAllowedDistance(
                createWorldRaycaster(viewEntity),
                pivotX, pivotY, pivotZ, yaw, pitch,
                current.getDistance(), desiredOffsets.getSide(),
                desiredOffsets.getVertical(),
                desiredOffsets.getForward(),
                LostTalesThirdPersonConfig.collisionPadding);
        return ThirdPersonCameraController.constrainDistance(
                current.getDistance(), allowedDistance,
                LostTalesThirdPersonConfig.collisionReleaseRate);
    }

    public static void applyCameraOffset(
            EntityLivingBase viewEntity, float partialTicks,
            double actualDistance) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!isTransformerActive()
                || !ThirdPersonCameraRuntime.shouldUseCamera(
                minecraft, viewEntity)) {
            return;
        }
        CameraPose pose = ThirdPersonCameraController.getCurrentPose();
        if (pose == null) {
            return;
        }
        CameraRenderTransform transform =
                ThirdPersonCameraController.prepareRenderFrame(
                interpolate(viewEntity.prevPosX, viewEntity.posX,
                        partialTicks),
                interpolate(viewEntity.prevPosY, viewEntity.posY,
                        partialTicks)
                        - CharacterCameraHook.resolveCameraOffset(
                        viewEntity, viewEntity.yOffset - 1.62F),
                interpolate(viewEntity.prevPosZ, viewEntity.posZ,
                        partialTicks),
                interpolateAngle(
                        viewEntity.prevRotationYaw,
                        viewEntity.rotationYaw, partialTicks),
                interpolate(
                        viewEntity.prevRotationPitch,
                        viewEntity.rotationPitch, partialTicks),
                actualDistance);
        if (transform == null) {
            return;
        }
        GL11.glTranslatef(
                (float)transform.getTranslateX(),
                (float)transform.getTranslateY(),
                (float)transform.getTranslateZ());
    }

    public static float resolveFov(float vanillaFov, boolean useFovSetting) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!useFovSetting || !isTransformerActive()
                || !ThirdPersonCameraRuntime.shouldUseCamera(
                minecraft, minecraft == null
                ? null : minecraft.renderViewEntity)) {
            return vanillaFov;
        }
        CameraPose pose = ThirdPersonCameraController.getCurrentPose();
        float resolvedFov = vanillaFov;
        if (LostTalesThirdPersonConfig.enableFovEffects && pose != null) {
            resolvedFov = (float)Math.max(
                    30.0D, Math.min(110.0D,
                    vanillaFov + pose.getFovOffset()));
        }
        ThirdPersonCameraController.recordRenderedFov(resolvedFov);
        return resolvedFov;
    }

    private static CameraCollisionResolver.Raycaster createWorldRaycaster(
            final EntityLivingBase viewEntity) {
        return new CameraCollisionResolver.Raycaster() {
            @Override
            public double trace(
                    double startX, double startY, double startZ,
                    double endX, double endY, double endZ) {
                Vec3 start = Vec3.createVectorHelper(
                        startX, startY, startZ);
                MovingObjectPosition hit = viewEntity.worldObj.rayTraceBlocks(
                        start, Vec3.createVectorHelper(endX, endY, endZ));
                return hit == null || hit.hitVec == null
                        ? -1.0D : start.distanceTo(hit.hitVec);
            }
        };
    }

    private static boolean isMoving(EntityLivingBase entity) {
        return Math.abs(entity.moveForward) > 0.01F
                || Math.abs(entity.moveStrafing) > 0.01F;
    }

    private static CameraMotionEffectsSample createMotionEffectsSample(
            EntityLivingBase entity) {
        boolean riding = entity.ridingEntity != null;
        boolean swimming = entity.isInWater();
        boolean flying = entity instanceof EntityPlayerSP
                && ((EntityPlayerSP)entity).capabilities.isFlying;
        boolean airborne = !entity.onGround && !riding
                && !swimming && !flying;
        Entity movementSource = riding ? entity.ridingEntity : entity;
        double motionX = movementSource == null
                ? 0.0D : movementSource.motionX;
        double motionZ = movementSource == null
                ? 0.0D : movementSource.motionZ;
        double horizontalSpeed = Math.sqrt(
                motionX * motionX + motionZ * motionZ) * 20.0D;
        if (Double.isNaN(horizontalSpeed)
                || Double.isInfinite(horizontalSpeed)) {
            horizontalSpeed = 0.0D;
        }
        double verticalVelocity = entity.motionY;
        if (Double.isNaN(verticalVelocity)
                || Double.isInfinite(verticalVelocity)) {
            verticalVelocity = 0.0D;
        }
        return new CameraMotionEffectsSample(
                airborne, entity.onGround, riding, swimming,
                entity.isSwingInProgress, Math.max(0, entity.hurtTime),
                verticalVelocity, horizontalSpeed);
    }

    private static CameraMotionEffectsSettings createMotionEffectsSettings(
            double masterMultiplier) {
        return new CameraMotionEffectsSettings(
                masterMultiplier * LostTalesThirdPersonConfig
                        .airborneMotionMultiplier,
                masterMultiplier * LostTalesThirdPersonConfig
                        .landingMotionMultiplier,
                masterMultiplier * LostTalesThirdPersonConfig
                        .ridingMotionMultiplier,
                masterMultiplier * LostTalesThirdPersonConfig
                        .swimmingMotionMultiplier,
                masterMultiplier * LostTalesThirdPersonConfig
                        .attackMotionMultiplier,
                masterMultiplier * LostTalesThirdPersonConfig
                        .damageMotionMultiplier,
                masterMultiplier * LostTalesThirdPersonConfig
                        .explosionMotionMultiplier);
    }

    private static String contextKey(EntityLivingBase entity) {
        int dimension = entity.worldObj == null
                || entity.worldObj.provider == null
                ? 0 : entity.worldObj.provider.dimensionId;
        return entity.getEntityId() + "@" + dimension;
    }

    private static double interpolate(
            double previous, double current, float partialTicks) {
        double partial = Math.max(0.0D, Math.min(1.0D, partialTicks));
        return previous + (current - previous) * partial;
    }

    private static double interpolateAngle(
            double previous, double current, float partialTicks) {
        double partial = Math.max(0.0D, Math.min(1.0D, partialTicks));
        return previous + CameraMath.wrapDegrees(current - previous) * partial;
    }

    private static boolean isTransformerActive() {
        return Boolean.getBoolean(ACTIVE_PROPERTY);
    }

}
