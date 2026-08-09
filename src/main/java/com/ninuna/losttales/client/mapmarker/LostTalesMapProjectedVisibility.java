package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Smooth visibility from the size an object actually occupies on screen. */
@SideOnly(Side.CLIENT)
final class LostTalesMapProjectedVisibility {
    private static final float FULLY_VISIBLE_MULTIPLIER = 2.25F;

    private LostTalesMapProjectedVisibility() {
    }

    static float alpha(float projectedWidth, float minimumReadableWidth) {
        if (!(projectedWidth > 0.0F)
                || !(minimumReadableWidth > 0.0F)) {
            return 0.0F;
        }
        float fullyVisible = minimumReadableWidth
                * FULLY_VISIBLE_MULTIPLIER;
        float progress = (projectedWidth - minimumReadableWidth)
                / (fullyVisible - minimumReadableWidth);
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
