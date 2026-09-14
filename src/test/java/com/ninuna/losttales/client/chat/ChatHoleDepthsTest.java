package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The layers a window draws while its holes are cut stand clear of one
 * another in the depth buffer, under the GUI pass's own projection. Each
 * layer is tested against the mask alone, and two chains of transforms
 * can round one depth a hair apart, so a layer drawn at exactly its
 * hole's depth can land a step behind the hole and vanish: every layer
 * here stands thousands of a 24-bit buffer's steps from the next.
 */
public final class ChatHoleDepthsTest {

    /** One step of a 24-bit depth buffer. */
    private static final double STEP = 1.0D / ((1 << 24) - 1);

    @Test
    public void theHistoryLandsInTheBuffersMiddleAndTheBoxFarBehindIt() {
        float[] projection = guiProjection(427.0F, 240.0F);
        float[] modelview = windowModelview(12.5F, 40.25F, 0.5F);
        assertEquals(0.5D, depth(modelview, projection, 0.0F), 1.0E-6D);
        assertEquals(0.75D, depth(modelview, projection,
                LostTalesChatOverlayRenderer.BASE_DEPTH), 1.0E-6D);
    }

    @Test
    public void everyLayerStandsThousandsOfStepsFromTheNext() {
        // From the back: the window's box, the slab an item icon's depth
        // is squeezed into, the history, the chips' holes, the chips, the
        // shades and the floating controls' holes.
        float[] layers = {
                LostTalesChatOverlayRenderer.BASE_DEPTH,
                LostTalesChatOverlayRenderer.ITEM_FAR_DEPTH,
                LostTalesChatOverlayRenderer.ITEM_NEAR_DEPTH,
                0.0F,
                LostTalesChatOverlayRenderer.CHIP_HOLE_DEPTH,
                LostTalesChatOverlayRenderer.CHIP_DEPTH,
                LostTalesChatOverlayRenderer.SHADE_DEPTH,
                LostTalesChatOverlayRenderer.CONTROL_DEPTH};
        float[] projection = guiProjection(427.0F, 240.0F);
        float[] scales = {0.25F, 0.5F, 1.0F};
        for (float scale : scales) {
            float[] modelview = windowModelview(3.75F, 101.5F, scale);
            for (int index = 0; index + 1 < layers.length; index++) {
                double behind = depth(modelview, projection, layers[index]);
                double inFront = depth(modelview, projection,
                        layers[index + 1]);
                // Nearer is smaller in the buffer, as GL_LEQUAL reads it.
                assertTrue("layer " + (index + 1) + " at scale " + scale,
                        behind - inFront > 1000.0D * STEP);
            }
        }
    }

    private static double depth(float[] modelview, float[] projection,
                                float z) {
        return LostTalesChatOverlayRenderer.bufferDepth(modelview,
                projection, 0.0D, 1.0D, 5.0F, 7.0F, z);
    }

    /**
     * The GUI pass's projection, {@code glOrtho(0, width, height, 0,
     * 1000, 3000)}, a column at a time.
     */
    private static float[] guiProjection(float width, float height) {
        float[] matrix = new float[16];
        matrix[0] = 2.0F / width;
        matrix[5] = -2.0F / height;
        matrix[10] = -2.0F / 2000.0F;
        matrix[12] = -1.0F;
        matrix[13] = 1.0F;
        matrix[14] = -4000.0F / 2000.0F;
        matrix[15] = 1.0F;
        return matrix;
    }

    /**
     * The GUI pass's {@code translate(0, 0, -2000)}, then a window's
     * origin and scale, which scale across the screen and not into it.
     */
    private static float[] windowModelview(float originX, float originY,
                                           float scale) {
        float[] matrix = new float[16];
        matrix[0] = scale;
        matrix[5] = scale;
        matrix[10] = 1.0F;
        matrix[12] = originX;
        matrix[13] = originY;
        matrix[14] = -2000.0F;
        matrix[15] = 1.0F;
        return matrix;
    }
}
