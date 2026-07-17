package com.ninuna.losttales.client.camera;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ProjectileTrajectorySmootherTest {
    private static final double EPSILON = 0.0000001D;

    @Test
    public void resamplingPreservesReleaseKnotsAndCollisionEndpoint() {
        TargetingVector release = vector(0.0D, 0.0D, 0.0D);
        TargetingVector firstTick = vector(3.0D, 0.0D, 0.0D);
        TargetingVector collision = vector(5.0D, -0.2D, 0.0D);

        List<TargetingVector> smoothed =
                ProjectileTrajectorySmoother.resample(
                Arrays.asList(release, firstTick, collision),
                4, 0.4D);

        assertEquals(9, smoothed.size());
        assertVector(smoothed.get(0), release);
        assertVector(smoothed.get(4), firstTick);
        assertVector(smoothed.get(8), collision);
    }

    @Test
    public void resamplingRoundsAFormerRightAngle() {
        List<TargetingVector> smoothed =
                ProjectileTrajectorySmoother.resample(Arrays.asList(
                vector(0.0D, 0.0D, 0.0D),
                vector(1.0D, 0.0D, 0.0D),
                vector(1.0D, -1.0D, 0.0D)), 4, 0.4D);

        TargetingVector before = smoothed.get(3);
        TargetingVector corner = smoothed.get(4);
        TargetingVector after = smoothed.get(5);
        TargetingVector incoming = corner.subtract(before);
        TargetingVector outgoing = after.subtract(corner);
        assertTrue(incoming.dot(outgoing) > 0.0D);
    }

    @Test(expected = IllegalArgumentException.class)
    public void excessiveSamplingIsRejected() {
        ProjectileTrajectorySmoother.resample(Arrays.asList(
                vector(0.0D, 0.0D, 0.0D),
                vector(1.0D, 0.0D, 0.0D)), 17, 0.4D);
    }

    private static TargetingVector vector(
            double x, double y, double z) {
        return new TargetingVector(x, y, z);
    }

    private static void assertVector(
            TargetingVector actual, TargetingVector expected) {
        assertEquals(expected.getX(), actual.getX(), EPSILON);
        assertEquals(expected.getY(), actual.getY(), EPSILON);
        assertEquals(expected.getZ(), actual.getZ(), EPSILON);
    }
}
