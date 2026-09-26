package com.ninuna.losttales.util;

import net.minecraft.entity.EntityLivingBase;
/**
 * Where a placed block faces. A plushie or an urn placed by a player keeps
 * its exact turn on its tile entity; the block's metadata holds a coarse
 * facing as well, which is the one a block placed without a player
 * ({@code /setblock}, a structure) faces by. The metadata math lives here
 * so no block or renderer works it out on its own.
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
     * A plushie's facing in sixteen steps as metadata, from the placer's
     * turn; the tile entity keeps the exact turn beside it.
     */
    public static int getPlushieMetadata(EntityLivingBase entity) {
        return (14 - getSnappedRotationIndex(entity, 16)) & 15;
    }

    /** The turn a plushie faces by when its tile entity keeps none. */
    public static float getPlushieMetadataRotation(int metadata) {
        return normalizeDegrees(90.0F + ((14 - metadata) & 15) * (360.0F / 16.0F));
    }

    /** An urn's facing in four steps as metadata, from the placer's turn. */
    public static int getDirectionalMetadata(EntityLivingBase entity) {
        int index = ((int)Math.floor((double)(entity.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3);
        int direction = (index + 2) % 4;

        if (direction == 0) return 1;
        if (direction == 1) return 3;
        if (direction == 2) return 0;
        return 2;
    }

    /** The turn an urn faces by when its tile entity keeps none. */
    public static float getDirectionalMetadataRotation(int metadata) {
        switch (metadata & 3) {
            case 1:
                return 180.0F;
            case 2:
                return 270.0F;
            case 3:
                return 90.0F;
            case 0:
            default:
                return 0.0F;
        }
    }

    public static float normalizeDegrees(float rotation) {
        rotation %= 360.0F;
        if (rotation < 0.0F) {
            rotation += 360.0F;
        }
        return rotation;
    }
}
