package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/** Owns client lifecycle and the two-state F5/shoulder behavior. */
public final class ThirdPersonCameraRuntime {
    private ThirdPersonCameraRuntime() {}

    public static void normalizePerspective(Minecraft minecraft) {
        if (minecraft == null || minecraft.gameSettings == null) {
            return;
        }
        if (!isOverhaulOperational()) {
            ThirdPersonCameraController.deactivate();
            ThirdPersonDirectionalMovementController.reset(
                    minecraft.thePlayer);
            return;
        }
        minecraft.gameSettings.thirdPersonView =
                CameraPerspective.normalizeVanillaValue(
                minecraft.gameSettings.thirdPersonView, true);
        if (minecraft.gameSettings.thirdPersonView != 1) {
            ThirdPersonCameraController.deactivate();
        }
    }

    public static void onClientTick(Minecraft minecraft) {
        normalizePerspective(minecraft);
        if (!shouldUseCamera(minecraft, minecraft == null
                ? null : minecraft.renderViewEntity)) {
            ThirdPersonCameraController.deactivate();
        }
        if (minecraft != null && minecraft.thePlayer != null) {
            ThirdPersonGameplayStateTracker.update(
                    minecraft.thePlayer);
            ThirdPersonDirectionalMovementController.update(
                    minecraft.thePlayer);
        } else {
            ThirdPersonGameplayStateTracker.reset();
        }
        ThirdPersonProjectileAimController.update(minecraft);
    }

    public static void toggleShoulder(Minecraft minecraft) {
        normalizePerspective(minecraft);
        if (minecraft != null && minecraft.currentScreen == null
                && isOverhaulOperational()
                && minecraft.gameSettings.thirdPersonView == 1) {
            ThirdPersonCameraController.toggleShoulder();
        }
    }

    public static boolean adjustZoom(
            Minecraft minecraft, int wheelDelta) {
        normalizePerspective(minecraft);
        if (wheelDelta == 0 || minecraft == null
                || minecraft.currentScreen != null
                || !shouldUseCamera(
                minecraft, minecraft.renderViewEntity)) {
            return false;
        }
        return ThirdPersonCameraController.adjustZoom(
                wheelDelta,
                LostTalesThirdPersonConfig.minimumZoomDistance,
                LostTalesThirdPersonConfig.maximumZoomDistance,
                LostTalesThirdPersonConfig.zoomStep);
    }

    public static boolean shouldUseCamera(
            Minecraft minecraft, EntityLivingBase viewEntity) {
        if (minecraft == null || viewEntity == null
                || !isOverhaulOperational()
                || minecraft.gameSettings == null
                || minecraft.gameSettings.thirdPersonView != 1
                || minecraft.gameSettings.debugCamEnable
                || minecraft.entityRenderer == null
                || minecraft.entityRenderer.debugViewDirection != 0
                || minecraft.thePlayer == null
                || minecraft.renderViewEntity != viewEntity
                || minecraft.thePlayer != viewEntity
                || !viewEntity.isEntityAlive()) {
            return false;
        }
        return !(viewEntity instanceof EntityPlayer)
                || !((EntityPlayer)viewEntity).isPlayerSleeping();
    }

    public static boolean shouldUseDirectionalMovement(
            Minecraft minecraft, EntityPlayerSP player) {
        return LostTalesThirdPersonConfig.enableDirectionalMovement
                && shouldUseCamera(minecraft, player)
                && minecraft.currentScreen == null
                && player.movementInput != null
                && player.ridingEntity == null
                && !player.capabilities.isFlying
                && (!player.isInWater()
                || LostTalesThirdPersonConfig
                .enableSwimmingDirectionalMovement);
    }

    public static void resetSession() {
        Minecraft minecraft = Minecraft.getMinecraft();
        ThirdPersonDirectionalMovementController.reset(
                minecraft == null ? null : minecraft.thePlayer);
        ThirdPersonHeadRenderHook.reset();
        ThirdPersonGameplayStateTracker.reset();
        ThirdPersonCameraController.reset(
                LostTalesThirdPersonConfig.defaultRightShoulder);
        ThirdPersonCrosshairRenderer.reset();
        ThirdPersonProjectileAimController.reset();
        ThirdPersonTargetingHooks.resetDiagnostics();
    }

    public static boolean isOverhaulOperational() {
        return ThirdPersonCompatibilityPolicy.canActivateOverhaul(
                LostTalesThirdPersonConfig.enabled,
                Boolean.getBoolean(ThirdPersonCameraHooks.ACTIVE_PROPERTY));
    }
}
