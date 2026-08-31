package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;

/** Project-owned motion curves used by the Lost Tales chat presentation. */
final class LostTalesChatMotion {
    private LostTalesChatMotion() {}

    /**
     * Entry motion for the newest message. The primary action is the stack
     * rising into place; the secondary action slides the line in from the
     * left with a decelerating ease-out (slow-out), briefly overshoots its
     * resting position to the right (follow-through), and settles on a
     * damped return. Opacity leads slightly so the text is readable while
     * the motion is still finishing.
     */
    static MessageSample message(float progress) {
        float p = clamp(progress);
        float settled = smoothStep(p);
        float slideIn = -14.0F * (1.0F - settled) * (1.0F - settled);
        float followThrough = 6.0F * (1.0F - p)
                * (float)Math.sin(clamp((p - 0.35F) / 0.65F) * Math.PI);
        return new MessageSample(
                7.0F * (1.0F - settled),
                smoothStep(clamp(p / 0.58F)),
                slideIn + followThrough);
    }

    /** Entry of the input bars: up from below with a brief overshoot. */
    static float inputOffset(float progress) {
        float p = clamp(progress);
        float settled = smoothStep(p);
        float followThrough = (float)Math.sin(p * Math.PI * 2.5D)
                * (1.0F - p) * (1.0F - p);
        return 13.0F * (1.0F - settled) + 1.25F * followThrough;
    }

    /**
     * How long the shared scroll easing takes to cover most of the
     * distance to its target: short enough to feel immediate, long
     * enough to read as motion rather than a jump.
     */
    static final double SCROLL_EASE_SECONDS = 0.06D;

    /**
     * One step of the chat's shared scroll easing: the drawn value moves
     * toward its target by an exponential share of the remaining
     * distance, so the glide covers most of the gap in
     * {@code easeSeconds} and looks the same at every frame rate. The
     * elapsed time is capped so a long-hidden view steps rather than
     * leaps. The history's per-view offset and the pickers' body scroll
     * both step with this.
     */
    static double approach(double current, double target,
                           double elapsedSeconds, double easeSeconds) {
        double elapsed = Math.max(0.0D, Math.min(0.25D, elapsedSeconds));
        return current + (target - current)
                * (1.0D - Math.exp(-elapsed / easeSeconds));
    }

    static float smoothStep(float value) {
        return LostTalesGuiEasing.smoothStep(value);
    }

    private static float clamp(float value) {
        return LostTalesGuiEasing.clamp(value);
    }

    static final class MessageSample {
        final float stackOffsetY;
        final float opacity;
        /** Negative while entering from the left; briefly positive after. */
        final float slideOffsetX;

        private MessageSample(float stackOffsetY, float opacity,
                              float slideOffsetX) {
            this.stackOffsetY = stackOffsetY;
            this.opacity = opacity;
            this.slideOffsetX = slideOffsetX;
        }
    }
}
