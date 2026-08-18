package com.ninuna.losttales.gui.hud.loot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

/**
 * The quick-loot panel is cut out of one strip by V offset, so the offsets and
 * the sprite are the same fact written twice. Re-exporting the strip with a
 * different band spacing fails here rather than shipping a panel assembled out
 * of its neighbours' pixels.
 */
public final class LostTalesQuickLootHudRendererTest {
    @Test
    public void bandOffsetsMatchTheBundledStrip() throws Exception {
        assertEquals("textures/gui/quick_loot_hud.png",
                LostTalesQuickLootHudRenderer.TEXTURE.getResourcePath());
        InputStream stream = LostTalesQuickLootHudRendererTest.class
                .getResourceAsStream(
                        "/assets/losttales/textures/gui/quick_loot_hud.png");
        assertNotNull("Quick-loot HUD sprite is missing", stream);
        try {
            BufferedImage strip = ImageIO.read(stream);
            assertNotNull("Quick-loot HUD sprite is not a readable PNG",
                    strip);
            assertEquals(LostTalesQuickLootHudRenderer.TEXTURE_WIDTH,
                    strip.getWidth());
            assertEquals(LostTalesQuickLootHudRenderer.TEXTURE_HEIGHT,
                    strip.getHeight());

            assertBand(strip, 0,
                    LostTalesQuickLootHudRenderer.TOP_HEIGHT);
            assertBand(strip,
                    LostTalesQuickLootHudRenderer.ROW_TEXTURE_V,
                    LostTalesQuickLootHudRenderer.ROW_HEIGHT);
            assertBand(strip,
                    LostTalesQuickLootHudRenderer.BOTTOM_TEXTURE_V,
                    LostTalesQuickLootHudRenderer.BOTTOM_HEIGHT);
            assertBand(strip,
                    LostTalesQuickLootHudRenderer.SELECTION_TEXTURE_V,
                    LostTalesQuickLootHudRenderer.SELECTION_HEIGHT);
            assertBand(strip,
                    LostTalesQuickLootHudRenderer
                            .VERTICAL_ORNAMENT_TEXTURE_V,
                    LostTalesQuickLootHudRenderer
                            .ORNAMENT_VERTICAL_HEIGHT);
            assertBand(strip,
                    LostTalesQuickLootHudRenderer
                            .HORIZONTAL_ORNAMENT_TEXTURE_V,
                    LostTalesQuickLootHudRenderer
                            .ORNAMENT_HORIZONTAL_HEIGHT);
            assertBand(strip,
                    LostTalesQuickLootHudRenderer.ARROW_TEXTURE_V,
                    LostTalesQuickLootHudRenderer.ARROW_HEIGHT);
        } finally {
            stream.close();
        }
    }

    /**
     * A band starts and ends on artwork, and the row above it — the single
     * separator row — carries none. That is what pins each offset to the one
     * band it was authored for.
     */
    private static void assertBand(
            BufferedImage strip, int v, int height) {
        assertTrue("Band at v=" + v + " runs off the strip",
                v >= 0 && v + height <= strip.getHeight());
        assertTrue("Band at v=" + v + " starts on a blank row",
                rowHasArt(strip, v));
        assertTrue("Band at v=" + v + " ends on a blank row",
                rowHasArt(strip, v + height - 1));
        if (v > 0) {
            assertTrue("Band at v=" + v + " is not preceded by a gap",
                    !rowHasArt(strip, v - 1));
        }
    }

    private static boolean rowHasArt(BufferedImage strip, int y) {
        for (int x = 0; x < strip.getWidth(); x++) {
            if ((strip.getRGB(x, y) >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }
}
