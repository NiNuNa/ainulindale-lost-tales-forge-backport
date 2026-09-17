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

    /**
     * One step of a crossfade with no fixed duration, such as a hover:
     * the value moves the same share of what is left of the way each
     * second, so a target changed halfway is followed without a corner.
     * A long frame is treated as a quarter second, which keeps a stall
     * from jumping the value to its target.
     */
    public static double approach(double current, double target,
                                  double elapsedSeconds,
                                  double easeSeconds) {
        if (easeSeconds <= 0.0D) {
            return target;
        }
        double elapsed = Math.max(0.0D, Math.min(0.25D, elapsedSeconds));
        return current + (target - current)
                * (1.0D - Math.exp(-elapsed / easeSeconds));
    }
}
