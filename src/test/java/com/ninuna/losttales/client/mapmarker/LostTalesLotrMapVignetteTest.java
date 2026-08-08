package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesLotrMapVignetteTest {
    @Test
    public void theMiddleOfTheMapIsLeftClear() {
        assertEquals(0.0F,
                LostTalesLotrMapVignette.shadeAt(0.0F, 0.0F), 0.0F);
        assertEquals(0.0F,
                LostTalesLotrMapVignette.shadeAt(0.3F, 0.2F), 0.0F);
    }

    /**
     * What makes it an oval rather than a frame: a corner is the furthest
     * point from the middle, so it has to be the darkest, and the middle of
     * a side has to be lighter than the corner beside it.
     */
    @Test
    public void theShadeIsAnOvalRatherThanAFrame() {
        float corner = LostTalesLotrMapVignette.shadeAt(1.0F, 1.0F);
        float sideMiddle = LostTalesLotrMapVignette.shadeAt(1.0F, 0.0F);
        float topMiddle = LostTalesLotrMapVignette.shadeAt(0.0F, 1.0F);

        assertTrue("a corner must be darker than the side beside it",
                corner > sideMiddle);
        assertTrue("a corner must be darker than the top",
                corner > topMiddle);
        // The two sides are the same distance out, so an oval shades them
        // alike however the screen is shaped.
        assertEquals(sideMiddle, topMiddle, 0.0001F);
    }

    /** Nothing may band: the whole point is that it reads as smooth. */
    @Test
    public void theShadeOnlyEverDeepensOutwards() {
        float previous = -1.0F;
        for (int step = 0; step <= 400; step++) {
            float radius = step / 200.0F;
            float shade = LostTalesLotrMapVignette.shadeAt(radius, 0.0F);
            assertTrue("the shade lightened further out at " + radius,
                    shade >= previous - 0.0001F);
            assertTrue("the shade left its range at " + radius,
                    shade >= 0.0F
                            && shade <= LostTalesLotrMapVignette.EDGE_ALPHA
                                    + 0.0001F);
            if (previous >= 0.0F) {
                assertTrue("the shade stepped at " + radius,
                        Math.abs(shade - previous) < 0.01F);
            }
            previous = shade;
        }
    }

    /** Symmetric in both directions, or the oval sits off centre. */
    @Test
    public void theShadeIsTheSameInEveryDirection() {
        for (float x = -1.4F; x <= 1.4F; x += 0.2F) {
            for (float y = -1.4F; y <= 1.4F; y += 0.2F) {
                float shade = LostTalesLotrMapVignette.shadeAt(x, y);
                assertEquals(shade,
                        LostTalesLotrMapVignette.shadeAt(-x, y), 0.0001F);
                assertEquals(shade,
                        LostTalesLotrMapVignette.shadeAt(x, -y), 0.0001F);
            }
        }
    }
}
