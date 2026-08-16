package com.ninuna.losttales.client.gui.animation;

/** Per-screen override for safe automatic background/fade behavior. */
public final class LostTalesGuiAnimationProfile {
    public static final LostTalesGuiAnimationProfile NONE =
            new LostTalesGuiAnimationProfile(false, false, 0, 0);
    public static final LostTalesGuiAnimationProfile DEFAULT =
            new LostTalesGuiAnimationProfile(true, true, 220, 255);

    private final boolean enabled;
    private final boolean blurBackground;
    private final int durationMillis;
    private final int backdropAlpha;

    public LostTalesGuiAnimationProfile(
            boolean enabled, boolean blurBackground,
            int durationMillis, int backdropAlpha) {
        this.enabled = enabled;
        this.blurBackground = blurBackground;
        this.durationMillis = Math.max(1, durationMillis);
        this.backdropAlpha = Math.max(0,
                Math.min(255, backdropAlpha));
    }

    public boolean isEnabled() { return this.enabled; }
    public boolean isBlurBackground() { return this.blurBackground; }
    public int getDurationMillis() { return this.durationMillis; }
    public int getBackdropAlpha() { return this.backdropAlpha; }
}
