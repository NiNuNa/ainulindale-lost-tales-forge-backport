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
        assertEquals(1.0F, settled.scaleX, EPSILON);
        assertEquals(1.0F, settled.scaleY, EPSILON);
        assertEquals(0.0F, settled.headLagY, EPSILON);
    }

    @Test
    public void entryUsesRestrainedSquashStretchAndPortraitOverlap() {
        LostTalesChatMotion.MessageSample entering =
                LostTalesChatMotion.message(0.0F);

        assertTrue(entering.stackOffsetY > 0.0F);
        assertEquals(0.0F, entering.opacity, EPSILON);
        assertTrue(entering.scaleX > 1.0F);
        assertTrue(entering.scaleY < 1.0F);
        assertEquals(0.0F, entering.headLagY, EPSILON);
        assertTrue(LostTalesChatMotion.message(0.18F).headLagY > 0.0F);
    }

    @Test
    public void motionInputsAreClamped() {
        assertEquals(LostTalesChatMotion.inputOffset(0.0F),
                LostTalesChatMotion.inputOffset(-2.0F), EPSILON);
        assertEquals(0.0F,
                LostTalesChatMotion.inputOffset(2.0F), EPSILON);
        assertEquals(1.0F,
                LostTalesChatMotion.menuProgress(2.0F), EPSILON);
    }
}
