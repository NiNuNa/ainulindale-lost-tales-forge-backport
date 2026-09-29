package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public final class ThirdPersonCameraControllerTest {
    private static final CameraSmoothing SMOOTHING = new CameraSmoothing(
            10.0D, 10.0D, 10.0D, 10.0D, 10.0D, 10.0D, 10.0D);
    private static final CameraMotionEffectsSample STILL =
            new CameraMotionEffectsSample(false, true, false, false,
                    false, 0, 0.0D, 0.0D);
    private static final CameraMotionEffectsSettings NO_EFFECTS =
            new CameraMotionEffectsSettings(
                    0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

    @After
    public void resetController() {
        ThirdPersonCameraController.reset(true);
    }

    /** The first update of a world puts the camera at the pose at once. */
    private static CameraPose update(String contextKey, CameraPose pose,
                                     long updateNanos) {
        return ThirdPersonCameraController.update(contextKey, pose, SMOOTHING,
                CameraMotionProfile.NONE, 0.0D, Double.NaN, Double.NaN,
                STILL, NO_EFFECTS, updateNanos);
    }

    private static void activate(CameraPose pose) {
        update("test", pose, 0L);
    }

    @Test
    public void inactiveControllerPreservesVanillaHudYaw() {
        assertEquals(30.0F,
                ThirdPersonCameraController.resolveViewYaw(
                        20.0F, 40.0F, 0.5F),
                0.0F);
    }

    @Test
    public void activeControllerProvidesDecoupledHudYaw() {
        activate(new CameraPose(
                0.0D, 64.0D, 0.0D, -75.0D, 10.0D,
                3.5D, 0.6D, 0.2D, 0.0D));

        assertNotNull(ThirdPersonCameraController.getCurrentPose());
        assertEquals(-75.0F,
                ThirdPersonCameraController.resolveViewYaw(
                        20.0F, 40.0F, 0.5F),
                0.0F);
    }

    @Test
    public void resetRestoresInactivePassThroughState() {
        activate(new CameraPose(
                0.0D, 64.0D, 0.0D, 90.0D, 0.0D,
                3.5D, 0.6D, 0.2D, 0.0D));
        ThirdPersonCameraController.reset(true);

        assertNull(ThirdPersonCameraController.getCurrentPose());
        assertEquals(10.0F,
                ThirdPersonCameraController.resolveViewYaw(
                        0.0F, 20.0F, 0.5F),
                0.0F);
    }

    @Test
    public void profileChangesWithinAWorldInterpolateWithoutSnapping() {
        CameraPose standing = pose(3.5D, 0.6D);
        CameraPose sprinting = pose(4.2D, 0.5D);
        update("7@0", standing, 1000000000L);
        CameraPose transitioned = update("7@0", sprinting, 1050000000L);

        assertTrue(transitioned.getDistance() > standing.getDistance());
        assertTrue(transitioned.getDistance() < sprinting.getDistance());
    }

    @Test
    public void shoulderChoicePersistsAcrossDeactivationAndUsesConfiguredReset() {
        ThirdPersonCameraController.reset(false);
        assertEquals(-1.0D,
                ThirdPersonCameraController.getShoulderSign(), 0.0D);

        ThirdPersonCameraController.deactivate();
        assertEquals(-1.0D,
                ThirdPersonCameraController.getShoulderSign(), 0.0D);

        ThirdPersonCameraController.toggleShoulder();
        assertEquals(1.0D,
                ThirdPersonCameraController.getShoulderSign(), 0.0D);
    }

    @Test
    public void renderedFrameUsesCollisionScaledOffsetsAndClearsSafely() {
        activate(new CameraPose(
                0.0D, 64.0D, 0.0D, 0.0D, 0.0D,
                4.0D, 1.0D, 0.5D, 0.0D));
        ThirdPersonCameraController.prepareRenderFrame(
                10.0D, 65.0D, 20.0D,
                0.0D, 0.0D, 2.0D);

        // Half the distance scales the shoulder and the lift by half too.
        CameraRenderFrame frame =
                ThirdPersonCameraController.getRenderFrame();
        assertEquals(frame.getPivotZ() - 2.0D, frame.getCameraZ(), 0.000001D);
        assertEquals(frame.getPivotX() - 0.5D, frame.getCameraX(), 0.000001D);
        assertEquals(frame.getPivotY() + 0.25D, frame.getCameraY(), 0.000001D);

        ThirdPersonCameraController.deactivate();
        assertNull(ThirdPersonCameraController.getRenderFrame());
    }

    @Test
    public void modifierWheelZoomsInAndOutWithinConfiguredBounds() {
        activate(new CameraPose(
                0.0D, 64.0D, 0.0D, 0.0D, 0.0D,
                2.65D, 0.6D, 0.2D, 0.0D));

        assertTrue(ThirdPersonCameraController.adjustZoom(
                120, 1.35D, 8.0D, 0.30D));
        assertEquals(2.90D,
                ThirdPersonCameraController.resolveZoomDistance(
                3.20D, 1.35D, 8.0D), 0.0000001D);

        assertTrue(ThirdPersonCameraController.adjustZoom(
                -120, 1.35D, 8.0D, 0.30D));
        assertEquals(3.20D,
                ThirdPersonCameraController.resolveZoomDistance(
                3.20D, 1.35D, 8.0D), 0.0000001D);
    }

    @Test
    public void manualZoomPreservesProfileRelativeFraming() {
        activate(new CameraPose(
                0.0D, 64.0D, 0.0D, 0.0D, 0.0D,
                2.65D, 0.6D, 0.2D, 0.0D));
        ThirdPersonCameraController.resolveZoomDistance(
                2.65D, 1.35D, 8.0D);
        ThirdPersonCameraController.adjustZoom(
                120, 1.35D, 8.0D, 0.30D);

        assertEquals(2.35D,
                ThirdPersonCameraController.resolveZoomDistance(
                2.65D, 1.35D, 8.0D), 0.0000001D);
        assertEquals(1.80D,
                ThirdPersonCameraController.resolveZoomDistance(
                2.10D, 1.35D, 8.0D), 0.0000001D);
        assertEquals(2.90D,
                ThirdPersonCameraController.resolveZoomDistance(
                3.20D, 1.35D, 8.0D), 0.0000001D);
    }

    @Test
    public void manualZoomSurvivesPerspectiveChangeButNotSessionReset() {
        activate(new CameraPose(
                0.0D, 64.0D, 0.0D, 0.0D, 0.0D,
                2.65D, 0.6D, 0.2D, 0.0D));
        ThirdPersonCameraController.adjustZoom(
                120, 1.35D, 8.0D, 0.30D);

        ThirdPersonCameraController.deactivate();
        assertEquals(2.90D,
                ThirdPersonCameraController.resolveZoomDistance(
                3.20D, 1.35D, 8.0D), 0.0000001D);

        ThirdPersonCameraController.reset(true);
        assertEquals(3.20D,
                ThirdPersonCameraController.resolveZoomDistance(
                3.20D, 1.35D, 8.0D), 0.0000001D);
    }

    private static CameraPose pose(double distance, double shoulder) {
        return new CameraPose(
                0.0D, 64.0D, 0.0D, 0.0D, 0.0D,
                distance, shoulder, 0.2D, 0.0D);
    }
}
