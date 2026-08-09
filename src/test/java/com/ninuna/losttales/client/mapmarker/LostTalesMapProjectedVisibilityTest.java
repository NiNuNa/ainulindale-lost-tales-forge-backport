package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesMapProjectedVisibilityTest {
    @Test
    public void projectedSizeFadesContinuouslyAndOnlyOnce() {
        float previous = 0.0F;
        boolean partial = false;
        for (float width = 0.0F; width <= 8.0F; width += 0.02F) {
            float alpha = LostTalesMapProjectedVisibility.alpha(width, 2.0F);
            assertTrue(alpha >= previous - 0.0001F);
            assertTrue(alpha >= 0.0F && alpha <= 1.0F);
            partial |= alpha > 0.0F && alpha < 1.0F;
            previous = alpha;
        }
        assertTrue(partial);
        assertEquals(0.0F,
                LostTalesMapProjectedVisibility.alpha(2.0F, 2.0F), 0.0F);
        assertEquals(1.0F,
                LostTalesMapProjectedVisibility.alpha(4.5F, 2.0F), 0.0F);
    }

    @Test
    public void malformedSizesFailClosed() {
        assertEquals(0.0F,
                LostTalesMapProjectedVisibility.alpha(-1.0F, 2.0F), 0.0F);
        assertEquals(0.0F,
                LostTalesMapProjectedVisibility.alpha(3.0F, 0.0F), 0.0F);
    }
}
