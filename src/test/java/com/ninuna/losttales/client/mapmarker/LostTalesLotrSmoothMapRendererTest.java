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

    @Test
    public void proceduralNoiseUsesNativeMapCoordinatesAndRepeats() {
        double farUSpan = LostTalesLotrSmoothMapRenderer
                .noiseTextureCoordinate(200.0D / 1.0D);
        double closeUSpan = LostTalesLotrSmoothMapRenderer
                .noiseTextureCoordinate(200.0D / 2.0D);
        double farVSpan = LostTalesLotrSmoothMapRenderer
                .noiseTextureCoordinate(100.0D / 1.0D);
        double closeVSpan = LostTalesLotrSmoothMapRenderer
                .noiseTextureCoordinate(100.0D / 2.0D);

        assertEquals(farUSpan * 0.5D, closeUSpan, 0.0001D);
        assertEquals(farVSpan * 0.5D, closeVSpan, 0.0001D);
        assertTrue("the noise is still stretched once across the whole map",
                LostTalesLotrSmoothMapRenderer
                        .noiseTextureCoordinate(1000.0D) > 1.0D);
        assertTrue("noise cannot continue before the map-image edge",
                LostTalesLotrSmoothMapRenderer
                        .noiseTextureCoordinate(-100.0D) < 0.0D);
    }

    @Test
    public void proceduralNoiseIsDeterministicSubtleAndCloudy() {
        int size = LostTalesLotrSmoothMapRenderer.NOISE_TILE_SIZE;
        int[] first = new int[size * size];
        int[] second = new int[size * size];
        LostTalesLotrSmoothMapRenderer.fillProceduralNoise(
                first, size, size);
        LostTalesLotrSmoothMapRenderer.fillProceduralNoise(
                second, size, size);

        long sum = 0L;
        double squareSum = 0.0D;
        int minimum = 255;
        int maximum = 0;
        int changedNeighbors = 0;
        for (int index = 0; index < first.length; index++) {
            assertEquals(first[index], second[index]);
            int alpha = first[index] >>> 24;
            assertEquals(255, alpha);
            int red = first[index] >> 16 & 255;
            int green = first[index] >> 8 & 255;
            int blue = first[index] & 255;
            assertEquals(red, green);
            assertEquals(red, blue);
            sum += red;
            squareSum += red * red;
            minimum = Math.min(minimum, red);
            maximum = Math.max(maximum, red);
            if (index % size != 0
                    && red != (first[index - 1] & 255)) {
                changedNeighbors++;
            }
        }
        double mean = sum / (double)first.length;
        double variance = squareSum / first.length - mean * mean;
        assertTrue("the cloudy field lost its tonal range",
                minimum < 130 && maximum > 185);
        assertTrue("the cloudy field gained a colour-cast bias",
                mean > 125.0D && mean < 190.0D);
        assertTrue("the cloudy field became flat or harsh",
                variance > 120.0D && variance < 1800.0D);
        assertTrue("the fine pixel texture disappeared",
                changedNeighbors > first.length / 5);
        assertTrue("the final overlay is no longer subtle",
                LostTalesLotrSmoothMapRenderer.NOISE_OPACITY <= 0.12F);

        // Every octave wraps at the tile edge, avoiding a repeated seam.
        for (int y = 0; y < size; y++) {
            int left = first[y * size] & 255;
            int right = first[y * size + size - 1] & 255;
            assertTrue(Math.abs(left - right) <= 15);
        }
    }
}
