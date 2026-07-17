package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CameraMotionStateTest {
    private static final double EPSILON = 0.0000001D;

    @Test
    public void followLagIsBoundedSeparatelyHorizontallyAndVertically() {
        CameraPose target = pose(10.0D, 64.0D, -5.0D, 0.0D);
        CameraPose current = pose(7.0D, 62.0D, -1.0D, 0.0D);
        CameraMotionProfile profile = profile();

        CameraPose constrained = CameraMotionState.constrainFollow(
                current, target, profile, 1.0D);

        double horizontal = Math.sqrt(
                square(constrained.getPositionX() - 10.0D)
                        + square(constrained.getPositionZ() + 5.0D));
        assertEquals(0.5D, horizontal, EPSILON);
        assertEquals(63.75D, constrained.getPositionY(), EPSILON);
    }

    @Test
    public void zeroMultiplierRemovesAllFollowLag() {
        CameraPose target = pose(10.0D, 64.0D, -5.0D, 0.0D);
        CameraPose current = pose(9.5D, 63.8D, -4.7D, 0.0D);

        CameraPose constrained = CameraMotionState.constrainFollow(
                current, target, profile(), 0.0D);

        assertEquals(target.getPositionX(),
                constrained.getPositionX(), EPSILON);
        assertEquals(target.getPositionY(),
                constrained.getPositionY(), EPSILON);
        assertEquals(target.getPositionZ(),
                constrained.getPositionZ(), EPSILON);
    }

    @Test
    public void movementAndTurnProduceBoundedSmoothOffsets() {
        CameraMotionState state = new CameraMotionState();
        CameraMotionProfile profile = profile();
        state.reset(pose(0.0D, 64.0D, 0.0D, 0.0D));

        CameraMotionOffset offset = state.update(
                pose(0.20D, 64.0D, 0.0D, 8.0D),
                profile, 1.0D, 0.05D);

        assertTrue(Math.abs(offset.getSide())
                <= profile.getSideSway() + profile.getTurnSway()
                + profile.getIdleSideSway());
        assertTrue(Math.abs(offset.getVertical())
                <= profile.getVerticalSway()
                + profile.getLookPitchSway()
                + profile.getIdleVerticalSway());
        assertTrue(Math.abs(offset.getForward())
                <= profile.getForwardSway()
                + profile.getLookForwardSway()
                + profile.getIdleForwardSway());
        assertTrue(Math.abs(offset.getSide()) > 0.0D);
    }

    @Test
    public void lookSpeedAndDirectionCreateSmoothThreeAxisMotion() {
        CameraMotionProfile profile = profile();
        CameraMotionState slow = new CameraMotionState();
        CameraMotionState fast = new CameraMotionState();
        CameraPose initial = pose(
                0.0D, 64.0D, 0.0D, 0.0D, 0.0D);
        slow.reset(initial);
        fast.reset(initial);

        CameraMotionOffset slowOffset = slow.update(
                pose(0.0D, 64.0D, 0.0D, 3.0D, 2.0D),
                profile, 1.0D, 0.05D, 0.0D, 0.0D);
        CameraMotionOffset fastOffset = fast.update(
                pose(0.0D, 64.0D, 0.0D, 12.0D, 8.0D),
                profile, 1.0D, 0.05D, 0.0D, 0.0D);

        assertTrue(slowOffset.getSide() < 0.0D);
        assertTrue(slowOffset.getVertical() < 0.0D);
        assertTrue(slowOffset.getForward() < 0.0D);
        assertTrue(magnitude(fastOffset) > magnitude(slowOffset));

        CameraMotionState reverse = new CameraMotionState();
        reverse.reset(initial);
        CameraMotionOffset reverseOffset = reverse.update(
                pose(0.0D, 64.0D, 0.0D, -12.0D, -8.0D),
                profile, 1.0D, 0.05D, 0.0D, 0.0D);
        assertTrue(reverseOffset.getSide() > 0.0D);
        assertTrue(reverseOffset.getVertical() > 0.0D);
        assertTrue(reverseOffset.getForward() < 0.0D);
    }

    @Test
    public void walkingSwayUsesTheRenderedLimbAnimationPhase() {
        CameraMotionProfile profile = new CameraMotionProfile(
                0.5D, 0.25D,
                0.04D, 0.02D, 0.01D, 0.0D,
                0.8D, 20.0D);
        CameraPose stationary = pose(0.0D, 64.0D, 0.0D, 0.0D);
        CameraMotionState leftStep = new CameraMotionState();
        CameraMotionState rightStep = new CameraMotionState();
        leftStep.reset(stationary);
        rightStep.reset(stationary);

        CameraMotionOffset left = leftStep.update(
                stationary, profile, 1.0D, 0.05D,
                Math.PI * 0.5D, 1.0D);
        CameraMotionOffset right = rightStep.update(
                stationary, profile, 1.0D, 0.05D,
                Math.PI * 1.5D, 1.0D);

        assertTrue(left.getSide() > 0.0D);
        assertTrue(right.getSide() < 0.0D);
        assertEquals(Math.abs(left.getSide()),
                Math.abs(right.getSide()), EPSILON);
    }

    @Test
    public void stationaryCameraRetainsSubtleAmbientMotion() {
        CameraMotionState state = new CameraMotionState();
        CameraPose stationary = pose(0.0D, 64.0D, 0.0D, 0.0D);
        CameraMotionProfile profile = idleOnlyProfile();
        state.reset(stationary);

        CameraMotionOffset offset = CameraMotionOffset.ZERO;
        for (int i = 0; i < 30; i++) {
            offset = state.update(
                    stationary, profile, 1.0D, 0.05D,
                    0.0D, 0.0D);
        }

        assertTrue(magnitude(offset) > 0.005D);
    }

    @Test
    public void mouseMovementQuicklySuppressesAmbientMotion() {
        CameraMotionState state = new CameraMotionState();
        CameraMotionProfile profile = idleOnlyProfile();
        CameraPose stationary = pose(0.0D, 64.0D, 0.0D, 0.0D);
        state.reset(stationary);
        CameraMotionOffset before = CameraMotionOffset.ZERO;
        for (int i = 0; i < 30; i++) {
            before = state.update(
                    stationary, profile, 1.0D, 0.05D,
                    0.0D, 0.0D);
        }

        CameraMotionOffset after = before;
        for (int i = 1; i <= 20; i++) {
            after = state.update(
                    pose(0.0D, 64.0D, 0.0D, i * 3.0D),
                    profile, 1.0D, 0.05D,
                    0.0D, 0.0D);
        }

        assertTrue(magnitude(after) < magnitude(before) * 0.10D);
    }

    @Test
    public void walkingQuicklySuppressesAmbientMotion() {
        CameraMotionState state = new CameraMotionState();
        CameraMotionProfile profile = idleOnlyProfile();
        CameraPose stationary = pose(0.0D, 64.0D, 0.0D, 0.0D);
        state.reset(stationary);
        CameraMotionOffset before = CameraMotionOffset.ZERO;
        for (int i = 0; i < 30; i++) {
            before = state.update(
                    stationary, profile, 1.0D, 0.05D,
                    0.0D, 0.0D);
        }

        CameraMotionOffset after = before;
        for (int i = 1; i <= 20; i++) {
            after = state.update(
                    stationary, profile, 1.0D, 0.05D,
                    i * 0.3D, 1.0D);
        }

        assertTrue(magnitude(after) < magnitude(before) * 0.10D);
    }

    @Test
    public void teleportClearsProceduralMotionInsteadOfSweeping() {
        CameraMotionState state = new CameraMotionState();
        state.reset(pose(0.0D, 64.0D, 0.0D, 0.0D));
        state.update(pose(0.2D, 64.0D, 0.0D, 4.0D),
                profile(), 1.0D, 0.05D);

        CameraMotionOffset offset = state.update(
                pose(100.0D, 70.0D, 100.0D, 90.0D),
                profile(), 1.0D, 0.05D);

        assertEquals(0.0D, offset.getSide(), EPSILON);
        assertEquals(0.0D, offset.getVertical(), EPSILON);
        assertEquals(0.0D, offset.getForward(), EPSILON);
    }

    private static CameraMotionProfile profile() {
        return new CameraMotionProfile(
                0.5D, 0.25D,
                0.04D, 0.02D, 0.01D,
                0.08D, 0.05D, 0.06D,
                8.0D, 240.0D, 0.8D, 7.0D,
                0.01D, 0.006D, 0.004D, 0.075D);
    }

    private static CameraMotionProfile idleOnlyProfile() {
        return new CameraMotionProfile(
                0.5D, 0.25D,
                0.0D, 0.0D, 0.0D, 0.0D,
                0.8D, 10.0D,
                0.035D, 0.022D, 0.014D, 0.11D);
    }

    private static CameraPose pose(
            double x, double y, double z, double yaw) {
        return pose(x, y, z, yaw, 0.0D);
    }

    private static CameraPose pose(
            double x, double y, double z,
            double yaw, double pitch) {
        return new CameraPose(
                x, y, z, yaw, pitch,
                3.0D, 0.6D, 0.2D, 0.0D);
    }

    private static double square(double value) {
        return value * value;
    }

    private static double magnitude(CameraMotionOffset offset) {
        return Math.sqrt(
                square(offset.getSide())
                        + square(offset.getVertical())
                        + square(offset.getForward()));
    }
}
