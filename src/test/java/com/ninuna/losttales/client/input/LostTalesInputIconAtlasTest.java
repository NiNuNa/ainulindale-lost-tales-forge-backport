package com.ninuna.losttales.client.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void keyboardSpritesAddressThreeHorizontalFramePoses() {
        Sprite letter = LostTalesInputIconAtlas.findSprite(
                Type.KEYBOARD, Keyboard.KEY_A);
        assertNotNull(letter);
        assertTrue(letter.isAnimated());
        assertEquals(3, letter.getFrameCount());
        assertEquals(0, letter.getU(0));
        assertEquals(14, letter.getU(1));
        assertEquals(28, letter.getU(2));
        assertEquals(0, letter.getV(0));
        assertEquals(0, letter.getV(2));

        Sprite escape = LostTalesInputIconAtlas.findSprite(
                Type.KEYBOARD, Keyboard.KEY_ESCAPE);
        assertEquals(0, escape.getU(0));
        assertEquals(22, escape.getU(1));
        assertEquals(44, escape.getU(2));
        assertEquals(20, escape.getV(0));
    }

    @Test
    public void oneCharacterGlyphsFollowLettersThenArrowOrder() {
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
                Keyboard.KEY_DOWN, Keyboard.KEY_LEFT
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

    @Test
    public void wordKeysUseTheFrameFamilyMatchingTheirLength() {
        assertWordKey(Keyboard.KEY_LMENU, 21, 20, 0, 34, 15, 22);
        assertWordKey(Keyboard.KEY_ESCAPE, 21, 20, 16, 34, 15, 22);
        assertWordKey(Keyboard.KEY_TAB, 21, 20, 32, 34, 15, 22);
        assertWordKey(Keyboard.KEY_CAPITAL, 25, 40, 0, 54, 19, 26);
        assertWordKey(Keyboard.KEY_LSHIFT, 29, 60, 0, 74, 23, 30);
    }

    @Test
    public void mouseWheelRemainsOnItsSingleUnanimatedFrame() {
        Sprite wheel = LostTalesInputIconAtlas.findSprite(Type.MOUSE_WHEEL, 0);
        assertNotNull(wheel);
        assertFalse(wheel.isAnimated());
        assertFalse(wheel.hasGlyph());
        assertEquals(80, wheel.getV(0));
        assertEquals(80, wheel.getV(2));
    }

    private static void assertWordKey(
            int keyCode, int width, int frameV,
            int glyphU, int glyphV, int glyphWidth, int frameStride) {
        Sprite sprite = LostTalesInputIconAtlas.findSprite(
                Type.KEYBOARD, keyCode);
        assertNotNull(sprite);
        assertEquals(width, sprite.getWidth());
        assertEquals(frameV, sprite.getV(0));
        assertEquals(0, sprite.getU(0));
        assertEquals(frameStride, sprite.getU(1));
        assertEquals(frameStride * 2, sprite.getU(2));
        assertEquals(glyphU, sprite.getGlyphU());
        assertEquals(glyphV, sprite.getGlyphV());
        assertEquals(glyphWidth, sprite.getGlyphWidth());
        assertCenteredGlyph(sprite);
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
