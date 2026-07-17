package com.ninuna.losttales.client.render.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CapeMotionMathTest {

    @Test
    public void yawInterpolationUsesShortestPathAcrossWrapBoundary() {
        assertEquals(-180.0F, CapeMotionMath.interpolateDegrees(
                170.0F, -170.0F, 0.5F), 0.0001F);
        assertEquals(-180.0F, CapeMotionMath.interpolateDegrees(
                -170.0F, 170.0F, 0.5F), 0.0001F);
    }

    @Test
    public void yawInterpolationClampsPartialTicks() {
        assertEquals(20.0F, CapeMotionMath.interpolateDegrees(
                10.0F, 20.0F, 2.0F), 0.0001F);
        assertEquals(10.0F, CapeMotionMath.interpolateDegrees(
                10.0F, 20.0F, -1.0F), 0.0001F);
    }

    @Test
    public void exponentialDampingIsFrameRateIndependent() {
        float oneStep = CapeMotionMath.damp(
                0.0F, 60.0F, 10.0F, 0.05F);
        float twoSteps = CapeMotionMath.damp(
                CapeMotionMath.damp(
                        0.0F, 60.0F, 10.0F, 0.025F),
                60.0F, 10.0F, 0.025F);
        assertEquals(oneStep, twoSteps, 0.0001F);
    }

    @Test
    public void capeImpulseClampingIsSymmetric() {
        assertEquals(65.0F,
                CapeMotionMath.clamp(100.0F, -65.0F, 65.0F),
                0.0F);
        assertEquals(-65.0F,
                CapeMotionMath.clamp(-100.0F, -65.0F, 65.0F),
                0.0F);
    }
}
