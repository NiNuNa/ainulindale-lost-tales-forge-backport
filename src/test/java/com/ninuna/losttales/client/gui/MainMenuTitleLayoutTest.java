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
                    assertEquals(screenWidth, layout.left * 2 + layout.width, 0.001F);
                    assertEquals(2006.0F / 560.0F, layout.width / layout.height, 0.001F);
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
