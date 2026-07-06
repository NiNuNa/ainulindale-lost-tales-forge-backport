package com.ninuna.losttales.util;

import net.minecraft.entity.EntityLivingBase;

/**
 * Small legacy equivalent of the placement-rotation helper used by the modern NeoForge code.
 *
 * Minecraft 1.7.10 stores block orientation in metadata instead of BlockState properties, so
 * this helper keeps the metadata math in one place and prevents every renderer/block from
 * inventing its own conversion.
 */
public final class LostTalesBlockRotationHelper {

    private LostTalesBlockRotationHelper() {}

    public static int getSnappedRotationIndex(EntityLivingBase entity, int rotationSteps) {
        float yaw = -entity.rotationYaw % 360.0F;
        if (yaw < 0.0F) {
            yaw += 360.0F;
        }
        return Math.round(yaw / (360.0F / rotationSteps)) & (rotationSteps - 1);
    }

    public static float getRotationFromSnappedRotationIndex(EntityLivingBase entity, int rotationSteps) {
        return getSnappedRotationIndex(entity, rotationSteps) * (360.0F / rotationSteps);
    }

    /**
     * Converts the modern 16-step rotation idea into the metadata value expected by the
     * existing 1.7.10 plushie renderer.
     */
    public static int getLegacyPlushieMetadata(EntityLivingBase entity) {
        return (14 - getSnappedRotationIndex(entity, 16)) & 15;
    }

    public static float getLegacyPlushieRenderRotation(int metadata) {
        return 90.0F + ((14 - metadata) & 15) * (360.0F / 16.0F);
    }
}
