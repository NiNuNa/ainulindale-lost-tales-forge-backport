package com.ninuna.losttales.client.gui.animation;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;

/**
 * A screen's opening, timed from the moment it opened and read at any
 * instant: its content along {@link MotionIds#SCREEN_OPEN}, which says
 * where it starts (off its place and at what size) and how it travels
 * home, and the veil behind it along {@link MotionIds#SCREEN_BACKDROP}.
 * Frame-rate independent; reduced motion sets the content down at once
 * and keeps the veil's fade short.
 */
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

    /**
     * Starts the opening afresh; {@code preserveBackdrop} keeps the veil
     * standing, for one screen replacing another over the same world.
     */
    public void restart(boolean preserveBackdrop) {
        this.startedNanos = System.nanoTime();
        this.backdropStartedNanos = this.startedNanos;
        this.backdropSettled = preserveBackdrop;
    }

    /** Where the opening stands at {@code nowNanos}. */
    public LostTalesGuiAnimationSample sample(long nowNanos) {
        String open = MotionIds.SCREEN_OPEN;
        float progress = share(nowNanos - this.startedNanos,
                Motions.travelNanos(open));
        float eased = Motions.curve(open).apply(progress);
        float opacity;
        if (this.backdropSettled) {
            opacity = 1.0F;
        } else {
            opacity = Motions.curve(MotionIds.SCREEN_BACKDROP).apply(share(
                    nowNanos - this.backdropStartedNanos,
                    Motions.nanos(MotionIds.SCREEN_BACKDROP)));
            if (opacity >= 1.0F) {
                this.backdropSettled = true;
            }
        }
        float remaining = 1.0F - eased;
        float startScale = Math.max(0.5F, Math.min(3.0F,
                Motions.param(open, "start_scale", 1.0F)));
        float scale = startScale + (1.0F - startScale) * eased;
        return new LostTalesGuiAnimationSample(progress, eased, opacity,
                Motions.param(open, "start_x", 0.0F) * remaining,
                Motions.param(open, "start_y", -14.0F) * remaining,
                scale, scale);
    }

    /** How far through {@code duration} {@code elapsed} is; all of it for none. */
    private static float share(long elapsed, long duration) {
        return duration <= 0L ? 1.0F
                : LostTalesGuiEasing.clamp(elapsed / (float)duration);
    }
}
