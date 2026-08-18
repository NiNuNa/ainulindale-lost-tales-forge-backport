package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

public final class LostTalesMapCursorTest {
    @Test
    public void bundledCursorMatchesItsDrawnSize() throws Exception {
        InputStream input = getClass().getResourceAsStream(
                "/assets/losttales/textures/gui/map_cursor.png");
        assertNotNull(input);
        try {
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image);
            assertEquals(LostTalesMapCursor.WIDTH, image.getWidth());
            assertEquals(LostTalesMapCursor.HEIGHT, image.getHeight());
        } finally {
            input.close();
        }
    }
}
