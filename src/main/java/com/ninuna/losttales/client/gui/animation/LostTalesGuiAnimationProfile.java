package com.ninuna.losttales.client.gui.animation;

/**
 * Whether a screen takes the automatic opening motion and veil, and
 * whether the world behind it is blurred. How the opening moves is its
 * motion's ({@link com.ninuna.losttales.client.motion.MotionIds#SCREEN_OPEN}).
 */
public final class LostTalesGuiAnimationProfile {
    public static final LostTalesGuiAnimationProfile NONE =
            new LostTalesGuiAnimationProfile(false, false);
    public static final LostTalesGuiAnimationProfile DEFAULT =
            new LostTalesGuiAnimationProfile(true, true);

    private final boolean enabled;
    private final boolean blurBackground;

    public LostTalesGuiAnimationProfile(boolean enabled,
                                        boolean blurBackground) {
        this.enabled = enabled;
        this.blurBackground = blurBackground;
    }

    public boolean isEnabled() { return this.enabled; }
    public boolean isBlurBackground() { return this.blurBackground; }
}
