package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import com.ninuna.losttales.gameplay.projectile.ThirdPersonProjectileItemPolicy;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonAimPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Synchronizes a bounded eye-origin projectile direction while ranged. */
public final class ThirdPersonProjectileAimController {
    private static final int SYNC_INTERVAL_TICKS = 2;
    private static final double MINIMUM_DIRECTION_LENGTH = 0.000001D;

    private static boolean active;
    private static String contextKey;
    private static int lastSyncTick = Integer.MIN_VALUE;

    private ThirdPersonProjectileAimController() {}

    public static void update(Minecraft minecraft) {
        if (!shouldUseProjectileAim(minecraft)) {
            deactivate(minecraft);
            return;
        }
        EntityPlayerSP player = minecraft.thePlayer;
        String newContext = contextKey(player);
        boolean contextChanged = !active
                || !newContext.equals(contextKey);
        if (!contextChanged
                && player.ticksExisted - lastSyncTick
                < SYNC_INTERVAL_TICKS) {
            return;
        }

        MovingObjectPosition target = resolveTarget(minecraft, 1.0F);
        Vec3 eye = player.getPosition(1.0F);
        if (target == null || target.hitVec == null || eye == null) {
            deactivate(minecraft);
            return;
        }
        double directionX = target.hitVec.xCoord - eye.xCoord;
        double directionY = target.hitVec.yCoord - eye.yCoord;
        double directionZ = target.hitVec.zCoord - eye.zCoord;
        double length = Math.sqrt(
                directionX * directionX
                        + directionY * directionY
                        + directionZ * directionZ);
        if (Double.isNaN(length) || Double.isInfinite(length)
                || length < MINIMUM_DIRECTION_LENGTH) {
            deactivate(minecraft);
            return;
        }

        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesThirdPersonAimPacket(
                        true,
                        (float)(directionX / length),
                        (float)(directionY / length),
                        (float)(directionZ / length)));
        active = true;
        contextKey = newContext;
        lastSyncTick = player.ticksExisted;
    }

    public static boolean shouldUseProjectileAim(Minecraft minecraft) {
        return minecraft != null && minecraft.thePlayer != null
                && minecraft.currentScreen == null
                && LostTalesThirdPersonConfig
                .enableProjectileAimCorrection
                && ThirdPersonCameraRuntime.shouldUseCamera(
                minecraft, minecraft.renderViewEntity)
                && ThirdPersonProjectileItemPolicy.isSupported(
                minecraft.thePlayer.inventory.getCurrentItem());
    }

    public static MovingObjectPosition resolveTarget(
            Minecraft minecraft, float partialTicks) {
        CameraRenderFrame frame =
                ThirdPersonCameraController.getRenderFrame();
        if (frame == null || !shouldUseProjectileAim(minecraft)) {
            return null;
        }
        return ThirdPersonCameraTargetingSolver.resolveProjectileAim(
                minecraft, frame, partialTicks,
                LostTalesThirdPersonConfig.projectileAimDistance);
    }

    private static void deactivate(Minecraft minecraft) {
        if (active && minecraft != null && minecraft.thePlayer != null
                && minecraft.theWorld != null) {
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new LostTalesThirdPersonAimPacket(
                            false, 0.0F, 0.0F, 0.0F));
        }
        reset();
    }

    public static void reset() {
        active = false;
        contextKey = null;
        lastSyncTick = Integer.MIN_VALUE;
    }

    private static String contextKey(EntityPlayerSP player) {
        if (player == null) {
            return "none";
        }
        int dimension = player.worldObj == null
                || player.worldObj.provider == null
                ? 0 : player.worldObj.provider.dimensionId;
        return player.getEntityId() + "@" + dimension;
    }
}
