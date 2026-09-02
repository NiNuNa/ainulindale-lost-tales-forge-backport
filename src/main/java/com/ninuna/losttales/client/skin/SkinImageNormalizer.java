package com.ninuna.losttales.client.skin;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Brings a player skin into Minecraft's 64x64 layout.
 *
 * A classic 64x32 skin gets its right arm and right leg mirrored into the
 * left arm and left leg regions the 64x64 layout reserves at (32,48) and
 * (16,48); its overlay regions stay empty. A 64x64 skin is kept as it is.
 * Both then receive the same alpha rules Minecraft applies: the base body
 * is forced opaque, and an overlay region that is completely opaque is
 * treated as unintended and cleared. Anything else is not a skin and
 * normalizes to null.
 */
public final class SkinImageNormalizer {

    public static final int WIDTH = 64;
    public static final int HEIGHT = 64;
    public static final int LEGACY_HEIGHT = 32;

    private static final int OPAQUE = 0xFF000000;
    private static final int COLOR_MASK = 0x00FFFFFF;
    private static final int TRANSPARENT_THRESHOLD = 128;

    private SkinImageNormalizer() {}

    public static boolean isSkinSized(BufferedImage image) {
        return image != null && image.getWidth() == WIDTH
                && (image.getHeight() == LEGACY_HEIGHT || image.getHeight() == HEIGHT);
    }

    public static BufferedImage normalize(BufferedImage source) {
        if (!isSkinSized(source)) {
            return null;
        }
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = image.getGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        int[] pixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();

        if (source.getHeight() == LEGACY_HEIGHT) {
            // Right leg faces (0,16) mirrored into the left leg region (16,48):
            // top, bottom, then the four sides with outer and inner swapped.
            copyMirrored(pixels, 4, 16, 4, 4, 20, 48);
            copyMirrored(pixels, 8, 16, 4, 4, 24, 48);
            copyMirrored(pixels, 8, 20, 4, 12, 16, 52);
            copyMirrored(pixels, 4, 20, 4, 12, 20, 52);
            copyMirrored(pixels, 0, 20, 4, 12, 24, 52);
            copyMirrored(pixels, 12, 20, 4, 12, 28, 52);
            // Right arm faces (40,16) mirrored into the left arm region (32,48).
            copyMirrored(pixels, 44, 16, 4, 4, 36, 48);
            copyMirrored(pixels, 48, 16, 4, 4, 40, 48);
            copyMirrored(pixels, 48, 20, 4, 12, 32, 52);
            copyMirrored(pixels, 44, 20, 4, 12, 36, 52);
            copyMirrored(pixels, 40, 20, 4, 12, 40, 52);
            copyMirrored(pixels, 52, 20, 4, 12, 44, 52);
        }

        setAreaOpaque(pixels, 0, 0, 32, 16);
        clearIfFullyOpaque(pixels, 32, 0, 64, 32);
        setAreaOpaque(pixels, 0, 16, 64, 32);
        clearIfFullyOpaque(pixels, 0, 32, 16, 48);
        clearIfFullyOpaque(pixels, 16, 32, 40, 48);
        clearIfFullyOpaque(pixels, 40, 32, 56, 48);
        clearIfFullyOpaque(pixels, 0, 48, 16, 64);
        setAreaOpaque(pixels, 16, 48, 48, 64);
        clearIfFullyOpaque(pixels, 48, 48, 64, 64);
        return image;
    }

    /** Copies a rectangle with its columns reversed, the way a mirrored limb reads. */
    private static void copyMirrored(int[] pixels, int sourceX, int sourceY,
                                     int width, int height,
                                     int targetX, int targetY) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int source = (sourceX + width - 1 - column) + (sourceY + row) * WIDTH;
                int target = (targetX + column) + (targetY + row) * WIDTH;
                pixels[target] = pixels[source];
            }
        }
    }

    private static void setAreaOpaque(int[] pixels, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                pixels[x + y * WIDTH] |= OPAQUE;
            }
        }
    }

    /**
     * An overlay saved without any transparency was almost certainly exported
     * from an editor with no alpha channel; the whole region is cleared so it
     * does not wrap the limb in a solid shell.
     */
    private static void clearIfFullyOpaque(int[] pixels, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                if ((pixels[x + y * WIDTH] >>> 24) < TRANSPARENT_THRESHOLD) {
                    return;
                }
            }
        }
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                pixels[x + y * WIDTH] &= COLOR_MASK;
            }
        }
    }
}
