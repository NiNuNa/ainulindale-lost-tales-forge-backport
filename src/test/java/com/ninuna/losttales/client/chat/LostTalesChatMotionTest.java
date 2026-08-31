package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LostTalesChatMotionTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void messageSettlesAtNeutralReadableState() {
        LostTalesChatMotion.MessageSample settled =
                LostTalesChatMotion.message(1.0F);

        assertEquals(0.0F, settled.stackOffsetY, EPSILON);
        assertEquals(1.0F, settled.opacity, EPSILON);
        assertEquals(0.0F, settled.slideOffsetX, EPSILON);
    }

    @Test
    public void entryCombinesVerticalAndHorizontalMotionWithOpacity() {
        LostTalesChatMotion.MessageSample entering =
                LostTalesChatMotion.message(0.0F);

        assertTrue(entering.stackOffsetY > 0.0F);
        assertEquals(0.0F, entering.opacity, EPSILON);
        assertTrue(LostTalesChatMotion.message(0.5F).stackOffsetY
                < entering.stackOffsetY);
        // Enters from the left and decelerates toward its resting position.
        assertTrue(entering.slideOffsetX <= -10.0F);
        assertTrue(LostTalesChatMotion.message(0.25F).slideOffsetX
                > entering.slideOffsetX);
        assertTrue(LostTalesChatMotion.message(0.5F).slideOffsetX
                > LostTalesChatMotion.message(0.25F).slideOffsetX);
    }

    @Test
    public void entryOvershootsRightThenSettlesBack() {
        // Follow-through: shortly before settling the line sits slightly
        // right of its final position, then returns to exactly zero.
        float late = LostTalesChatMotion.message(0.75F).slideOffsetX;
        assertTrue(late > 0.5F);
        assertTrue(late < 3.0F);
        assertTrue(LostTalesChatMotion.message(0.95F).slideOffsetX < late);
        assertEquals(0.0F,
                LostTalesChatMotion.message(1.0F).slideOffsetX, EPSILON);
    }

    @Test
    public void motionInputsAreClamped() {
        assertEquals(LostTalesChatMotion.inputOffset(0.0F),
                LostTalesChatMotion.inputOffset(-2.0F), EPSILON);
        assertEquals(0.0F,
                LostTalesChatMotion.inputOffset(2.0F), EPSILON);
    }
}
