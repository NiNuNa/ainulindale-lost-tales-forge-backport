package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CameraCollisionResolverTest {
    private static final double EPSILON = 0.0000001D;

    @Test
    public void unobstructedSweepKeepsDesiredDistance() {
        double result = CameraCollisionResolver.resolveAllowedDistance(
                noHit(), 0.0D, 64.0D, 0.0D,
                0.0D, 0.0D, 4.0D, 0.6D, 0.2D, 0.12D);

        assertEquals(4.0D, result, EPSILON);
    }

    @Test
    public void nearestSweptRayPullsCameraIn() {
        CameraCollisionResolver.Raycaster raycaster =
                new CameraCollisionResolver.Raycaster() {
                    private int ray;

                    @Override
                    public double trace(
                            double startX, double startY, double startZ,
                            double endX, double endY, double endZ) {
                        return ray++ == 3 ? 2.12D : -1.0D;
                    }
                };

        double result = CameraCollisionResolver.resolveAllowedDistance(
                raycaster, 0.0D, 64.0D, 0.0D,
                0.0D, 0.0D, 4.0D, 0.6D, 0.2D, 0.12D);

        assertTrue(result > 1.8D);
        assertTrue(result < 2.0D);
    }

    @Test
    public void collisionStatePullsInImmediatelyAndReleasesSmoothly() {
        CameraCollisionState state = new CameraCollisionState();
        assertEquals(1.0D, state.update(
                4.0D, 1.0D, 5.0D, 0.05D), EPSILON);

        double releasing = state.update(
                4.0D, 4.0D, 5.0D, 0.05D);
        assertTrue(releasing > 1.0D);
        assertTrue(releasing < 4.0D);
        assertEquals(0.5D, state.update(
                4.0D, 0.5D, 5.0D, 0.05D), EPSILON);
    }

    @Test
    public void shoulderAndForwardOffsetsUseRenderedCameraAxes() {
        final double[] end = new double[3];
        CameraCollisionResolver.resolveAllowedDistance(
                new CameraCollisionResolver.Raycaster() {
                    @Override
                    public double trace(
                            double startX, double startY, double startZ,
                            double endX, double endY, double endZ) {
                        end[0] = endX;
                        end[1] = endY;
                        end[2] = endZ;
                        return -1.0D;
                    }
                },
                0.0D, 64.0D, 0.0D,
                0.0D, 0.0D, 4.0D,
                1.0D, 0.0D, 0.5D, 0.0D);

        assertEquals(-1.0D, end[0], EPSILON);
        assertEquals(64.0D, end[1], EPSILON);
        assertEquals(-3.5D, end[2], EPSILON);
    }

    private static CameraCollisionResolver.Raycaster noHit() {
        return new CameraCollisionResolver.Raycaster() {
            @Override
            public double trace(
                    double startX, double startY, double startZ,
                    double endX, double endY, double endZ) {
                return -1.0D;
            }
        };
    }
}
