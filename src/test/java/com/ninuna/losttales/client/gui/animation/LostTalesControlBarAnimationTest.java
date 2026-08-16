package com.ninuna.losttales.client.gui.animation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LostTalesControlBarAnimationTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void controlBarWaitsBrieflyThenFliesUpFromBelow() {
        assertEquals(24.0F,
                LostTalesControlBarAnimation.offsetForProgress(0.0F),
                EPSILON);
        assertEquals(24.0F,
                LostTalesControlBarAnimation.offsetForProgress(0.1F),
                EPSILON);
        assertTrue(LostTalesControlBarAnimation.offsetForProgress(0.5F)
                < 12.0F);
        assertEquals(0.0F,
                LostTalesControlBarAnimation.offsetForProgress(1.0F),
                EPSILON);
    }
}
