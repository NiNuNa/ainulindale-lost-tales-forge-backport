package com.ninuna.losttales.client.gui.animation;

/** Reusable monotonic, frame-rate-independent opening animation lifecycle. */
public final class LostTalesGuiAnimationState {
    private long startedNanos;
    private long backdropStartedNanos;
    private boolean backdropSettled;

    public LostTalesGuiAnimationState() {
        restart();
    }

    public void restart() {
        restart(false);
    }

    public void restart(boolean preserveBackdrop) {
        this.startedNanos = System.nanoTime();
        this.backdropStartedNanos = this.startedNanos;
        this.backdropSettled = preserveBackdrop;
    }

    public LostTalesGuiAnimationSample sample(
            long nowNanos, int durationMillis, boolean reducedMotion) {
        return sample(nowNanos, durationMillis, durationMillis,
                reducedMotion, "BACK", "DOWN", 1.0F);
    }

    public LostTalesGuiAnimationSample sample(
            long nowNanos, int durationMillis, int backdropDurationMillis,
            boolean reducedMotion, String easingStyle,
            String direction, float animationScale) {
        long durationNanos = Math.max(1, durationMillis) * 1000000L;
        float progress = LostTalesGuiEasing.clamp(
                (nowNanos - this.startedNanos) / (float)durationNanos);
        float eased = ease(progress, easingStyle);
        float opacity;
        if (this.backdropSettled || backdropDurationMillis <= 0) {
            opacity = 1.0F;
        } else {
            long backdropNanos = Math.max(1, backdropDurationMillis)
                    * 1000000L;
            opacity = LostTalesGuiEasing.smoothStep(
                    (nowNanos - this.backdropStartedNanos)
                            / (float)backdropNanos);
            if (opacity >= 1.0F) {
                this.backdropSettled = true;
            }
        }
        if (reducedMotion) {
            return new LostTalesGuiAnimationSample(
                    progress, eased, opacity,
                    0.0F, 0.0F, 1.0F, 1.0F);
        }

        float distance = 14.0F;
        float translationX = 0.0F;
        float translationY = 0.0F;
        float remaining = 1.0F - eased;
        if ("UP".equalsIgnoreCase(direction)) {
            translationY = distance * remaining;
        } else if ("LEFT".equalsIgnoreCase(direction)) {
            translationX = distance * remaining;
        } else if ("RIGHT".equalsIgnoreCase(direction)) {
            translationX = -distance * remaining;
        } else if (!"NONE".equalsIgnoreCase(direction)) {
            translationY = -distance * remaining;
        }
        float startingScale = Math.max(0.5F,
                Math.min(3.0F, animationScale));
        float scale = mix(startingScale, 1.0F, eased);
        return new LostTalesGuiAnimationSample(
                progress, eased, opacity,
                translationX, translationY, scale, scale);
    }

    private static float ease(float progress, String style) {
        if ("CUBIC".equalsIgnoreCase(style)) {
            return LostTalesGuiEasing.easeOutCubic(progress);
        }
        if ("SMOOTH".equalsIgnoreCase(style)) {
            return LostTalesGuiEasing.smoothStep(progress);
        }
        return LostTalesGuiEasing.subtleBackOut(progress);
    }

    private static float mix(float from, float to, float progress) {
        return from + (to - from) * progress;
    }
}
