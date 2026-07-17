package com.ninuna.losttales.client.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ThirdPersonTargetLockMathTest {
    private static final double EPSILON = 0.000001D;

    @Test
    public void targetAnglesUseMinecraftYawAndPitchConventions() {
        TargetingVector origin = vector(0.0D, 0.0D, 0.0D);

        assertEquals(0.0F, ThirdPersonTargetLockMath.resolveYaw(
                origin, vector(0.0D, 0.0D, 1.0D)), 0.0F);
        assertEquals(-90.0F, ThirdPersonTargetLockMath.resolveYaw(
                origin, vector(1.0D, 0.0D, 0.0D)), 0.0F);
        assertEquals(-45.0F, ThirdPersonTargetLockMath.resolvePitch(
                origin, vector(0.0D, 1.0D, 1.0D)), 0.0001F);
    }

    @Test
    public void candidateAngleIsMeasuredFromCameraDirection() {
        TargetingVector origin = vector(0.0D, 0.0D, 0.0D);
        TargetingVector forward = ThirdPersonTargetLockMath
                .directionFromRotation(0.0F, 0.0F);

        assertEquals(0.0D, ThirdPersonTargetLockMath.angleDegrees(
                origin, forward, vector(0.0D, 0.0D, 5.0D)), EPSILON);
        assertEquals(90.0D, ThirdPersonTargetLockMath.angleDegrees(
                origin, forward, vector(5.0D, 0.0D, 0.0D)), EPSILON);
    }

    private static TargetingVector vector(
            double x, double y, double z) {
        return new TargetingVector(x, y, z);
    }
}
