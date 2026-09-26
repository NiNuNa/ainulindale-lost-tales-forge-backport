package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.motion.MotionIds;

/**
 * The newest message's entrance, whose shape is the chat's own. Its
 * times and distances are its motion's ({@link MotionIds#CHAT_LINE_APPEAR}).
 */
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
        String id = MotionIds.CHAT_LINE_APPEAR;
        float rise = Motions.param(id, "rise", 7.0F);
        float slide = Motions.param(id, "slide", 14.0F);
        float followThrough = Motions.param(id, "follow_through", 6.0F);
        float fadeLead = Math.max(0.05F, Motions.param(id, "fade_lead", 0.58F));
        float p = clamp(progress);
        float settled = smoothStep(p);
        float slideIn = -slide * (1.0F - settled) * (1.0F - settled);
        float swing = followThrough * (1.0F - p)
                * (float)Math.sin(clamp((p - 0.35F) / 0.65F) * Math.PI);
        return new MessageSample(
                rise * (1.0F - settled),
                smoothStep(clamp(p / fadeLead)),
                slideIn + swing);
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
