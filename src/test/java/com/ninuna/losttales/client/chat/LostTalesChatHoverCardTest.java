package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesChatHoverCardTest {
    @Test
    public void hitBoundsHandleScaledAnimatedGeometry() {
        assertTrue(LostTalesChatHoverCard.contains(
                31.0F, 42.0F, 20.0F, 40.0F, 44.0F, 49.0F));
        assertFalse(LostTalesChatHoverCard.contains(
                44.0F, 42.0F, 20.0F, 40.0F, 44.0F, 49.0F));
    }

    @Test
    public void cardFlipsAndClampsAtScreenEdges() {
        assertEquals(38,
                LostTalesChatHoverCard.cardX(170, 120, 200));
        assertEquals(52,
                LostTalesChatHoverCard.cardY(110, 50, 130));
        assertEquals(4,
                LostTalesChatHoverCard.cardX(1, 120, 100));
        assertEquals(4,
                LostTalesChatHoverCard.cardY(1, 80, 70));
    }
}
