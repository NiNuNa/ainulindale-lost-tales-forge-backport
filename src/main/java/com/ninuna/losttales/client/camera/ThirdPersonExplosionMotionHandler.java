package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraftforge.client.event.sound.PlaySoundEvent17;

/** Converts nearby client explosion sounds into distance-faded visual shake. */
public final class ThirdPersonExplosionMotionHandler {
    private ThirdPersonExplosionMotionHandler() {}

    public static void onSound(
            Minecraft minecraft, PlaySoundEvent17 event) {
        if (minecraft == null || minecraft.thePlayer == null
                || event == null || event.sound == null
                || !LostTalesThirdPersonConfig.enableCameraMotion
                || LostTalesThirdPersonConfig.cameraMotionMultiplier == 0.0D
                || LostTalesThirdPersonConfig
                .explosionMotionMultiplier == 0.0D
                || !ThirdPersonCameraRuntime.shouldUseCamera(
                minecraft, minecraft.thePlayer)) {
            return;
        }
        String name = event.name == null
                ? "" : event.name.toLowerCase(Locale.ROOT);
        if (!name.contains("explode") && !name.contains("explosion")) {
            return;
        }
        ISound sound = event.sound;
        double dx = sound.getXPosF() - minecraft.thePlayer.posX;
        double dy = sound.getYPosF() - minecraft.thePlayer.posY;
        double dz = sound.getZPosF() - minecraft.thePlayer.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double radius = LostTalesThirdPersonConfig.explosionMotionRadius;
        if (Double.isNaN(distance) || Double.isInfinite(distance)
                || distance >= radius) {
            return;
        }
        double attenuation = 1.0D - distance / radius;
        attenuation *= attenuation;
        double volumeScale = Math.max(0.25D, Math.min(
                1.5D, sound.getVolume() / 4.0D));
        ThirdPersonCameraController.triggerExplosionShake(
                attenuation * volumeScale);
    }
}
