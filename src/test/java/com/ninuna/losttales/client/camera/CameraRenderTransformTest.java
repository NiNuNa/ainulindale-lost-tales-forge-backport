package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class CameraRenderTransformTest {
    private static final double EPSILON = 0.0000001D;

    @Test
    public void combinesFollowSwayAndCollisionScaleConsistently() {
        CameraPose pose = new CameraPose(
                9.8D, 20.1D, 30.2D,
                0.0D, 0.0D, 4.0D,
                1.0D, 0.5D, 0.0D);
        CameraMotionOffset motion =
                new CameraMotionOffset(0.1D, 0.05D, 0.2D);

        CameraRenderTransform transform = CameraRenderTransform.resolve(
                10.0D, 20.0D, 30.0D,
                0.0D, 0.0D, 2.0D,
                pose, motion, 70.0D);

        assertEquals(-0.65D, transform.getTranslateX(), EPSILON);
        assertEquals(-0.325D, transform.getTranslateY(), EPSILON);
        assertEquals(0.2D, transform.getTranslateZ(), EPSILON);
        CameraRenderFrame frame = transform.getFrame();
        assertEquals(9.9D, frame.getPivotX(), EPSILON);
        assertEquals(20.05D, frame.getPivotY(), EPSILON);
        assertEquals(30.2D, frame.getPivotZ(), EPSILON);
        assertEquals(9.35D, frame.getCameraX(), EPSILON);
        assertEquals(20.325D, frame.getCameraY(), EPSILON);
        assertEquals(28.2D, frame.getCameraZ(), EPSILON);
    }

    @Test
    public void desiredOffsetsProjectWorldFollowIntoViewSpace() {
        CameraPose pose = new CameraPose(
                -0.2D, 0.1D, 0.2D,
                0.0D, 0.0D, 4.0D,
                1.0D, 0.5D, 0.0D);

        CameraRenderTransform.LocalOffsets offsets =
                CameraRenderTransform.resolveDesiredOffsets(
                        0.0D, 0.0D, 0.0D,
                        0.0D, 0.0D, pose,
                        new CameraMotionOffset(0.1D, 0.05D, 0.2D));

        assertEquals(1.3D, offsets.getSide(), EPSILON);
        assertEquals(0.65D, offsets.getVertical(), EPSILON);
        assertEquals(0.4D, offsets.getForward(), EPSILON);
    }
}
