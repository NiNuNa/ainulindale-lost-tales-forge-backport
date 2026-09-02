package com.ninuna.losttales.client.skin;

import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * The normalizer must produce the layout the player model reads: a classic
 * skin's right limbs mirrored into the left limb regions, overlays cleared
 * when they were saved solid, and the base body forced opaque.
 */
public final class SkinImageNormalizerTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREY = 0xFF808080;

    @Test
    public void rejectsAnythingThatIsNotASkin() {
        assertNull(SkinImageNormalizer.normalize(null));
        assertNull(SkinImageNormalizer.normalize(
                new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)));
        assertNull(SkinImageNormalizer.normalize(
                new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)));
    }

    @Test
    public void legacySkinGetsMirroredLeftLimbs() {
        BufferedImage legacy = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        fill(legacy, 0, 16, 64, 16, GREY);
        legacy.setRGB(4, 20, RED);   // right leg front, leftmost column
        legacy.setRGB(0, 20, GREEN); // right leg outer face, leftmost column
        legacy.setRGB(44, 20, BLUE); // right arm front, leftmost column
        legacy.setRGB(5, 16, RED);   // right leg top, second column

        BufferedImage image = SkinImageNormalizer.normalize(legacy);
        assertNotNull(image);
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        // Mirrored: the leftmost source column lands on the rightmost target column.
        assertEquals(RED, image.getRGB(23, 52));   // left leg front
        assertEquals(GREEN, image.getRGB(27, 52)); // left leg inner face
        assertEquals(BLUE, image.getRGB(39, 52));  // left arm front
        assertEquals(RED, image.getRGB(22, 48));   // left leg top
        assertEquals(GREY, image.getRGB(16, 63));  // rest of the leg region copied
        // The original right limbs stay where they were.
        assertEquals(RED, image.getRGB(4, 20));
        assertEquals(BLUE, image.getRGB(44, 20));
        // Overlay regions of a legacy skin stay empty.
        assertEquals(0, image.getRGB(20, 36) >>> 24);
        assertEquals(0, image.getRGB(50, 52) >>> 24);
    }

    @Test
    public void modernSkinKeepsItsOverlaysAndForcesTheBodyOpaque() {
        BufferedImage modern = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        fill(modern, 0, 0, 64, 64, 0);
        modern.setRGB(20, 36, RED);  // body overlay, one pixel, rest transparent
        modern.setRGB(50, 52, BLUE); // left arm overlay
        modern.setRGB(20, 20, 0x80123456); // body pixel saved half transparent

        BufferedImage image = SkinImageNormalizer.normalize(modern);
        assertNotNull(image);
        assertEquals(RED, image.getRGB(20, 36));
        assertEquals(BLUE, image.getRGB(50, 52));
        assertEquals(0xFF123456, image.getRGB(20, 20));
        assertEquals(0xFF000000, image.getRGB(33, 50)); // left arm base forced opaque
    }

    @Test
    public void solidOverlayRegionsAreClearedAsUnintended() {
        BufferedImage modern = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        fill(modern, 0, 0, 64, 64, GREY);         // everything opaque, hat included
        modern.setRGB(0, 33, 0x00000000);          // one clear pixel keeps the leg overlay

        BufferedImage image = SkinImageNormalizer.normalize(modern);
        assertNotNull(image);
        assertEquals(0, image.getRGB(40, 8) >>> 24);   // hat cleared
        assertEquals(0, image.getRGB(20, 36) >>> 24);  // body overlay cleared
        assertEquals(0xFF, image.getRGB(8, 40) >>> 24); // right leg overlay kept
        assertEquals(GREY, image.getRGB(8, 8));         // face untouched
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, int argb) {
        for (int column = x; column < x + width; column++) {
            for (int row = y; row < y + height; row++) {
                image.setRGB(column, row, argb);
            }
        }
    }
}
