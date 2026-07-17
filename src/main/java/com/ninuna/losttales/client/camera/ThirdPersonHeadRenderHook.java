package com.ninuna.losttales.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.RenderPlayerEvent;

/** Temporarily supplies model-only pitch without changing gameplay look. */
public final class ThirdPersonHeadRenderHook {
    private static EntityPlayerSP overriddenPlayer;
    private static float savedPitch;
    private static float savedPreviousPitch;

    private ThirdPersonHeadRenderHook() {}

    public static void onPre(RenderPlayerEvent.Pre event) {
        restore();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event == null || event.isCanceled() || minecraft == null
                || event.entityPlayer != minecraft.thePlayer
                || !(event.entityPlayer instanceof EntityPlayerSP)) {
            return;
        }
        EntityPlayerSP player = (EntityPlayerSP)event.entityPlayer;
        if (!ThirdPersonDirectionalMovementController
                .shouldOverrideHeadPitch(player)) {
            return;
        }
        overriddenPlayer = player;
        savedPitch = player.rotationPitch;
        savedPreviousPitch = player.prevRotationPitch;
        player.rotationPitch = ThirdPersonDirectionalMovementController
                .getHeadPitch();
        player.prevRotationPitch = ThirdPersonDirectionalMovementController
                .getPreviousHeadPitch();
    }

    public static void onPost(RenderPlayerEvent.Post event) {
        if (event != null && event.entityPlayer == overriddenPlayer) {
            restore();
        }
    }

    public static void reset() {
        restore();
    }

    private static void restore() {
        if (overriddenPlayer != null) {
            overriddenPlayer.rotationPitch = savedPitch;
            overriddenPlayer.prevRotationPitch = savedPreviousPitch;
            overriddenPlayer = null;
        }
    }
}
