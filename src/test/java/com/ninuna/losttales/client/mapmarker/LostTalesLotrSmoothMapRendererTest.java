package com.ninuna.losttales.client.mapmarker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LostTalesLotrSmoothMapRendererTest {
    @Test
    public void clippedWorldEdgeRetainsFractionalScreenPosition() {
        LostTalesLotrSmoothMapRenderer.Clip clip =
                LostTalesLotrSmoothMapRenderer.calculateClip(
                        50.0F, 400.0F, 0.5F,
                        200, 100, 1000, 800,
                        0, 200, 0, 100);

        assertEquals(75.0D, clip.drawnXMin, 0.0001D);
        assertEquals(200.0D, clip.drawnXMax, 0.0001D);
        assertEquals(0.0D, clip.uMin, 0.0001D);
        assertEquals(0.25D, clip.uMax, 0.0001D);
    }

    @Test
    public void tinyScaleChangesMoveClippedEdgeContinuously() {
        LostTalesLotrSmoothMapRenderer.Clip first =
                LostTalesLotrSmoothMapRenderer.calculateClip(
                        50.0F, 400.0F, 0.500F,
                        200, 100, 1000, 800,
                        0, 200, 0, 100);
        LostTalesLotrSmoothMapRenderer.Clip second =
                LostTalesLotrSmoothMapRenderer.calculateClip(
                        50.0F, 400.0F, 0.501F,
                        200, 100, 1000, 800,
                        0, 200, 0, 100);

        double movement = Math.abs(
                second.drawnXMin - first.drawnXMin);
        assertTrue(movement > 0.0D);
        assertTrue(movement < 1.0D);
    }

    @Test
    public void translucentInfluencePassDoesNotEraseControlZones() {
        assertTrue(LostTalesLotrSmoothMapRenderer
                .shouldDrawOpaqueBackground(1.0F));
        org.junit.Assert.assertFalse(LostTalesLotrSmoothMapRenderer
                .shouldDrawOpaqueBackground(0.5F));
    }
}
