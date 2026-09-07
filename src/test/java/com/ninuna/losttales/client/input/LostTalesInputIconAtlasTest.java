package com.ninuna.losttales.client.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.client.input.LostTalesInputBinding.Type;
import com.ninuna.losttales.client.input.LostTalesInputIconAtlas.Sprite;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

public final class LostTalesInputIconAtlasTest {
    @Test
    public void bundledTextureMatchesDeclaredAtlasSize() throws Exception {
        InputStream input = getClass().getResourceAsStream(
                "/assets/losttales/textures/gui/keyboard_keys.png");
        assertNotNull(input);
        try {
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image);
            assertEquals(LostTalesInputIconAtlas.TEXTURE_WIDTH,
                    image.getWidth());
            assertEquals(LostTalesInputIconAtlas.TEXTURE_HEIGHT,
                    image.getHeight());
        } finally {
            input.close();
        }
    }

    @Test
    public void singleGlyphsFollowLettersArrowsThenDigitOrder() {
        int[] codes = {
                Keyboard.KEY_A, Keyboard.KEY_B, Keyboard.KEY_C,
                Keyboard.KEY_D, Keyboard.KEY_E, Keyboard.KEY_F,
                Keyboard.KEY_G, Keyboard.KEY_H, Keyboard.KEY_I,
                Keyboard.KEY_J, Keyboard.KEY_K, Keyboard.KEY_L,
                Keyboard.KEY_M, Keyboard.KEY_N, Keyboard.KEY_O,
                Keyboard.KEY_P, Keyboard.KEY_Q, Keyboard.KEY_R,
                Keyboard.KEY_S, Keyboard.KEY_T, Keyboard.KEY_U,
                Keyboard.KEY_V, Keyboard.KEY_W, Keyboard.KEY_X,
                Keyboard.KEY_Y, Keyboard.KEY_Z,
                Keyboard.KEY_UP, Keyboard.KEY_RIGHT,
                Keyboard.KEY_DOWN, Keyboard.KEY_LEFT,
                Keyboard.KEY_0, Keyboard.KEY_1, Keyboard.KEY_2,
                Keyboard.KEY_3, Keyboard.KEY_4, Keyboard.KEY_5,
                Keyboard.KEY_6, Keyboard.KEY_7, Keyboard.KEY_8,
                Keyboard.KEY_9,
                Keyboard.KEY_PERIOD, Keyboard.KEY_COMMA
        };
        for (int index = 0; index < codes.length; index++) {
            Sprite sprite = LostTalesInputIconAtlas.findSprite(
                    Type.KEYBOARD, codes[index]);
            assertNotNull(sprite);
            assertEquals(index * 8, sprite.getGlyphU());
            assertEquals(14, sprite.getGlyphV());
            assertEquals(7, sprite.getGlyphWidth());
            assertCenteredGlyph(sprite);
        }
    }

    /**
     * Every glyph sits inside the frame it is composed over, which is what
     * keeps a re-tiered atlas from addressing a neighbouring band.
     */
    @Test
    public void everyGlyphFitsInsideItsOwnFrame() {
        int[] codes = {
                Keyboard.KEY_A, Keyboard.KEY_9,
                Keyboard.KEY_PERIOD, Keyboard.KEY_COMMA,
                Keyboard.KEY_F1, Keyboard.KEY_F9,
                Keyboard.KEY_LMENU, Keyboard.KEY_DELETE, Keyboard.KEY_F12,
                Keyboard.KEY_CAPITAL, Keyboard.KEY_LCONTROL,
                Keyboard.KEY_LSHIFT, Keyboard.KEY_SPACE
        };
        for (int index = 0; index < codes.length; index++) {
            Sprite sprite = LostTalesInputIconAtlas.findSprite(
                    Type.KEYBOARD, codes[index]);
            assertNotNull(sprite);
            assertEquals(sprite.getWidth(),
                    sprite.getGlyphWidth() + sprite.getGlyphOffsetX() * 2);
            int lastFrame = sprite.getFrameCount() - 1;
            assertTrue(sprite.getU(lastFrame) + sprite.getWidth()
                    <= LostTalesInputIconAtlas.TEXTURE_WIDTH);
            assertTrue(sprite.getGlyphV() + sprite.getGlyphHeight()
                    <= LostTalesInputIconAtlas.TEXTURE_HEIGHT);
            assertTrue(sprite.getGlyphOffsetY(lastFrame)
                    + sprite.getGlyphHeight()
                    <= LostTalesInputIconAtlas.SPRITE_HEIGHT);
        }
    }

    private static void assertCenteredGlyph(Sprite sprite) {
        assertTrue(sprite.hasGlyph());
        assertEquals(3, sprite.getGlyphOffsetX());
        assertEquals(2, sprite.getGlyphOffsetY(0));
        assertEquals(3, sprite.getGlyphOffsetY(1));
        assertEquals(4, sprite.getGlyphOffsetY(2));
        assertEquals(5, sprite.getGlyphHeight());
    }
}
