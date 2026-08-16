package com.ninuna.losttales.client.gui.animation;

/** Small allocation-free easing catalogue shared by GUI and HUD motion. */
public final class LostTalesGuiEasing {
    private LostTalesGuiEasing() {}

    public static float smoothStep(float progress) {
        float value = clamp(progress);
        return value * value * (3.0F - 2.0F * value);
    }

    public static float easeOutCubic(float progress) {
        float remaining = 1.0F - clamp(progress);
        return 1.0F - remaining * remaining * remaining;
    }

    public static float subtleBackOut(float progress) {
        float value = clamp(progress) - 1.0F;
        float overshoot = 1.12F;
        return 1.0F + value * value
                * ((overshoot + 1.0F) * value + overshoot);
    }

    public static float clamp(float progress) {
        return Math.max(0.0F, Math.min(1.0F, progress));
    }
}
