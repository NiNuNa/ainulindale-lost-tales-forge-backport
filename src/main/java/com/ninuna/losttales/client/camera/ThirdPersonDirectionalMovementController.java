package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * Client visual body-facing controller. Logical yaw, movement input, combat,
 * and packets remain vanilla so the server retains its normal authority.
 */
public final class ThirdPersonDirectionalMovementController {
    private static final float TICK_SECONDS = 1.0F / 20.0F;
    private static final float MOVEMENT_EPSILON = 0.01F;

    private static boolean active;
    private static String contextKey;
    private static float bodyYaw;
    private static float previousBodyYaw;
    private static float headYaw;
    private static float previousHeadYaw;
    private static float headPitch;
    private static float previousHeadPitch;
    private static long activationSequence;
    private static boolean attackCommittedLastTick;
    private static float committedAttackYaw;
    private static boolean reverseHeadTracking;
    private static float reverseHeadTrackingBlend;

    private ThirdPersonDirectionalMovementController() {}

    public static void update(EntityPlayerSP player) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!ThirdPersonCameraRuntime.shouldUseDirectionalMovement(
                minecraft, player)) {
            reset(player);
            return;
        }
        ThirdPersonGameplayState gameplayState =
                ThirdPersonGameplayStateTracker.get(player);

        String newContextKey = contextKey(player);
        if (!active || !newContextKey.equals(contextKey)) {
            bodyYaw = finiteOr(player.renderYawOffset, player.rotationYaw);
            previousBodyYaw = bodyYaw;
            headYaw = finiteOr(player.rotationYawHead, player.rotationYaw);
            previousHeadYaw = headYaw;
            boolean allowReverseTracking = !gameplayState.shouldFaceAim()
                    && !gameplayState.isAttacking();
            reverseHeadTracking = allowReverseTracking
                    && DirectionalMovementMath.updateReverseHeadTracking(
                    false,
                    bodyYaw, player.rotationYaw,
                    (float)LostTalesThirdPersonConfig.headTrackingAngle,
                    (float)LostTalesThirdPersonConfig
                            .headTrackingHysteresisAngle);
            reverseHeadTrackingBlend = reverseHeadTracking ? 1.0F : 0.0F;
            headPitch = DirectionalMovementMath.resolveHeadTrackingPitch(
                    player.rotationPitch, reverseHeadTrackingBlend);
            previousHeadPitch = headPitch;
            contextKey = newContextKey;
            active = true;
            activationSequence++;
            attackCommittedLastTick = false;
            committedAttackYaw = bodyYaw;
        }

        float moveForward = player.movementInput.moveForward;
        float moveStrafe = player.movementInput.moveStrafe;
        boolean moving = Math.abs(moveForward) > MOVEMENT_EPSILON
                || Math.abs(moveStrafe) > MOVEMENT_EPSILON;
        previousBodyYaw = bodyYaw;
        float targetYaw = bodyYaw;
        double speed = LostTalesThirdPersonConfig.bodyRotationSpeed;
        boolean rotate = false;

        if (gameplayState.isAttackCommitted()) {
            if (!attackCommittedLastTick) {
                committedAttackYaw = gameplayState.shouldFaceAim()
                        ? player.rotationYaw : moving
                        ? DirectionalMovementMath.resolveMovementYaw(
                        player.rotationYaw, moveForward, moveStrafe)
                        : bodyYaw;
            }
            targetYaw = committedAttackYaw;
            speed = LostTalesThirdPersonConfig.attackBodyRotationSpeed;
            rotate = true;
        } else if (gameplayState.shouldFaceAim()) {
            targetYaw = player.rotationYaw;
            speed = LostTalesThirdPersonConfig.aimingBodyRotationSpeed;
            rotate = true;
        } else if (moving) {
            targetYaw = DirectionalMovementMath.resolveMovementYaw(
                    player.rotationYaw, moveForward, moveStrafe);
            speed = player.isSprinting()
                    ? LostTalesThirdPersonConfig.sprintBodyRotationSpeed
                    : gameplayState.isAttacking()
                    ? LostTalesThirdPersonConfig.attackBodyRotationSpeed
                    : LostTalesThirdPersonConfig.bodyRotationSpeed;
            rotate = true;
        }

        if (rotate) {
            bodyYaw = DirectionalMovementMath.approachDegrees(
                    bodyYaw, targetYaw, (float)(speed * TICK_SECONDS));
        }
        attackCommittedLastTick = gameplayState.isAttackCommitted();

        previousHeadYaw = headYaw;
        previousHeadPitch = headPitch;
        float trackingAngle = (float)LostTalesThirdPersonConfig
                .headTrackingAngle;
        boolean allowReverseTracking = !gameplayState.shouldFaceAim()
                && !gameplayState.isAttacking();
        reverseHeadTracking = allowReverseTracking
                && DirectionalMovementMath.updateReverseHeadTracking(
                reverseHeadTracking, bodyYaw, player.rotationYaw,
                trackingAngle,
                (float)LostTalesThirdPersonConfig
                        .headTrackingHysteresisAngle);
        float transitionSeconds = (float)LostTalesThirdPersonConfig
                .headTrackingTransitionSeconds;
        reverseHeadTrackingBlend = DirectionalMovementMath.approachValue(
                reverseHeadTrackingBlend,
                reverseHeadTracking ? 1.0F : 0.0F,
                Math.min(1.0F, TICK_SECONDS
                        / Math.max(0.0001F, transitionSeconds)));
        float targetHeadYaw = DirectionalMovementMath
                .resolveHeadTrackingYaw(
                bodyYaw, player.rotationYaw,
                trackingAngle, reverseHeadTrackingBlend);
        float targetHeadPitch = DirectionalMovementMath
                .resolveHeadTrackingPitch(
                player.rotationPitch, reverseHeadTrackingBlend);
        float maximumHeadStep = (float)(LostTalesThirdPersonConfig
                .headTrackingSpeed * TICK_SECONDS);
        headYaw = DirectionalMovementMath.approachDegrees(
                headYaw, targetHeadYaw, maximumHeadStep);
        headPitch = DirectionalMovementMath.approachValue(
                headPitch, targetHeadPitch, maximumHeadStep);
        player.prevRenderYawOffset = previousBodyYaw;
        player.renderYawOffset = bodyYaw;
        player.prevRotationYawHead = previousHeadYaw;
        player.rotationYawHead = headYaw;
    }

    public static void reset(EntityPlayerSP player) {
        if (player != null && active
                && contextKey(player).equals(contextKey)) {
            float vanillaYaw = finiteOr(
                    player.rotationYaw, player.renderYawOffset);
            player.prevRenderYawOffset = vanillaYaw;
            player.renderYawOffset = vanillaYaw;
            player.prevRotationYawHead = vanillaYaw;
            player.rotationYawHead = vanillaYaw;
        }
        active = false;
        contextKey = null;
        bodyYaw = 0.0F;
        previousBodyYaw = 0.0F;
        headYaw = 0.0F;
        previousHeadYaw = 0.0F;
        headPitch = 0.0F;
        previousHeadPitch = 0.0F;
        attackCommittedLastTick = false;
        committedAttackYaw = 0.0F;
        reverseHeadTracking = false;
        reverseHeadTrackingBlend = 0.0F;
    }

    public static boolean isActive() {
        return active;
    }

    public static long getActivationSequence() {
        return activationSequence;
    }

    static boolean shouldOverrideHeadPitch(EntityPlayerSP player) {
        return active && player != null
                && contextKey(player).equals(contextKey);
    }

    static float getHeadPitch() {
        return headPitch;
    }

    static float getPreviousHeadPitch() {
        return previousHeadPitch;
    }

    private static String contextKey(EntityPlayerSP player) {
        int dimension = player == null || player.worldObj == null
                || player.worldObj.provider == null
                ? 0 : player.worldObj.provider.dimensionId;
        return player == null ? "none" : player.getEntityId() + "@" + dimension;
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
