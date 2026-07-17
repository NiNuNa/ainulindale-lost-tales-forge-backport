package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ThirdPersonCameraTargetingSolverTest {
    private static final double EPSILON = 0.0000001D;

    @Test
    public void eyeRayConvergesTowardOffsetCameraIntentAtExactReach() {
        TargetingVector eye = new TargetingVector(0.0D, 0.0D, 0.0D);
        TargetingVector desired = new TargetingVector(1.0D, 0.0D, 4.0D);

        TargetingVector endpoint =
                ThirdPersonCameraTargetingSolver.resolveEyeEndpoint(
                eye, desired,
                new TargetingVector(0.0D, 0.0D, 1.0D), 3.0D);

        assertEquals(9.0D, eye.distanceSquared(endpoint), EPSILON);
        assertTrue(endpoint.getX() > 0.0D);
        assertTrue(endpoint.getZ() > endpoint.getX());
    }

    @Test
    public void zeroLengthIntentFallsBackToCameraForward() {
        TargetingVector eye = new TargetingVector(2.0D, 3.0D, 4.0D);

        TargetingVector endpoint =
                ThirdPersonCameraTargetingSolver.resolveEyeEndpoint(
                eye, eye,
                new TargetingVector(0.0D, 0.0D, 2.0D), 4.5D);

        assertEquals(2.0D, endpoint.getX(), EPSILON);
        assertEquals(3.0D, endpoint.getY(), EPSILON);
        assertEquals(8.5D, endpoint.getZ(), EPSILON);
    }

    @Test
    public void cameraCandidatesBehindTheEyeAreRejected() {
        TargetingVector eye = new TargetingVector(0.0D, 0.0D, 0.0D);
        TargetingVector forward = new TargetingVector(0.0D, 0.0D, 1.0D);

        assertTrue(ThirdPersonCameraTargetingSolver.isInFront(
                eye, new TargetingVector(0.0D, 0.0D, 2.0D), forward));
        assertFalse(ThirdPersonCameraTargetingSolver.isInFront(
                eye, new TargetingVector(0.0D, 0.0D, -0.1D), forward));
    }
}
