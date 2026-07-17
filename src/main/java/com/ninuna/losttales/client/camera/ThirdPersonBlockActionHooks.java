package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonBlockActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Client-only replacement for camera-intent block-placement packets. */
public final class ThirdPersonBlockActionHooks {
    public static final String ACTIVE_PROPERTY =
            "losttales.thirdPersonBlockActionTransformer.active";

    private static final double HIT_MATCH_TOLERANCE = 0.001D;

    private ThirdPersonBlockActionHooks() {}

    /**
     * Called at the vanilla C08 send site after all client Forge/item/block
     * prechecks. Inactive or mismatched calls retain the original packet.
     */
    public static void sendBlockActionOrVanilla(
            NetHandlerPlayClient handler, Packet packet) {
        if (!(packet instanceof C08PacketPlayerBlockPlacement)
                || !shouldHandle(
                (C08PacketPlayerBlockPlacement)packet)) {
            handler.addToSendQueue(packet);
            return;
        }

        C08PacketPlayerBlockPlacement placement =
                (C08PacketPlayerBlockPlacement)packet;
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesThirdPersonBlockActionPacket(
                        placement.func_149576_c(),
                        placement.func_149571_d(),
                        placement.func_149570_e(),
                        placement.func_149568_f(),
                        clampOffset(placement.func_149573_h()),
                        clampOffset(placement.func_149569_i()),
                        clampOffset(placement.func_149575_j())));
    }

    private static boolean shouldHandle(
            C08PacketPlayerBlockPlacement placement) {
        Minecraft minecraft = Minecraft.getMinecraft();
        MovingObjectPosition mouseOver = minecraft == null
                ? null : minecraft.objectMouseOver;
        Vec3 hit = mouseOver == null ? null : mouseOver.hitVec;
        if (!ThirdPersonCompatibilityPolicy
                .canUseAuthoritativeTargeting(
                LostTalesThirdPersonConfig.enableCameraIntentTargeting,
                Boolean.getBoolean(
                ThirdPersonTargetingHooks.ACTIVE_PROPERTY),
                Boolean.getBoolean(ACTIVE_PROPERTY))
                || minecraft == null || minecraft.thePlayer == null
                || minecraft.currentScreen != null
                || !ThirdPersonCameraRuntime.shouldUseCamera(
                minecraft, minecraft.renderViewEntity)
                || mouseOver == null
                || mouseOver.typeOfHit
                != MovingObjectPosition.MovingObjectType.BLOCK
                || hit == null || placement.func_149568_f() < 0
                || placement.func_149568_f() > 5) {
            return false;
        }

        int x = placement.func_149576_c();
        int y = placement.func_149571_d();
        int z = placement.func_149570_e();
        float offsetX = placement.func_149573_h();
        float offsetY = placement.func_149569_i();
        float offsetZ = placement.func_149575_j();
        return mouseOver.blockX == x && mouseOver.blockY == y
                && mouseOver.blockZ == z
                && mouseOver.sideHit == placement.func_149568_f()
                && isUsableOffset(offsetX)
                && isUsableOffset(offsetY)
                && isUsableOffset(offsetZ)
                && nearlyEqual(hit.xCoord - (double)x, offsetX)
                && nearlyEqual(hit.yCoord - (double)y, offsetY)
                && nearlyEqual(hit.zCoord - (double)z, offsetZ);
    }

    private static boolean isUsableOffset(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value)
                && value >= -HIT_MATCH_TOLERANCE
                && value <= 1.0F + HIT_MATCH_TOLERANCE;
    }

    private static boolean nearlyEqual(double first, double second) {
        return Math.abs(first - second) <= HIT_MATCH_TOLERANCE;
    }

    private static float clampOffset(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
