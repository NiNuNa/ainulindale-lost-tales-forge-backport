package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ThirdPersonTargetingSolverTest {
    private static final double EPSILON = 0.0000001D;

    @Test
    public void vanillaEntityReachRemainsCappedWithoutExtendedReach() {
        assertEquals(3.0D, ThirdPersonTargetingSolver.resolveEntityReach(
                5.0D, false), EPSILON);
    }

    @Test
    public void creativeExtendedReachUsesVanillaSixBlocks() {
        assertEquals(6.0D, ThirdPersonTargetingSolver.resolveEntityReach(
                5.0D, true), EPSILON);
    }

    @Test
    public void invalidReachFallsBackSafely() {
        assertEquals(3.0D, ThirdPersonTargetingSolver.sanitizeReach(
                Double.NaN), EPSILON);
        assertEquals(3.0D, ThirdPersonTargetingSolver.sanitizeReach(
                -1.0D), EPSILON);
    }
}
