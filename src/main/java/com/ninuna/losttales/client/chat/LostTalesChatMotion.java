package com.ninuna.losttales.client.chat;

/** Project-owned motion curves used by the Lost Tales chat presentation. */
final class LostTalesChatMotion {
    private LostTalesChatMotion() {}

    static MessageSample message(float progress) {
        float p = clamp(progress);
        float settled = smoothStep(p);
        float energy = (1.0F - p) * (1.0F - p);
        float followThrough = (float)Math.sin(p * Math.PI * 3.0D) * energy;
        float shapePulse = (float)Math.cos(p * Math.PI * 2.5D) * energy;
        return new MessageSample(
                7.0F * (1.0F - settled) + 0.9F * followThrough,
                smoothStep(clamp(p / 0.58F)),
                1.0F + 0.025F * shapePulse,
                1.0F - 0.035F * shapePulse,
                0.45F * followThrough);
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
        final float scaleX;
        final float scaleY;
        final float headLagY;

        private MessageSample(float stackOffsetY, float opacity,
                              float scaleX, float scaleY,
                              float headLagY) {
            this.stackOffsetY = stackOffsetY;
            this.opacity = opacity;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.headLagY = headLagY;
        }
    }
}
