package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class CameraRenderFrameTest {
    private static final double EPSILON = 0.0000001D;

    @Test
    public void computesRenderedCameraOriginFromLocalOffsets() {
        CameraRenderFrame frame = new CameraRenderFrame(
                10.0D, 20.0D, 30.0D,
                0.0D, 0.0D, 4.0D,
                1.0D, 0.5D, 70.0D);

        assertEquals(9.0D, frame.getCameraX(), EPSILON);
        assertEquals(20.5D, frame.getCameraY(), EPSILON);
        assertEquals(26.0D, frame.getCameraZ(), EPSILON);
    }
}
