package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

public final class LostTalesMapCursorTest {
    @Test
    public void bundledCursorHoldsBothPosesAtTheirDrawnSize() throws Exception {
        InputStream input = getClass().getResourceAsStream(
                "/assets/losttales/textures/gui/cursor.png");
        assertNotNull(input);
        try {
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image);
            assertEquals(LostTalesMapCursor.TEXTURE_WIDTH, image.getWidth());
            assertEquals(LostTalesMapCursor.TEXTURE_HEIGHT, image.getHeight());
            // Both poses are addressed as cells of the strip, so a re-export
            // that changes its width silently re-aims every pixel.
            assertEquals(0, LostTalesMapCursor.TEXTURE_WIDTH
                    % LostTalesMapCursor.SPRITE_WIDTH);
            assertEquals(2, LostTalesMapCursor.TEXTURE_WIDTH
                    / LostTalesMapCursor.SPRITE_WIDTH);
            assertEquals(LostTalesMapCursor.SPRITE_HEIGHT,
                    LostTalesMapCursor.TEXTURE_HEIGHT);
        } finally {
            input.close();
        }
    }
}
