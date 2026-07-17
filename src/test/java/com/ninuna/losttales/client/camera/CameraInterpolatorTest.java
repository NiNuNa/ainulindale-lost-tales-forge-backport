package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CameraInterpolatorTest {
    private static final double EPSILON = 0.0000001D;

    @Test
    public void convergenceIsIndependentOfFrameRate() {
        CameraPose atTwentyFps = simulate(20, 1.0D);
        CameraPose atOneHundredFortyFourFps = simulate(144, 1.0D);

        assertPoseEquals(atTwentyFps, atOneHundredFortyFourFps, EPSILON);
    }

    @Test
    public void yawUsesShortestWrappedArc() {
        CameraPose current = pose(170.0D, 0.0D);
        CameraPose target = pose(-170.0D, 0.0D);
        CameraSmoothing smoothing = allRates(2.0D);

        CameraPose result = CameraInterpolator.interpolate(
                current, target, smoothing, 0.1D);

        assertTrue("yaw should move forward across 180 degrees",
                result.getYaw() > 170.0D || result.getYaw() < -170.0D);
    }

    @Test
    public void pauseSizedDeltaIsCapped() {
        double alpha = CameraMath.exponentialAlpha(2.0D, 10.0D);
        assertEquals(
                1.0D - Math.exp(-2.0D
                        * CameraMath.MAX_FRAME_DELTA_SECONDS),
                alpha, EPSILON);
        assertTrue(alpha < 1.0D);
    }

    @Test
    public void zeroOrInvalidDeltaKeepsCurrentPose() {
        CameraPose current = pose(0.0D, 0.0D);
        CameraPose target = pose(90.0D, 10.0D);
        CameraSmoothing smoothing = allRates(5.0D);

        assertSame(current, CameraInterpolator.interpolate(
                current, target, smoothing, 0.0D));
        assertSame(current, CameraInterpolator.interpolate(
                current, target, smoothing, Double.NaN));
    }

    @Test
    public void verticalWorldFollowHasAnIndependentResponseRate() {
        CameraPose current = new CameraPose(
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                4.0D, 0.0D, 0.0D, 0.0D);
        CameraPose target = new CameraPose(
                10.0D, 10.0D, 0.0D, 0.0D, 0.0D,
                4.0D, 0.0D, 0.0D, 0.0D);
        CameraSmoothing smoothing = new CameraSmoothing(
                10.0D, 2.0D, 10.0D, 10.0D,
                10.0D, 10.0D, 10.0D);

        CameraPose result = CameraInterpolator.interpolate(
                current, target, smoothing, 0.1D);

        assertTrue(result.getPositionX() > result.getPositionY());
    }

    @Test(expected = IllegalArgumentException.class)
    public void poseRejectsNonFiniteInput() {
        new CameraPose(
                Double.NaN, 0.0D, 0.0D, 0.0D, 0.0D,
                3.0D, 0.5D, 0.0D, 0.0D);
    }

    @Test(expected = IllegalArgumentException.class)
    public void smoothingRejectsNegativeRates() {
        new CameraSmoothing(-1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D);
    }

    private static CameraPose simulate(int framesPerSecond, double seconds) {
        CameraPose current = new CameraPose(
                0.0D, 64.0D, 0.0D, 170.0D, -10.0D,
                4.0D, 0.0D, 0.0D, 0.0D);
        CameraPose target = new CameraPose(
                12.0D, 68.0D, -8.0D, -150.0D, 25.0D,
                2.8D, 0.8D, 0.3D, -2.0D);
        CameraSmoothing smoothing = new CameraSmoothing(
                8.0D, 15.0D, 10.0D, 12.0D, 9.0D, 6.0D);
        int frames = (int)Math.round(framesPerSecond * seconds);
        double delta = seconds / frames;
        for (int frame = 0; frame < frames; frame++) {
            current = CameraInterpolator.interpolate(
                    current, target, smoothing, delta);
        }
        return current;
    }

    private static CameraPose pose(double yaw, double pitch) {
        return new CameraPose(
                0.0D, 0.0D, 0.0D, yaw, pitch,
                4.0D, 0.0D, 0.0D, 0.0D);
    }

    private static CameraSmoothing allRates(double rate) {
        return new CameraSmoothing(rate, rate, rate, rate, rate, rate);
    }

    private static void assertPoseEquals(
            CameraPose expected, CameraPose actual, double epsilon) {
        assertEquals(expected.getPositionX(), actual.getPositionX(), epsilon);
        assertEquals(expected.getPositionY(), actual.getPositionY(), epsilon);
        assertEquals(expected.getPositionZ(), actual.getPositionZ(), epsilon);
        assertEquals(expected.getYaw(), actual.getYaw(), epsilon);
        assertEquals(expected.getPitch(), actual.getPitch(), epsilon);
        assertEquals(expected.getDistance(), actual.getDistance(), epsilon);
        assertEquals(expected.getShoulderOffset(),
                actual.getShoulderOffset(), epsilon);
        assertEquals(expected.getVerticalOffset(),
                actual.getVerticalOffset(), epsilon);
        assertEquals(expected.getFovOffset(), actual.getFovOffset(), epsilon);
    }
}
