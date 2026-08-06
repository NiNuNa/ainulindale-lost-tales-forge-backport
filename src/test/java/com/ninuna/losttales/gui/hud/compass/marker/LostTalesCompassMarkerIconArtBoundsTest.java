package com.ninuna.losttales.gui.hud.compass.marker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

/**
 * Pins the declared artwork bounds to the bundled atlas.
 *
 * <p>Marker anchoring, labels and mouse targets are all derived from these
 * numbers, so a re-export that nudges a glyph inside its cell must fail the
 * build instead of shipping icons that sit off their coordinate and hit-test
 * several pixels wide.</p>
 */
public final class LostTalesCompassMarkerIconArtBoundsTest {
    /** Alpha above this counts as ink; the atlas has no partial edges below it. */
    private static final int OPAQUE_THRESHOLD = 8;

    @Test
    public void declaredArtBoundsMatchTheBundledAtlas() throws Exception {
        BufferedImage atlas = readAtlas();
        for (LostTalesCompassMarkerIcon icon
                : LostTalesCompassMarkerIcon.values()) {
            int[] measured = measureArt(atlas, icon);
            if (measured == null) {
                // Blank cell: nothing to verify, the declared box is a
                // documented placeholder.
                continue;
            }
            assertEquals(icon.name() + " art left",
                    measured[0], icon.getArtLeft(), 0.0F);
            assertEquals(icon.name() + " art right",
                    measured[1], icon.getArtRight(), 0.0F);
            assertEquals(icon.name() + " art top",
                    measured[2], icon.getArtTop(), 0.0F);
            assertEquals(icon.name() + " art bottom",
                    measured[3], icon.getArtBottom(), 0.0F);
        }
    }

    @Test
    public void artBoundsStayInsideTheirCell() {
        for (LostTalesCompassMarkerIcon icon
                : LostTalesCompassMarkerIcon.values()) {
            assertTrue(icon.name() + " art starts inside the cell",
                    icon.getArtLeft() >= 0.0F && icon.getArtTop() >= 0.0F);
            assertTrue(icon.name() + " art ends inside the cell",
                    icon.getArtRight()
                            <= LostTalesCompassMarkerIcon.WIDTH
                    && icon.getArtBottom()
                            <= LostTalesCompassMarkerIcon.HEIGHT);
            assertTrue(icon.name() + " art is not empty",
                    icon.getArtRight() > icon.getArtLeft()
                    && icon.getArtBottom() > icon.getArtTop());
        }
    }

    @Test
    public void everyGlyphSharesTheAtlasHorizontalCentre() {
        // The compass anchors on this single constant; the map relies on the
        // same alignment, so a glyph drawn off-centre would break both.
        for (LostTalesCompassMarkerIcon icon
                : LostTalesCompassMarkerIcon.values()) {
            assertEquals(icon.name() + " art centre",
                    LostTalesCompassMarkerIcon.ART_CENTER_X,
                    icon.getArtCenterX(), 0.0F);
        }
    }

    @Test
    public void artIsNarrowerThanTheCellItIsDrawnInto() {
        // The regression this guards: treating the 17-pixel cell as the icon
        // made the mouse target of the pin glyphs far wider than the ink.
        LostTalesCompassMarkerIcon pin =
                LostTalesCompassMarkerIcon.UNDISCOVERED;
        assertEquals(9.0F, pin.getArtRight() - pin.getArtLeft(), 0.0F);
        assertTrue("the cell carries transparent margin",
                pin.getArtRight() - pin.getArtLeft()
                        < LostTalesCompassMarkerIcon.WIDTH);
    }

    private static BufferedImage readAtlas() throws Exception {
        InputStream stream = LostTalesCompassMarkerIconArtBoundsTest.class
                .getResourceAsStream(
                        "/assets/losttales/textures/gui/map_markers.png");
        assertNotNull("Map marker atlas is missing", stream);
        try {
            BufferedImage atlas = ImageIO.read(stream);
            assertNotNull("Map marker atlas is not a readable PNG", atlas);
            assertEquals(LostTalesCompassMarkerIcon.TEXTURE_WIDTH,
                    atlas.getWidth());
            assertEquals(LostTalesCompassMarkerIcon.TEXTURE_HEIGHT,
                    atlas.getHeight());
            return atlas;
        } finally {
            stream.close();
        }
    }

    /** Returns {left, right, top, bottom} as continuous edges, or null. */
    private static int[] measureArt(
            BufferedImage atlas, LostTalesCompassMarkerIcon icon) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = 0; y < LostTalesCompassMarkerIcon.HEIGHT; y++) {
            for (int x = 0; x < LostTalesCompassMarkerIcon.WIDTH; x++) {
                int pixelX = icon.getU() + x;
                int pixelY = icon.getV() + y;
                if (pixelX >= atlas.getWidth()
                        || pixelY >= atlas.getHeight()) {
                    continue;
                }
                int alpha = (atlas.getRGB(pixelX, pixelY) >>> 24) & 0xFF;
                if (alpha <= OPAQUE_THRESHOLD) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return maxX < 0
                ? null
                : new int[] {minX, maxX + 1, minY, maxY + 1};
    }
}
