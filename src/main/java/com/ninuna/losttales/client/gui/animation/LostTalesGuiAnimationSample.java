package com.ninuna.losttales.client.gui.animation;

/** Immutable elapsed-time sample suitable for GUI or state-driven HUD use. */
public final class LostTalesGuiAnimationSample {
    public static final LostTalesGuiAnimationSample SETTLED =
            new LostTalesGuiAnimationSample(
                    1.0F, 1.0F, 1.0F,
                    0.0F, 0.0F, 1.0F, 1.0F);

    private final float progress;
    private final float easedProgress;
    private final float opacity;
    private final float translationX;
    private final float translationY;
    private final float scaleX;
    private final float scaleY;

    LostTalesGuiAnimationSample(float progress, float easedProgress,
                                float opacity, float translationY,
                                float scaleX, float scaleY) {
        this(progress, easedProgress, opacity,
                0.0F, translationY, scaleX, scaleY);
    }

    LostTalesGuiAnimationSample(float progress, float easedProgress,
                                float opacity, float translationX,
                                float translationY,
                                float scaleX, float scaleY) {
        this.progress = progress;
        this.easedProgress = easedProgress;
        this.opacity = opacity;
        this.translationX = translationX;
        this.translationY = translationY;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    public float getProgress() { return this.progress; }
    public float getEasedProgress() { return this.easedProgress; }
    public float getOpacity() { return this.opacity; }
    public float getBackdropProgress() { return this.opacity; }
    public float getTranslationX() { return this.translationX; }
    public float getTranslationY() { return this.translationY; }
    /** Compatibility accessor for callers that only support uniform scaling. */
    public float getScale() { return (this.scaleX + this.scaleY) * 0.5F; }
    public float getScaleX() { return this.scaleX; }
    public float getScaleY() { return this.scaleY; }

    public int inverseMouseX(int mouseX, int screenWidth) {
        float center = screenWidth * 0.5F;
        return Math.round(center
                + (mouseX - center - this.translationX) / this.scaleX);
    }

    public int inverseMouseY(int mouseY, int screenHeight) {
        float center = screenHeight * 0.5F;
        return Math.round(center
                + (mouseY - center - this.translationY) / this.scaleY);
    }
}
