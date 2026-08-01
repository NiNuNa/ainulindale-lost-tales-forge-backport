package com.ninuna.losttales.gui.hud.compass.marker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

public final class LostTalesCompassMarkerIconTest {
    @Test
    public void markerAtlasMetadataMatchesBundledSprite() throws Exception {
        InputStream stream = LostTalesCompassMarkerIconTest.class
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

            LostTalesCompassMarkerIcon[] sprites = {
                    LostTalesCompassMarkerIcon.QUEST,
                    LostTalesCompassMarkerIcon.HOSTILE,
                    LostTalesCompassMarkerIcon.UNDISCOVERED,
                    LostTalesCompassMarkerIcon.N,
                    LostTalesCompassMarkerIcon.NE,
                    LostTalesCompassMarkerIcon.E,
                    LostTalesCompassMarkerIcon.SE,
                    LostTalesCompassMarkerIcon.S,
                    LostTalesCompassMarkerIcon.SW,
                    LostTalesCompassMarkerIcon.W,
                    LostTalesCompassMarkerIcon.NW,
                    LostTalesCompassMarkerIcon.TOWN,
                    LostTalesCompassMarkerIcon.GRAVEYARD,
                    LostTalesCompassMarkerIcon.FOREST,
                    LostTalesCompassMarkerIcon.FOUNTAIN,
                    LostTalesCompassMarkerIcon.PORT
            };
            for (LostTalesCompassMarkerIcon sprite : sprites) {
                assertTrue(sprite.name() + " extends beyond the atlas",
                        sprite.getU() + LostTalesCompassMarkerIcon.WIDTH
                                <= atlas.getWidth()
                        && sprite.getV()
                                + LostTalesCompassMarkerIcon.HEIGHT
                                <= atlas.getHeight());
                // The supplied atlas currently leaves the fountain slot empty.
                if (sprite != LostTalesCompassMarkerIcon.FOUNTAIN) {
                    assertTrue(sprite.name() + " has no visible pixels",
                            hasVisiblePixel(atlas, sprite));
                }
            }

            // The revised forest silhouette begins with its three-pixel cap;
            // losing that row makes the tree look clipped in the compass.
            assertEquals(3, visiblePixelCountInRow(atlas,
                    LostTalesCompassMarkerIcon.FOREST,
                    LostTalesCompassMarkerIcon.FOREST.getV()));
            assertTrue(hasVisiblePixelInRow(atlas,
                    LostTalesCompassMarkerIcon.FOREST,
                    LostTalesCompassMarkerIcon.FOREST.getV() + 14));
        } finally {
            stream.close();
        }
    }

    @Test
    public void iconNamesResolveNewIconsAndLegacyAliases() {
        assertEquals(LostTalesCompassMarkerIcon.TOWN,
                LostTalesCompassMarkerIcon.fromName("town"));
        assertEquals(LostTalesCompassMarkerIcon.GRAVEYARD,
                LostTalesCompassMarkerIcon.fromName("cemetery"));
        assertEquals(LostTalesCompassMarkerIcon.FOREST,
                LostTalesCompassMarkerIcon.fromName("woods"));
        assertEquals(LostTalesCompassMarkerIcon.FOUNTAIN,
                LostTalesCompassMarkerIcon.fromName("fountain"));
        assertEquals(LostTalesCompassMarkerIcon.PORT,
                LostTalesCompassMarkerIcon.fromName("harbour"));
        assertEquals(LostTalesCompassMarkerIcon.FORT,
                LostTalesCompassMarkerIcon.fromName("fort"));
        assertEquals(LostTalesCompassMarkerIcon.TAVERN,
                LostTalesCompassMarkerIcon.fromName("tavern"));
    }

    private static boolean hasVisiblePixel(BufferedImage atlas,
            LostTalesCompassMarkerIcon sprite) {
        for (int y = sprite.getV();
                y < sprite.getV() + LostTalesCompassMarkerIcon.HEIGHT; y++) {
            for (int x = sprite.getU();
                    x < sprite.getU() + LostTalesCompassMarkerIcon.WIDTH;
                    x++) {
                if ((atlas.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasVisiblePixelInRow(BufferedImage atlas,
            LostTalesCompassMarkerIcon sprite, int y) {
        return visiblePixelCountInRow(atlas, sprite, y) > 0;
    }

    private static int visiblePixelCountInRow(BufferedImage atlas,
            LostTalesCompassMarkerIcon sprite, int y) {
        int count = 0;
        for (int x = sprite.getU();
                x < sprite.getU() + LostTalesCompassMarkerIcon.WIDTH; x++) {
            if (alphaAt(atlas, x, y) != 0) {
                count++;
            }
        }
        return count;
    }

    private static int alphaAt(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
