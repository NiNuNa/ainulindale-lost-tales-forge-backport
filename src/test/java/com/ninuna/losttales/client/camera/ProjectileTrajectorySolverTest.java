package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public final class ProjectileTrajectorySolverTest {
    private static final double EPSILON = 0.0000001D;

    @Test
    public void simulationUsesProjectileTickOrderForDragAndGravity() {
        List<TargetingVector> points = ProjectileTrajectorySolver.predict(
                vector(0.0D, 0.0D, 0.0D),
                vector(1.0D, 0.0D, 0.0D),
                new ProjectileBallisticsProfile(1.0D, 0.1D, 0.5D),
                100.0D, 3, null);

        assertEquals(4, points.size());
        assertVector(points.get(1), 1.0D, 0.0D, 0.0D);
        assertVector(points.get(2), 1.5D, -0.1D, 0.0D);
        assertVector(points.get(3), 1.75D, -0.25D, 0.0D);
    }

    @Test
    public void firstCollisionTerminatesTheArc() {
        List<TargetingVector> points = ProjectileTrajectorySolver.predict(
                vector(0.0D, 0.0D, 0.0D),
                vector(1.0D, 0.0D, 0.0D),
                new ProjectileBallisticsProfile(1.0D, 0.0D, 1.0D),
                100.0D, 20,
                new ProjectileTrajectorySolver.CollisionResolver() {
                    @Override
                    public TargetingVector resolve(
                            TargetingVector start, TargetingVector end) {
                        return end.getX() >= 1.25D
                                ? vector(1.25D, 0.0D, 0.0D) : null;
                    }
                });

        assertEquals(3, points.size());
        assertVector(points.get(2), 1.25D, 0.0D, 0.0D);
    }

    @Test
    public void maximumDistanceClipsTheFinalSegment() {
        List<TargetingVector> points = ProjectileTrajectorySolver.predict(
                vector(0.0D, 0.0D, 0.0D),
                vector(1.0D, 0.0D, 0.0D),
                new ProjectileBallisticsProfile(3.0D, 0.0D, 1.0D),
                2.0D, 20, null);

        assertEquals(2, points.size());
        assertVector(points.get(1), 2.0D, 0.0D, 0.0D);
    }

    private static TargetingVector vector(
            double x, double y, double z) {
        return new TargetingVector(x, y, z);
    }

    private static void assertVector(
            TargetingVector actual, double x, double y, double z) {
        assertEquals(x, actual.getX(), EPSILON);
        assertEquals(y, actual.getY(), EPSILON);
        assertEquals(z, actual.getZ(), EPSILON);
    }
}
