package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public final class ThirdPersonCameraControllerTest {
    @After
    public void resetController() {
        ThirdPersonCameraController.reset();
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
        ThirdPersonCameraController.activate(new CameraPose(
                0.0D, 64.0D, 0.0D, -75.0D, 10.0D,
                3.5D, 0.6D, 0.2D, 0.0D));

        assertTrue(ThirdPersonCameraController.isActive());
        assertEquals(-75.0F,
                ThirdPersonCameraController.resolveViewYaw(
                        20.0F, 40.0F, 0.5F),
                0.0F);
    }

    @Test
    public void resetRestoresInactivePassThroughState() {
        ThirdPersonCameraController.activate(new CameraPose(
                0.0D, 64.0D, 0.0D, 90.0D, 0.0D,
                3.5D, 0.6D, 0.2D, 0.0D));
        ThirdPersonCameraController.reset();

        assertFalse(ThirdPersonCameraController.isActive());
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
        CameraSmoothing smoothing = new CameraSmoothing(
                10.0D, 10.0D, 10.0D, 10.0D, 10.0D, 10.0D);

        ThirdPersonCameraController.update(
                "7@0", standing, smoothing, 1000000000L);
        CameraPose transitioned = ThirdPersonCameraController.update(
                "7@0", sprinting, smoothing, 1050000000L);

        assertTrue(transitioned.getDistance() > standing.getDistance());
        assertTrue(transitioned.getDistance() < sprinting.getDistance());
    }

    @Test
    public void shoulderChoicePersistsAcrossDeactivationAndUsesConfiguredReset() {
        ThirdPersonCameraController.reset(false);
        assertFalse(ThirdPersonCameraController.isRightShoulder());
        assertEquals(-1.0D,
                ThirdPersonCameraController.getShoulderSign(), 0.0D);

        ThirdPersonCameraController.deactivate();
        assertFalse(ThirdPersonCameraController.isRightShoulder());

        ThirdPersonCameraController.toggleShoulder();
        assertTrue(ThirdPersonCameraController.isRightShoulder());
    }

    @Test
    public void renderedFrameUsesCollisionScaledOffsetsAndClearsSafely() {
        ThirdPersonCameraController.activate(new CameraPose(
                0.0D, 64.0D, 0.0D, 0.0D, 0.0D,
                4.0D, 1.0D, 0.5D, 0.0D));
        ThirdPersonCameraController.recordRenderedFov(80.0D);
        ThirdPersonCameraController.recordRenderFrame(
                10.0D, 65.0D, 20.0D,
                0.0D, 0.0D, 2.0D);

        CameraRenderFrame frame =
                ThirdPersonCameraController.getRenderFrame();
        assertEquals(2.0D, frame.getDistance(), 0.0D);
        assertEquals(0.5D, frame.getShoulderOffset(), 0.0D);
        assertEquals(0.25D, frame.getVerticalOffset(), 0.0D);
        assertEquals(80.0D, frame.getVerticalFov(), 0.0D);

        ThirdPersonCameraController.deactivate();
        assertNull(ThirdPersonCameraController.getRenderFrame());
    }

    @Test
    public void modifierWheelZoomsInAndOutWithinConfiguredBounds() {
        ThirdPersonCameraController.activate(new CameraPose(
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
        ThirdPersonCameraController.activate(new CameraPose(
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
        ThirdPersonCameraController.activate(new CameraPose(
                0.0D, 64.0D, 0.0D, 0.0D, 0.0D,
                2.65D, 0.6D, 0.2D, 0.0D));
        ThirdPersonCameraController.adjustZoom(
                120, 1.35D, 8.0D, 0.30D);

        ThirdPersonCameraController.deactivate();
        assertEquals(2.90D,
                ThirdPersonCameraController.resolveZoomDistance(
                3.20D, 1.35D, 8.0D), 0.0000001D);

        ThirdPersonCameraController.reset();
        assertTrue(Double.isNaN(
                ThirdPersonCameraController.getManualZoomOffset()));
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
