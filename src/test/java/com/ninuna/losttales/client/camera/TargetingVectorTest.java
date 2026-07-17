package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TargetingVectorTest {
    private static final double EPSILON = 0.0000001D;

    @Test
    public void vectorOperationsRemainImmutableAndNormalized() {
        TargetingVector source = new TargetingVector(3.0D, 0.0D, 4.0D);
        TargetingVector normalized = source.normalizeOr(
                new TargetingVector(0.0D, 0.0D, 1.0D));

        assertEquals(3.0D, source.getX(), EPSILON);
        assertEquals(4.0D, source.getZ(), EPSILON);
        assertEquals(0.6D, normalized.getX(), EPSILON);
        assertEquals(0.8D, normalized.getZ(), EPSILON);
        assertEquals(1.0D, normalized.lengthSquared(), EPSILON);
    }

    @Test
    public void zeroVectorUsesNormalizedFallback() {
        TargetingVector normalized = new TargetingVector(
                0.0D, 0.0D, 0.0D).normalizeOr(
                new TargetingVector(0.0D, 2.0D, 0.0D));

        assertEquals(0.0D, normalized.getX(), EPSILON);
        assertEquals(1.0D, normalized.getY(), EPSILON);
        assertEquals(0.0D, normalized.getZ(), EPSILON);
    }
}
