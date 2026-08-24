package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

/**
 * The pointer sheet is a strip of poses, each as wide as its own
 * artwork. Every pose addresses its own rectangle of it, so a re-export
 * at another size has to fail here rather than re-aim the poses after
 * the first.
 */
public final class LostTalesMapCursorTest {

    private static BufferedImage sheet() throws Exception {
        InputStream input = LostTalesMapCursorTest.class.getResourceAsStream(
                "/assets/losttales/textures/gui/cursor.png");
        assertNotNull(input);
        try {
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image);
            return image;
        } finally {
            input.close();
        }
    }

    @Test
    public void theSheetIsTheSizeThePosesAreAimedAt() throws Exception {
        BufferedImage image = sheet();
        assertEquals(LostTalesMapCursor.TEXTURE_WIDTH, image.getWidth());
        assertEquals(LostTalesMapCursor.TEXTURE_HEIGHT, image.getHeight());
        assertEquals(LostTalesMapCursor.SPRITE_HEIGHT,
                LostTalesMapCursor.TEXTURE_HEIGHT);
    }

    /** Every pose's rectangle holds artwork, and none of it is clipped. */
    @Test
    public void everyPoseAddressesItsOwnArtwork() throws Exception {
        BufferedImage image = sheet();
        for (LostTalesMapCursor.Pose pose
                : LostTalesMapCursor.Pose.values()) {
            assertTrue(pose + " runs past the sheet",
                    pose.fitsSheet(image.getWidth()));
            assertTrue(pose + " addresses an empty rectangle",
                    hasPixels(image, pose.textureU(), pose.spriteWidth()));
            // The column each pose ends at is clear, so a pose never
            // shows the edge of the next one.
            int after = pose.textureU() + pose.spriteWidth();
            if (after < image.getWidth()) {
                assertFalse(pose + " touches the pose after it",
                        columnHasPixels(image, after - 1)
                                && columnHasPixels(image, after));
            }
        }
    }

    /** The pointer aims with its hotspot, which has to be inside it. */
    @Test
    public void everyHotspotLiesInsideItsPose() {
        for (LostTalesMapCursor.Pose pose
                : LostTalesMapCursor.Pose.values()) {
            assertTrue(pose + " aims outside itself",
                    pose.hotspotX() >= 0
                            && pose.hotspotX() < pose.drawnWidth());
            assertTrue(pose + " aims outside itself",
                    pose.hotspotY() >= 0
                            && pose.hotspotY() < pose.drawnHeight());
        }
    }

    private static boolean hasPixels(BufferedImage image, int u, int width) {
        for (int x = u; x < u + width; x++) {
            if (columnHasPixels(image, x)) {
                return true;
            }
        }
        return false;
    }

    private static boolean columnHasPixels(BufferedImage image, int x) {
        for (int y = 0; y < image.getHeight(); y++) {
            if ((image.getRGB(x, y) >>> 24) > 0) {
                return true;
            }
        }
        return false;
    }
}
