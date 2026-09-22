package com.ninuna.losttales.client.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.client.motion.MotionTestSettings;
import com.ninuna.losttales.config.LostTalesConfig;
import org.junit.Test;

/** The dots breathe between their floor and full strength, one behind the other. */
public final class ChatTypingDotsTest {

    @Test
    public void aDotRisesAndFallsOverOnePeriod() {
        assertEquals(ChatTypingDots.FLOOR, ChatTypingDots.opacity(0, 0L), 0.0001F);
        assertEquals(1.0F, ChatTypingDots.opacity(0, ChatTypingDots.PERIOD_NANOS / 2), 0.0001F);
        assertEquals(ChatTypingDots.FLOOR,
                ChatTypingDots.opacity(0, ChatTypingDots.PERIOD_NANOS), 0.0001F);
        for (long now = 0L; now < ChatTypingDots.PERIOD_NANOS * 2;
                now += ChatTypingDots.PERIOD_NANOS / 24) {
            float opacity = ChatTypingDots.opacity(0, now);
            assertTrue(opacity >= ChatTypingDots.FLOOR && opacity <= 1.0F);
        }
    }

    @Test
    public void theDotsFollowOneAnotherByTheStagger() {
        long now = 3L * ChatTypingDots.PERIOD_NANOS + 100000000L;
        assertEquals(ChatTypingDots.opacity(0, now - ChatTypingDots.STAGGER_NANOS),
                ChatTypingDots.opacity(1, now), 0.0001F);
        assertEquals(ChatTypingDots.opacity(0, now - 2 * ChatTypingDots.STAGGER_NANOS),
                ChatTypingDots.opacity(2, now), 0.0001F);
        assertEquals("a negative clock reads the same",
                ChatTypingDots.opacity(0, -ChatTypingDots.PERIOD_NANOS / 2),
                ChatTypingDots.opacity(0, ChatTypingDots.PERIOD_NANOS / 2), 0.0001F);
    }

    @Test
    public void stilledMotionLeavesAnEllipsis() {
        MotionTestSettings settings = MotionTestSettings.reset();
        try {
            LostTalesConfig.animations = false;
            assertEquals(1.0F, ChatTypingDots.opacity(0, 0L), 0.0F);
            assertEquals(1.0F, ChatTypingDots.opacity(2, 0L), 0.0F);
            LostTalesConfig.animations = true;
            LostTalesConfig.reducedMotion = true;
            assertEquals(1.0F, ChatTypingDots.opacity(1, 0L), 0.0F);
        } finally {
            settings.restore();
        }
    }

    @Test
    public void theDotsKeepThePaceInForce() {
        MotionTestSettings settings = MotionTestSettings.reset();
        try {
            LostTalesConfig.animationSpeed = 2.0D;
            // Twice as fast, a dot is at its brightest a quarter period in.
            assertEquals(1.0F, ChatTypingDots.opacity(0,
                    ChatTypingDots.PERIOD_NANOS / 4), 0.0001F);
        } finally {
            settings.restore();
        }
    }

    @Test
    public void theRowIsAsWideAsItsDotsAndGaps() {
        assertEquals(10, ChatTypingDots.WIDTH);
    }
}
