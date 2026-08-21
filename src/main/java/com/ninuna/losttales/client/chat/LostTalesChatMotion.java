package com.ninuna.losttales.client.chat;

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

    static float inputOffset(float progress) {
        float p = clamp(progress);
        float settled = smoothStep(p);
        float followThrough = (float)Math.sin(p * Math.PI * 2.5D)
                * (1.0F - p) * (1.0F - p);
        return 13.0F * (1.0F - settled) + 1.25F * followThrough;
    }

    static float menuProgress(float progress) {
        float p = clamp(progress);
        float settled = smoothStep(p);
        float followThrough = (float)Math.sin(p * Math.PI)
                * (1.0F - p) * 0.08F;
        return clamp(settled + followThrough);
    }

    static float stagger(float progress, int index) {
        float delay = Math.min(0.30F, Math.max(0, index) * 0.045F);
        return menuProgress((clamp(progress) - delay) / (1.0F - delay));
    }

    static float smoothStep(float value) {
        float p = clamp(value);
        return p * p * (3.0F - 2.0F * p);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
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
