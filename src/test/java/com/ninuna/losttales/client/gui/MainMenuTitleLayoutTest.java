package com.ninuna.losttales.client.gui;

import org.junit.Test;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MainMenuTitleLayoutTest {
    @Test
    public void modernTitleProportionsFitAboveButtonsAtMinimumGuiSize() {
        MainMenuTitleLayout layout = MainMenuTitleLayout.fit(320, 108, 9);
        assertEquals(30, MainMenuTitleLayout.TOP);
        assertEquals(256.0F, layout.width, 0.001F);
        assertEquals(32.0F, layout.left, 0.001F);
        assertTrue(layout.subtitleY() + 9 + MainMenuTitleLayout.GAP <= 108);
    }

    @Test
    public void oddGuiWidthUsesTheSameIntegerCenterAsTheButtons() {
        MainMenuTitleLayout layout = MainMenuTitleLayout.fit(427, 136, 9);
        assertEquals(256.0F, layout.width, 0.001F);
        assertEquals(427 / 2 - 128, layout.left, 0.001F);
    }

    @Test
    public void dimensionsAndTransparencyMatchTheBundledArtwork() throws Exception {
        InputStream stream = getClass().getResourceAsStream(
                "/assets/losttales/" + LostTalesMainMenuTitle.TEXTURE_PATH);
        assertNotNull(stream);
        try {
            BufferedImage title = ImageIO.read(stream);
            assertNotNull(title);
            assertEquals(MainMenuTitleLayout.TEXTURE_WIDTH, title.getWidth());
            assertEquals(MainMenuTitleLayout.TEXTURE_HEIGHT, title.getHeight());
            assertTrue(title.getColorModel().hasAlpha());
            assertEquals(0, title.getRGB(0, 0) >>> 24);
            int minX = title.getWidth();
            int minY = title.getHeight();
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < title.getHeight(); y++) {
                for (int x = 0; x < title.getWidth(); x++) {
                    if ((title.getRGB(x, y) >>> 24) != 0) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            // The UV crop must keep every visible pixel of the supplied artwork.
            assertEquals(MainMenuTitleLayout.TEXTURE_PADDING, minX);
            assertEquals(MainMenuTitleLayout.TEXTURE_PADDING, minY);
            assertEquals(MainMenuTitleLayout.ARTWORK_WIDTH, maxX - minX + 1);
            assertEquals(MainMenuTitleLayout.ARTWORK_HEIGHT, maxY - minY + 1);
        } finally {
            stream.close();
        }
    }

    @Test
    public void fitsNarrowAndShortMenusWithoutStretchingOrOverlappingControls() {
        for (int screenWidth : new int[] {160, 320, 427, 854, 1920}) {
            for (int buttonsTop : new int[] {60, 111, 160, 188, 300}) {
                for (int subtitleHeight : new int[] {0, 9}) {
                    MainMenuTitleLayout layout = MainMenuTitleLayout.fit(
                            screenWidth, buttonsTop, subtitleHeight);
                    assertTrue(layout.width > 0.0F);
                    assertTrue(layout.width <= MainMenuTitleLayout.MAX_WIDTH);
                    assertTrue(layout.left >= MainMenuTitleLayout.SIDE_MARGIN - 0.001F);
                    float rightMargin = screenWidth - layout.left - layout.width;
                    assertTrue(rightMargin >= layout.left - 0.001F);
                    assertTrue(rightMargin - layout.left < 2.0F);
                    assertEquals(4012.0F / 794.0F, layout.width / layout.height, 0.001F);
                    float bottom = subtitleHeight == 0
                            ? MainMenuTitleLayout.TOP + layout.height
                            : layout.subtitleY() + subtitleHeight;
                    assertTrue(bottom + MainMenuTitleLayout.GAP <= buttonsTop + 0.001F);
                }
            }
        }
    }

    @Test
    public void noRoomProducesNoInvertedOrOffscreenTitle() {
        assertEquals(0.0F, MainMenuTitleLayout.fit(20, 100, 9).width, 0.0F);
        assertEquals(0.0F, MainMenuTitleLayout.fit(320, 10, 9).width, 0.0F);
    }
}
