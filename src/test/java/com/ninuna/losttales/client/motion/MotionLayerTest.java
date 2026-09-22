package com.ninuna.losttales.client.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A layer's shape over its window, and nothing outside it. */
public final class MotionLayerTest {
    /** Terraria's banner spring: alternating triangle lobes that decay. */
    @Test
    public void theSpringAlternatesAndDecays() {
        assertEquals(0.0F, MotionLayer.ringOut(0.0F, 3), 0.0F);
        assertEquals(0.0F, MotionLayer.ringOut(1.0F, 3), 0.0F);
        assertEquals(0.0F, MotionLayer.ringOut(1.5F, 3), 0.0F);
        assertEquals(0.0F, MotionLayer.ringOut(0.5F, 0), 0.0F);
        assertEquals(1.0F, MotionLayer.ringOut(1.0F / 6.0F, 3), 1.0E-5F);
        assertEquals(-2.0F / 3.0F, MotionLayer.ringOut(0.5F, 3), 1.0E-5F);
        assertEquals(1.0F / 3.0F, MotionLayer.ringOut(5.0F / 6.0F, 3), 1.0E-5F);
        assertEquals(0.0F, MotionLayer.ringOut(1.0F / 3.0F, 3), 1.0E-5F);
        for (int step = 0; step <= 200; step++) {
            float value = MotionLayer.ringOut(step / 200.0F, 3);
            assertTrue("bounded at " + step, Math.abs(value) <= 1.0F + 1.0E-5F);
        }
    }

    @Test
    public void aBumpGoesOutAndBackInsideItsWindow() {
        MotionLayer bump = MotionLayer.bump(2.0F, 0.2F, 0.6F);
        assertEquals(0.0F, bump.offset(0.1F), 0.0F);
        assertEquals(0.0F, bump.offset(0.2F), 0.0F);
        assertEquals(2.0F, bump.offset(0.4F), 1.0E-5F);
        assertEquals(0.0F, bump.offset(0.6F), 0.0F);
        assertEquals(0.0F, bump.offset(0.9F), 0.0F);
    }

    @Test
    public void aTravelCoversItsWindowOnItsCurve() {
        MotionLayer travel = MotionLayer.travel(MotionCurve.LINEAR, 0.5F, 1.0F);
        assertEquals(0.0F, travel.travelShare(0.25F), 0.0F);
        assertEquals(0.5F, travel.travelShare(0.75F), 1.0E-5F);
        assertEquals(1.0F, travel.travelShare(1.0F), 1.0E-5F);
    }

    @Test
    public void keysPassThroughTheirValuesAndReturnToNothing() {
        MotionLayer keys = MotionLayer.keys(new float[] {0.25F, 0.75F},
                new float[] {1.0F, -1.0F},
                new MotionCurve[] {MotionCurve.LINEAR, MotionCurve.LINEAR},
                MotionCurve.LINEAR, 0.0F, 1.0F);
        assertEquals(0.5F, keys.offset(0.125F), 1.0E-5F);
        assertEquals(1.0F, keys.offset(0.25F), 1.0E-5F);
        assertEquals(0.0F, keys.offset(0.5F), 1.0E-5F);
        assertEquals(-1.0F, keys.offset(0.75F), 1.0E-5F);
        assertEquals(-0.5F, keys.offset(0.875F), 1.0E-5F);
        assertEquals(0.0F, keys.offset(1.0F), 0.0F);
    }

    @Test
    public void aKeyCannotStandAtTheWindowsEnd() {
        MotionLayer keys = MotionLayer.keys(new float[] {1.0F},
                new float[] {3.0F}, new MotionCurve[] {MotionCurve.LINEAR},
                MotionCurve.LINEAR, 0.0F, 1.0F);
        assertTrue(keys.keyAt(0) < 1.0F);
        assertEquals(0.0F, keys.offset(1.0F), 0.0F);
    }
}
