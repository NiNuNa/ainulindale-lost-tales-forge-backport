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
        assertEquals(40, escape.getV(0));
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

    @Test
    public void wordKeysUseTheFrameFamilyMatchingTheirLength() {
        assertWordKey(Keyboard.KEY_LMENU, 21, 40, 0, 54, 15, 22);
        assertWordKey(Keyboard.KEY_ESCAPE, 21, 40, 16, 54, 15, 22);
        assertWordKey(Keyboard.KEY_TAB, 21, 40, 32, 54, 15, 22);
        assertWordKey(Keyboard.KEY_DELETE, 21, 40, 48, 54, 15, 22);
        assertWordKey(Keyboard.KEY_CAPITAL, 25, 60, 0, 74, 19, 26);
        assertWordKey(Keyboard.KEY_LCONTROL, 25, 60, 20, 74, 19, 26);
        assertWordKey(Keyboard.KEY_RCONTROL, 25, 60, 20, 74, 19, 26);
        assertWordKey(Keyboard.KEY_LSHIFT, 29, 80, 0, 94, 23, 30);
        assertWordKey(Keyboard.KEY_SPACE, 29, 80, 24, 94, 23, 30);
    }

    @Test
    public void functionKeysSplitByHowManyCharactersTheyNeed() {
        // F1 to F9 are two characters wide and have their own frame family;
        // F10 to F12 are three, so they share the ALT/ESC/TAB frames.
        int[] shortKeys = {
                Keyboard.KEY_F1, Keyboard.KEY_F2, Keyboard.KEY_F3,
                Keyboard.KEY_F4, Keyboard.KEY_F5, Keyboard.KEY_F6,
                Keyboard.KEY_F7, Keyboard.KEY_F8, Keyboard.KEY_F9
        };
        for (int index = 0; index < shortKeys.length; index++) {
            assertWordKey(shortKeys[index], 17, 20,
                    index * 12, 34, 11, 18);
        }
        assertWordKey(Keyboard.KEY_F10, 21, 40, 64, 54, 15, 22);
        assertWordKey(Keyboard.KEY_F11, 21, 40, 80, 54, 15, 22);
        assertWordKey(Keyboard.KEY_F12, 21, 40, 96, 54, 15, 22);
    }

    @Test
    public void mouseWheelRemainsOnItsSingleUnanimatedFrame() {
        Sprite wheel = LostTalesInputIconAtlas.findSprite(Type.MOUSE_WHEEL, 0);
        assertNotNull(wheel);
        assertFalse(wheel.isAnimated());
        assertFalse(wheel.hasGlyph());
        assertEquals(100, wheel.getV(0));
        assertEquals(100, wheel.getV(2));
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
